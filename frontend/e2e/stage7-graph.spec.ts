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

  await page.goto('/graph')
  await expect(page.getByRole('heading', { name: '知识图谱' })).toBeVisible()
  await page.locator('label:has-text("中心业务ID") input').fill('42')
  await page.getByRole('button', { name: '加载中心子图' }).click()

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
  await page.goto('/graph')
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
