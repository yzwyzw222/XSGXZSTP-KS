import { expect, test, type Page, type Route } from '@playwright/test'

const researcher = {
  id: 8,
  username: 'researcher',
  roles: ['RESEARCHER'],
  permissions: ['ACCOUNT_SELF_READ', 'CATALOG_READ', 'GRAPH_READ', 'ANALYTICS_READ'],
}
const scope = { source: 'MYSQL', filters: {} }

async function fulfill(route: Route, body: unknown): Promise<void> {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
}

test('研究人员查看MySQL统计图表、表格摘要和实际筛选范围', async ({ page }) => {
  await page.route('**/api/v1/auth/me', (route) => fulfill(route, researcher))
  await page.route('**/api/v1/analytics/**', (route) => {
    const pathname = new URL(route.request().url()).pathname
    if (pathname.endsWith('/overview')) {
      return fulfill(route, {
        achievementCount: 12,
        authorCount: 8,
        organizationCount: 3,
        sourceCount: 2,
        coverage: {
          withDoiCount: 9, withPublicationYearCount: 12, withAbstractCount: 8,
          withCitationCount: 6, withOpenAccessStatusCount: 6, withRetractionStatusCount: 6,
          authorshipsMayBeIncompleteCount: 1,
        },
        scope,
        updatedAt: '2026-09-02T10:00:00Z',
      })
    }
    if (pathname.endsWith('/trends')) {
      return fulfill(route, {
        items: [
          { publicationYear: 2025, achievementCount: 5 },
          { publicationYear: 2026, achievementCount: 7 },
        ],
        scope,
        updatedAt: '2026-09-02T10:00:00Z',
      })
    }
    if (pathname.endsWith('/distributions')) {
      return fulfill(route, {
        achievementTypes: [{ key: 'article', label: 'article', achievementCount: 9 }],
        sources: [{ key: 'OPENALEX', label: 'OPENALEX', achievementCount: 8 }],
        organizations: [{ key: '3', label: '可信计算实验室', achievementCount: 6 }],
        topics: [{ key: '5', label: '软件安全', achievementCount: 4 }],
        scope,
        updatedAt: '2026-09-02T10:00:00Z',
      })
    }
    return fulfill(route, {
      authors: [{ leftId: 1, leftLabel: '张研究员', rightId: 2, rightLabel: '李研究员', sharedAchievementCount: 3 }],
      organizations: [{ leftId: 3, leftLabel: '可信计算实验室', rightId: 4, rightLabel: '软件工程实验室', sharedAchievementCount: 2 }],
      scope,
      updatedAt: '2026-09-02T10:00:00Z',
    })
  })

  await page.goto('/analytics')
  await expect(page.getByRole('heading', { name: '统计分析' })).toBeVisible()
  await expect(page.getByText('权威来源 · MYSQL')).toBeVisible()
  await expect(page.getByRole('heading', { name: '本地字段覆盖率' })).toBeVisible()
  await expect(page.getByText('75.0%', { exact: true })).toBeVisible()
  await expect(page.getByText('各分类数量不能相加作为成果总量', { exact: false })).toBeVisible()
  await page.screenshot({ path: 'test-results/analytics-coverage.png', animations: 'disabled' })
  await expect(page.getByRole('img', { name: '年度成果趋势折线图' })).toBeVisible()
  await expect(page.getByRole('img', { name: '机构成果分布条形图' })).toBeVisible()
  await expect(page.getByRole('cell', { name: '张研究员 × 李研究员' })).toBeVisible()

  await page.getByText('查看趋势表格').click()
  await expect(page.getByRole('cell', { name: '2026' })).toBeVisible()
  await expect(page.getByRole('cell', { name: '7', exact: true })).toBeVisible()

  await page.locator('label:has-text("起始年份") input').fill('2025')
  const filteredRequest = page.waitForRequest((request) =>
    request.url().includes('/api/v1/analytics/overview?publicationYearFrom=2025'),
  )
  await page.getByRole('button', { name: '应用筛选' }).click()
  await filteredRequest
})
