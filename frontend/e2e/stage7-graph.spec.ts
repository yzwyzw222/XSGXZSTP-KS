import { expect, test, type Page } from '@playwright/test'

const researcher = {
  id: 8,
  username: 'researcher',
  roles: ['RESEARCHER'],
  permissions: ['ACCOUNT_SELF_READ', 'CATALOG_READ', 'GRAPH_READ'],
}

async function mockJson(page: Page, url: string, body: unknown): Promise<void> {
  await page.route(url, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(body),
    }),
  )
}

test('研究人员按业务节点加载受限图谱并切换无障碍表格', async ({ page }) => {
  const invalidStyles: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'warning' && message.text().includes('style property')) invalidStyles.push(message.text())
  })
  await mockJson(page, '**/api/v1/auth/me', researcher)
  await mockJson(page, '**/api/v1/graph/subgraph**', {
    nodes: [
      {
        id: 'ACHIEVEMENT:42',
        businessId: '42',
        type: 'ACHIEVEMENT',
        label: '可信软件供应链研究',
        properties: { publicationYear: 2026 },
      },
      {
        id: 'AUTHOR:7',
        businessId: '7',
        type: 'AUTHOR',
        label: '张研究员',
        properties: {},
      },
    ],
    edges: [
      {
        id: 'AUTHORED:7:42',
        type: 'AUTHORED',
        source: 'AUTHOR:7',
        target: 'ACHIEVEMENT:42',
        properties: {},
      },
    ],
    rootNodeId: 'ACHIEVEMENT:42',
    truncated: true,
    narrowingSuggestion: '结果达到上限，请增加类型或年份过滤条件。',
    appliedLimits: { depth: 1, nodeLimit: 100, maxHops: 6 },
    syncedAt: '2026-09-02T09:30:00Z',
    projectionLagSeconds: 8,
    traceId: 'trace-stage7-graph',
  })

  await page.goto('/graph/explore')
  await expect(page.getByRole('heading', { name: '高级查询' })).toBeVisible()
  await page.locator('label:has-text("中心业务ID") input').fill('42')
  await page.getByRole('button', { name: '加载图谱' }).click()

  await expect(page.getByRole('img', { name: '知识图谱，共2个节点和1条关系' })).toBeVisible()
  await expect(page.getByText('结果达到上限，请增加类型或年份过滤条件。')).toBeVisible()
  await expect(page.getByText('Trace trace-stage7-graph')).toBeVisible()
  await expect(page.getByRole('link', { name: '进入业务详情 →' })).toHaveAttribute(
    'href',
    '/catalog/achievements/42',
  )

  await page.getByRole('button', { name: '节点表' }).click()
  await expect(page.getByRole('cell', { name: '可信软件供应链研究' })).toBeVisible()
  await expect(page.getByRole('cell', { name: '张研究员' })).toBeVisible()

  await page.getByRole('button', { name: '关系表' }).click()
  await expect(page.getByRole('cell', { name: '创作' })).toBeVisible()
  expect(invalidStyles).toEqual([])
})

test('按名称选择图谱中心、保存筛选并从详情链接自动加载', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await mockJson(page, '**/api/v1/auth/me', researcher)
  await mockJson(page, '**/api/v1/catalog/achievements**', {
    items: [{ id: 21, title: '学术关系分析', publicationDate: '2026-01-01', doi: null, primaryVenue: '示例期刊' }],
    totalElements: 1, page: 0, size: 10,
  })
  const centers: string[] = []
  await page.route('**/api/v1/graph/subgraph**', (route) => {
    const center = new URL(route.request().url()).searchParams.get('centerId')!
    centers.push(center)
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      nodes: [{ id: `ACHIEVEMENT:${center}`, businessId: center, type: 'ACHIEVEMENT', label: '学术关系分析', properties: {} }],
      edges: [], rootNodeId: `ACHIEVEMENT:${center}`, truncated: false,
      appliedLimits: { depth: 1, nodeLimit: 100, maxHops: 6 }, syncedAt: '2026-09-05T00:00:00Z',
      projectionLagSeconds: 0, traceId: 'graph-lookup-test',
    }) })
  })
  await page.goto('/graph/explore')
  await page.getByRole('textbox', { name: '中心名称', exact: true }).fill('学术关系')
  await page.getByRole('button', { name: '查找中心' }).click()
  await page.getByRole('list', { name: '中心候选' }).getByRole('button').click()
  await expect(page.getByRole('spinbutton', { name: '中心业务ID' })).toHaveValue('21')
  await page.locator('summary').filter({ hasText: '常用查询' }).click()
  await page.getByRole('textbox', { name: '常用查询名称' }).fill('我的研究主题')
  await page.getByRole('button', { name: '保存当前查询' }).click()
  await page.getByRole('spinbutton', { name: '中心业务ID' }).fill('99')
  await page.getByRole('button', { name: '我的研究主题', exact: true }).click()
  await expect(page.getByRole('img', { name: '知识图谱，共1个节点和0条关系' })).toBeVisible()
  expect(centers.at(-1)).toBe('21')
  await page.goto('/graph?centerType=ACHIEVEMENT&centerId=21')
  await expect(page.getByRole('img', { name: '知识图谱，共1个节点和0条关系' })).toBeVisible()
  expect(centers.at(-1)).toBe('21')
  await page.screenshot({ path: 'test-results/graph-lookup.png', fullPage: true, animations: 'disabled' })
  expect(errors).toEqual([])
})

