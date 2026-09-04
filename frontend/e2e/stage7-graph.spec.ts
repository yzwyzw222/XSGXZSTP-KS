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
})
