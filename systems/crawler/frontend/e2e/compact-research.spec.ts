import { expect, test, type Page } from '@playwright/test'
import { readFile } from 'node:fs/promises'

const base = process.env.AACV_E2E_BASE ?? ''

async function setup(page: Page, canExport = true) {
  const requests: URL[] = []
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/v1/**', route => {
    const url = new URL(route.request().url())
    const path = url.pathname.replace(/^\/crawler/, '').replace('/api/v1', '')
    requests.push(url)
    const scope = { source: 'MYSQL', filters: Object.fromEntries([...url.searchParams].map(([key, value]) => [key, Number(value)])) }
    const summary = (id: number) => ({ id, title: `学术成果与数据关联方法 ${id}`, achievementType: 'patent', publicationDate: '2025-03-11', primaryVenue: '测试期刊', doi: null, authors: ['测试学者'], topics: ['科研数据'] })
    let data: unknown = { items: [], totalElements: 0, totalPages: 0, page: 0, size: 20 }
    if (path === '/auth/me') data = { id: 7, username: 'compact-test', roles: ['RESEARCHER'], permissions: ['CATALOG_READ', 'GRAPH_READ', 'ANALYTICS_READ', ...(canExport ? ['EXPORT_CREATE', 'EXPORT_READ'] : [])] }
    else if (path === '/catalog/achievements') data = { items: Array.from({ length: 20 }, (_, i) => summary(i + 1)), totalElements: 41, totalPages: 3, page: Number(url.searchParams.get('page') ?? 0), size: 20 }
    else if (/^\/catalog\/achievements\/\d+$/.test(path)) data = { summary: summary(Number(path.split('/').at(-1))), abstractText: '可核对的完整成果摘要。', sources: [], fields: [], authorships: [], referencedWorkIds: [], language: null }
    else if (/^\/catalog\/[^/]+$/.test(path)) data = { items: [{ id: 101, displayName: '测试实体', entityType: null, externalId: null, achievementCount: 2, advisors: ['导师甲', '导师乙'] }], totalElements: 1, totalPages: 1, page: 0, size: 20 }
    else if (path === '/analytics/overview') data = { achievementCount: 20, authorCount: 10, organizationCount: 4, sourceCount: 2, scope, updatedAt: '2026-09-11T00:00:00Z' }
    else if (path === '/analytics/trends') data = { items: [{ publicationYear: 2025, achievementCount: 20 }], scope, updatedAt: '' }
    else if (path === '/analytics/distributions') data = { achievementTypes: [{ key: 'patent', label: 'patent', achievementCount: 20 }], sources: [], organizations: [], topics: [], scope, updatedAt: '' }
    else if (path === '/analytics/collaboration') data = { authors: [], organizations: [], scope, updatedAt: '' }
    return route.fulfill({ json: data })
  })
  return { requests, errors }
}

for (const width of [1440, 2549, 390]) {
  test(`成果目录紧凑搜索、日历筛选与列表空间 ${width}px`, async ({ page }, testInfo) => {
    const { requests, errors } = await setup(page)
    await page.setViewportSize({ width, height: width === 2549 ? 1403 : 900 })
    await page.goto(`${base}/catalog?achievementType=article&sourceCode=CNKI`)
    await expect(page.getByRole('link', { name: '学术成果与数据关联方法 1', exact: true })).toBeVisible()
    await expect(page.locator('.filter-bar')).toHaveCount(0)
    await expect(page.getByRole('combobox', { name: '搜索字段' })).toHaveValue('title')
    expect(requests.find(url => url.pathname.endsWith('/catalog/achievements'))?.searchParams.has('sourceCode')).toBe(false)
    if (width >= 1440) {
      const table = await page.locator('.catalog-workspace .data-table').boundingBox()
      expect(table!.y).toBeLessThan(200)
      expect(table!.height).toBeGreaterThan(width === 2549 ? 1150 : 670)
      const search = await page.getByRole('search').boundingBox()
      expect(search!.x).toBeGreaterThan(width * 0.6)
      expect(search!.width).toBeLessThanOrEqual(365)
    }
    await page.getByRole('combobox', { name: '搜索字段' }).selectOption('author')
    await page.getByRole('textbox', { name: '作者名称检索' }).fill('测试')
    await page.getByRole('list', { name: '作者候选' }).getByRole('button').click()
    await page.getByRole('button', { name: '搜索', exact: true }).click()
    await expect(page).toHaveURL(/authorId=101/)
    await page.getByRole('button', { name: '按发表年份筛选' }).click()
    await page.getByRole('button', { name: '2025', exact: true }).click()
    await expect(page).toHaveURL(/publicationYear=2025/)
    await page.reload()
    await expect(page.getByRole('combobox', { name: '搜索字段' })).toHaveValue('author')
    await expect(page.getByRole('textbox', { name: '作者名称检索' })).toHaveValue('测试实体')
    await expect(page.getByRole('button', { name: '按发表年份筛选' })).toContainText('2025')
    await page.screenshot({ path: testInfo.outputPath(`catalog-${width}.png`), animations: 'disabled' })
    await page.getByRole('button', { name: '预览成果：学术成果与数据关联方法 1', exact: true }).click()
    await expect(page.getByText('可核对的完整成果摘要。')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
    expect(errors).toEqual([])
  })
}

test('实体编目显示内部标识和类型，新成果编目可打开详情', async ({ page }) => {
  const { errors } = await setup(page)
  for (const [collection, label] of [['authors', '作者'], ['organizations', '机构'], ['venues', '期刊'], ['patents', '专利'], ['master-theses', '指导硕论'], ['doctoral-theses', '指导博论']]) {
    await page.goto(`${base}/catalog/${collection}`)
    await expect(page.getByRole('columnheader', { name: '内部标识' })).toBeVisible()
    await expect(page.getByRole('cell', { name: '101', exact: true })).toBeVisible()
    await expect(page.getByRole('cell', { name: label, exact: true })).toBeVisible()
    if (['master-theses', 'doctoral-theses'].includes(collection!)) {
      await expect(page.getByRole('columnheader', { name: '导师', exact: true })).toBeVisible()
      await expect(page.getByRole('cell', { name: '导师甲、导师乙', exact: true })).toBeVisible()
    } else {
      await expect(page.getByRole('columnheader', { name: '导师', exact: true })).toHaveCount(0)
    }
    if (['patents', 'master-theses', 'doctoral-theses'].includes(collection!)) {
      await page.getByRole('button', { name: '查看详情', exact: true }).click()
      await expect(page.getByText('可核对的完整成果摘要。')).toBeVisible()
    }
  }
  expect(errors).toEqual([])
})

for (const width of [1440, 2549, 390]) {
  test(`统计紧凑搜索、年份边界和当前结果导出 ${width}px`, async ({ page }, testInfo) => {
    const { requests, errors } = await setup(page)
    await page.setViewportSize({ width, height: width === 2549 ? 1403 : 900 })
    await page.goto(`${base}/analytics`)
    await expect(page.getByRole('img', { name: '年度成果趋势折线图' })).toBeVisible()
    await expect(page.locator('.filter-bar')).toHaveCount(0)
    if (width === 1440) {
      const chart = await page.getByRole('img', { name: '年度成果趋势折线图' }).boundingBox()
      expect(chart!.y).toBeLessThan(300)
      expect(chart!.height).toBeGreaterThan(580)
    }
    await page.getByRole('combobox', { name: '搜索字段' }).selectOption('topicId')
    await page.getByRole('textbox', { name: '主题名称检索' }).fill('测试')
    await page.getByRole('list', { name: '主题候选' }).getByRole('button').click()
    await page.getByRole('button', { name: '搜索', exact: true }).click()
    await expect(page.getByText('实际范围：主题ID 101')).toBeVisible()
    await page.getByRole('button', { name: '查看趋势表格' }).click()
    await page.getByRole('columnheader', { name: /发表年份/ }).getByRole('button').click()
    await page.getByRole('button', { name: '选择起始年份' }).click()
    await page.getByRole('button', { name: '2026', exact: true }).click()
    await page.getByRole('button', { name: '选择结束年份' }).click()
    await page.getByRole('button', { name: '2025', exact: true }).click()
    await expect(page.getByRole('button', { name: '应用年份' })).toBeDisabled()
    await expect(page.getByText('起始年份不能晚于结束年份。')).toBeVisible()
    await page.getByRole('button', { name: '选择起始年份' }).click()
    await page.getByRole('button', { name: '2025', exact: true }).click()
    await page.getByRole('button', { name: '应用年份' }).click()
    await expect(page.getByText('实际范围：起始年份 2025 · 结束年份 2025 · 主题ID 101')).toBeVisible()
    await page.getByRole('button', { name: '导出统计' }).click()
    const downloaded = page.waitForEvent('download')
    await page.getByRole('button', { name: '导出 CSV' }).click()
    const download = await downloaded
    expect(download.suggestedFilename()).toBe('aacv-statistics.csv')
    const csv = await readFile((await download.path())!, 'utf8')
    expect(csv).toContain('""topicId"":101')
    expect(csv).toContain('"年度趋势","2025","","20"')
    expect(requests.filter(url => url.pathname.includes('/analytics/')).every(url => !url.searchParams.has('sourceType') && !url.searchParams.has('achievementType'))).toBe(true)
    await page.getByRole('heading', { name: '统计分析', exact: true }).click()
    await page.screenshot({ path: testInfo.outputPath(`analytics-${width}.png`), animations: 'disabled' })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
    expect(errors).toEqual([])
  })
}

test('统计部分失败可重试，缺少导出权限时隐藏入口', async ({ page }) => {
  await setup(page, false)
  await page.route('**/api/v1/analytics/trends', route => route.fulfill({ status: 503, json: { detail: '趋势暂不可用' } }), { times: 1 })
  await page.goto(`${base}/analytics`)
  await expect(page.getByRole('alert')).toContainText('趋势暂不可用')
  await expect(page.getByRole('button', { name: '导出统计' })).toHaveCount(0)
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(page.getByRole('img', { name: '年度成果趋势折线图' })).toBeVisible()
  await expect(page.getByRole('alert')).toHaveCount(0)
  await page.goto(`${base}/catalog`)
  await expect(page.getByRole('button', { name: '导出成果' })).toHaveCount(0)
})