test('路径配置保留查询条件，校验及服务失败可见且保留上次图谱', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(() => {
    const runtime = window as typeof window & { pathErrors: string[] }
    runtime.pathErrors = []
    window.addEventListener('error', event => runtime.pathErrors.push(event.message))
  })
  async function assertNoBrowserErrors(stage: string): Promise<void> {
    await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))))
    expect(await page.evaluate(() => (window as typeof window & { pathErrors: string[] }).pathErrors), stage).toEqual([])
  }
  await mockJson(page, '**/api/v1/auth/me', researcher)
  const queries: Record<string, string>[] = []
  let failPath = false
  let successfulQueries = 0
  await page.route('**/api/v1/graph/path**', route => {
    const query = Object.fromEntries(new URL(route.request().url()).searchParams)
    queries.push(query)
    if (failPath) {
      return route.fulfill({
        status: 503,
        contentType: 'application/problem+json',
        body: JSON.stringify({ detail: '路径查询服务暂不可用，请稍后重试。', errorCode: 'GRAPH_UNAVAILABLE' }),
      })
    }
    successfulQueries++
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      nodes: [
        { id: 'AUTHOR:7', businessId: '7', type: 'AUTHOR', label: '张研究员', properties: {} },
        { id: 'ACHIEVEMENT:42', businessId: '42', type: 'ACHIEVEMENT', label: '可信软件供应链研究', properties: {} },
        { id: 'TOPIC:3', businessId: '3', type: 'TOPIC', label: '软件工程', properties: {} },
      ],
      edges: [
        { id: 'AUTHORED:7:42', type: 'AUTHORED', source: 'AUTHOR:7', target: 'ACHIEVEMENT:42', properties: {} },
        { id: 'HAS_TOPIC:42:3', type: 'HAS_TOPIC', source: 'ACHIEVEMENT:42', target: 'TOPIC:3', properties: {} },
      ],
      rootNodeId: 'AUTHOR:7', truncated: false,
      appliedLimits: { depth: 1, nodeLimit: 100, maxHops: 4 }, syncedAt: '2026-09-05T00:00:00Z',
      projectionLagSeconds: 0, traceId: `path-regression-${successfulQueries}`,
    }) })
  })

  await page.goto('/graph/path')
  await assertNoBrowserErrors('配置初始')
  const sourceId = page.getByRole('spinbutton', { name: '起点业务ID' })
  const targetId = page.getByRole('spinbutton', { name: '终点业务ID' })
  const maxHops = page.locator('label').filter({ hasText: '最大跳数' }).locator('.el-select__wrapper')
  const submit = page.getByRole('button', { name: '查询路径', exact: true })
  const graph = page.getByRole('img', { name: '知识图谱，共3个节点和2条关系' })
  for (const [label, option] of [['起点类型', '作者'], ['终点类型', '主题']]) {
    const field = page.locator('label').filter({ hasText: label })
    await field.locator('.el-select__wrapper').click()
    const listId = await field.getByRole('combobox').getAttribute('aria-controls')
    await page.locator(`[id="${listId}"]`).getByRole('option', { name: option, exact: true }).click()
  }
  await sourceId.fill('7')
  await targetId.fill('3')
  await maxHops.click()
  await page.getByRole('option', { name: '4 跳', exact: true }).click()
  await assertNoBrowserErrors('选择跳数')
  await submit.click()
  await expect(graph).toBeVisible()
  await expect(page.getByText('Trace path-regression-1', { exact: true })).toBeVisible()
  expect(queries).toEqual([{ sourceType: 'AUTHOR', sourceId: '7', targetType: 'TOPIC', targetId: '3', maxHops: '4' }])
  await assertNoBrowserErrors('首次路径结果')

  await page.getByRole('button', { name: '路径配置', exact: true }).click()
  await expect(sourceId).toBeVisible()
  await expect(sourceId).toHaveValue('7')
  await expect(targetId).toHaveValue('3')
  await expect(page.locator('label').filter({ hasText: '最大跳数' })).toContainText('4 跳')
  await expect(graph).toBeHidden()
  await assertNoBrowserErrors('重新打开路径配置')

  // 数字控件会把显式 0 钳制为 1；清空 ID 后页面解析为 0，覆盖同一非法值分支。
  await sourceId.fill('')
  await submit.click()
  await expect(page.getByRole('alert').filter({ hasText: '请输入大于0的路径起点和终点业务ID。' })).toBeVisible()
  expect(queries).toHaveLength(1)
  await assertNoBrowserErrors('非法路径输入')

  await sourceId.fill('7')
  failPath = true
  await submit.click()
  await expect(page.getByRole('alert').filter({ hasText: '路径查询服务暂不可用，请稍后重试。' })).toBeVisible()
  await expect(sourceId).toHaveValue('7')
  await expect(targetId).toHaveValue('3')
  expect(queries).toHaveLength(2)
  await assertNoBrowserErrors('路径服务失败')
  await page.getByRole('button', { name: '返回图谱', exact: true }).click()
  await expect(graph).toBeVisible()
  await expect(page.getByText('Trace path-regression-1', { exact: true })).toBeVisible()
  await assertNoBrowserErrors('失败后返回旧图')

  await page.getByRole('button', { name: '路径配置', exact: true }).click()
  failPath = false
  await submit.click()
  await expect(graph).toBeVisible()
  await expect(page.getByText('Trace path-regression-2', { exact: true })).toBeVisible()
  await expect(page.getByRole('alert')).toHaveCount(0)
  expect(queries).toHaveLength(3)
  expect(queries[2]).toEqual(queries[0])
  expect(errors).toEqual([])
  await assertNoBrowserErrors('重新查询成功')
})
