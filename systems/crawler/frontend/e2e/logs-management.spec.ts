import { expect, test, type Page } from '@playwright/test'

const base = process.env.AACV_E2E_BASE ?? ''
test.use({ timezoneId: 'Asia/Shanghai' })

async function setup(page: Page) {
  const queries: URLSearchParams[] = []
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/v1/**', route => {
    const url = new URL(route.request().url())
    const path = url.pathname.replace(/^\/crawler/, '').replace('/api/v1', '')
    if (path === '/auth/me') return route.fulfill({ json: { id: 1, username: '日志测试账号', roles: ['ADMIN'], permissions: ['AUDIT_READ', 'CATALOG_READ', 'GRAPH_READ', 'ANALYTICS_READ', 'AUTHOR_IMPORT', 'USER_LIST'] } })
    if (path === '/operations/audits') {
      queries.push(url.searchParams)
      const category = url.searchParams.get('category') ?? 'OPERATION'
      const pageNumber = Number(url.searchParams.get('page') ?? 0)
      return route.fulfill({ json: {
        items: Array.from({ length: 20 }, (_, index) => ({
          id: pageNumber * 20 + index + 1, category, username: url.searchParams.get('username') || '日志测试账号',
          action: url.searchParams.get('action') || (category === 'LOGIN' ? 'LOGIN_SUCCEEDED' : 'AUTHOR_IMPORTED'),
          result: url.searchParams.get('result') || 'SUCCESS', createdAt: '2026-09-12T02:44:10Z',
          actorUserId: 1, clientIp: '127.0.0.1', userAgent: 'Mozilla/5.0 Edg/140.0',
          targetType: 'AUTHOR_IMPORT', targetId: '1', traceId: 'logs-browser-test', summary: {},
        })), page: pageNumber, size: 20, totalElements: 41, totalPages: 3,
      } })
    }
    return route.fulfill({ status: 404, json: { detail: '日志测试未定义的接口' } })
  })
  return { queries, errors }
}

for (const width of [1440, 2549, 390]) {
  test(`日志紧凑搜索、表头筛选、分类与详情 ${width}px`, async ({ page }, testInfo) => {
    const { queries, errors } = await setup(page)
    const height = width === 2549 ? 1403 : 900
    await page.setViewportSize({ width, height })
    await page.goto(`${base}/logs`)
    await expect(page.getByRole('heading', { name: '日志管理', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '查看日志 1 详情', exact: true })).toBeVisible()
    await expect(page.getByRole('tab')).toHaveCount(2)
    await expect(page.getByRole('link', { name: '请求日志', exact: true })).toHaveCount(0)
    await expect(page.locator('.logs-filters')).toHaveCount(0)
    await expect(page.getByRole('textbox', { name: '账号搜索' })).toHaveAttribute('maxlength', '64')
    if (width >= 1440) {
      const search = await page.getByRole('search').boundingBox()
      const table = await page.locator('.logs-workspace').boundingBox()
      expect(search!.x).toBeGreaterThan(width * 0.6)
      expect(search!.width).toBeLessThanOrEqual(365)
      expect(table!.y).toBeLessThan(250)
      expect(table!.height).toBeGreaterThan(height - 280)
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath(`logs-${width}.png`), animations: 'disabled' })

    await page.getByRole('textbox', { name: '账号搜索' }).fill('  检索测试  ')
    await page.getByRole('textbox', { name: '账号搜索' }).press('Enter')
    await expect.poll(() => queries.at(-1)?.get('username')).toBe('检索测试')
    expect(queries.at(-1)?.get('category')).toBe('OPERATION')
    await page.getByRole('button', { name: '查看日志 1 详情', exact: true }).click()
    await expect(page.getByRole('dialog', { name: '日志详情' })).toBeVisible()
    await expect(page.getByRole('dialog')).toContainText('logs-browser-test')
    await page.getByRole('dialog').getByRole('button', { name: '关闭此对话框' }).click()

    await page.getByRole('button', { name: '按结果筛选', exact: true }).click()
    await page.getByRole('combobox', { name: '结果', exact: true }).press('ArrowDown')
    await page.getByRole('option', { name: '失败', exact: true }).click()
    await expect.poll(() => queries.at(-1)?.get('result')).toBe('FAILURE')
    await expect(page.getByRole('button', { name: '按结果筛选：失败' })).toHaveAttribute('aria-pressed', 'true')

    await page.getByRole('button', { name: '按事件类型筛选', exact: true }).click()
    await page.getByRole('combobox', { name: '操作类型', exact: true }).press('ArrowDown')
    await page.getByRole('option', { name: '导入作者资料', exact: true }).click()
    await expect.poll(() => queries.at(-1)?.get('action')).toBe('AUTHOR_IMPORTED')
    await page.getByRole('tab', { name: '登录日志', exact: true }).click()
    await expect(page).toHaveURL(/category=LOGIN/)
    await expect.poll(() => queries.at(-1)?.get('category')).toBe('LOGIN')
    expect(queries.at(-1)?.has('action')).toBe(false)
    expect(queries.at(-1)?.get('username')).toBe('检索测试')
    expect(queries.at(-1)?.get('result')).toBe('FAILURE')
    await page.getByRole('button', { name: '按事件类型筛选', exact: true }).click()
    await page.getByRole('combobox', { name: '登录事件', exact: true }).press('ArrowDown')
    await expect(page.getByRole('option', { name: '导入作者资料', exact: true })).toHaveCount(0)
    await page.getByRole('option', { name: '登录失败', exact: true }).click()
    await expect.poll(() => queries.at(-1)?.get('action')).toBe('LOGIN_FAILED')

    await page.getByRole('button', { name: '重置', exact: true }).click()
    await expect.poll(() => queries.at(-1)?.has('username')).toBe(false)
    expect(queries.at(-1)?.has('result')).toBe(false)
    expect(queries.at(-1)?.has('action')).toBe(false)
    expect(queries.at(-1)?.get('category')).toBe('LOGIN')
    await expect(page.getByRole('textbox', { name: '账号搜索' })).toHaveValue('')
    expect(errors).toEqual([])
  })
}

test('时间范围校验、分页和刷新使用已提交条件，旧请求日志入口转到登录日志', async ({ page }) => {
  const { queries, errors } = await setup(page)
  await page.goto(`${base}/request-logs?category=LOGIN`)
  await expect(page).toHaveURL(/\/logs\?category=LOGIN$/)
  await expect(page.getByRole('tab', { name: '登录日志' })).toHaveAttribute('aria-selected', 'true')
  await page.getByRole('button', { name: '按时间范围筛选' }).click()
  const form = page.locator('.logs-time-filter')
  await form.getByRole('combobox', { name: '开始时间' }).fill('2026-09-12 10:00:00')
  await form.getByRole('combobox', { name: '开始时间' }).press('Tab')
  await form.getByRole('combobox', { name: '结束时间' }).fill('2026-09-11 10:00:00')
  await form.getByRole('combobox', { name: '结束时间' }).press('Tab')
  const count = queries.length
  await form.getByRole('button', { name: '应用时间' }).click()
  await expect(form.getByRole('alert')).toHaveText('开始时间必须早于结束时间')
  expect(queries).toHaveLength(count)
  await form.getByRole('combobox', { name: '结束时间' }).fill('2026-09-13 10:00:00')
  await form.getByRole('combobox', { name: '结束时间' }).press('Tab')
  await form.getByRole('button', { name: '应用时间' }).click()
  await expect.poll(() => queries.at(-1)?.get('from')).toBe('2026-09-12T02:00:00.000Z')
  expect(queries.at(-1)?.get('to')).toBe('2026-09-13T02:00:00.000Z')
  await expect(page.getByRole('button', { name: '按时间范围筛选' })).toHaveAttribute('aria-pressed', 'true')
  await page.getByRole('textbox', { name: '账号搜索' }).fill('尚未提交的账号')
  await page.getByRole('button', { name: '下一页' }).click()
  await expect.poll(() => queries.at(-1)?.get('page')).toBe('1')
  expect(queries.at(-1)?.has('username')).toBe(false)
  const refreshCount = queries.length
  await page.getByRole('button', { name: '刷新日志' }).click()
  await expect.poll(() => queries.length).toBe(refreshCount + 1)
  expect(queries.at(-1)?.get('page')).toBe('1')
  expect(queries.at(-1)?.has('username')).toBe(false)
  expect(queries.at(-1)?.get('from')).toBe('2026-09-12T02:00:00.000Z')
  await page.getByRole('button', { name: '按时间范围筛选' }).click()
  await form.getByRole('button', { name: '清除时间' }).click()
  await expect.poll(() => queries.at(-1)?.has('from')).toBe(false)
  expect(queries.at(-1)?.has('to')).toBe(false)
  expect(queries.at(-1)?.get('page')).toBe('0')
  expect(errors).toEqual([])
})

test('空结果保留表头筛选入口，接口故障可重试', async ({ page }) => {
  await setup(page)
  let failed = true
  await page.route('**/api/v1/operations/audits*', route => failed
    ? route.fulfill({ status: 503, json: { detail: '日志服务暂不可用' } })
    : route.fulfill({ json: { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } }))
  await page.goto(`${base}/logs`)
  await expect(page.getByText('日志服务暂不可用', { exact: true })).toBeVisible()
  failed = false
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.getByText('暂无日志记录', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '按时间范围筛选' })).toBeVisible()
  await expect(page.getByRole('button', { name: '按事件类型筛选' })).toBeVisible()
  await expect(page.getByRole('button', { name: '按结果筛选' })).toBeVisible()
})
