import { expect, test, type Page } from '@playwright/test'

async function setup(page: Page) {
  await page.route('**/api/v1/**', (route) => route.fulfill({ json: { items: [], totalElements: 0, totalPages: 0, page: 0, size: 20 } }))
  await page.route('**/api/v1/auth/me', (route) => route.fulfill({ json: {
    id: 3, username: 'catalog-test', roles: ['DATA_ANALYST'], permissions: ['CATALOG_READ'],
  } }))
  for (const collection of ['authors', 'organizations']) {
    await page.route(`**/api/v1/catalog/${collection}?*`, (route) => route.fulfill({ json: {
      items: [{ id: 10, displayName: collection === 'authors' ? '同名作者的独立记录' : '当前规范机构名称', externalId: 'source-10', achievementCount: 3 }],
      totalElements: 1, totalPages: 1, page: 0, size: 20,
    } }))
  }
}

test('机构抽屉展示来源别名与观测时间，窄屏仍可阅读', async ({ page }) => {
  await setup(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.route('**/api/v1/catalog/organizations/10/evidence', (route) => route.fulfill({ json: {
    entityId: 10, entityType: 'ORGANIZATION', namesTruncated: true, affiliationsTruncated: false, affiliations: [],
    names: [{ displayName: '曾采集到的机构名称', sourceCode: 'OPENALEX', firstObservedAt: '2026-01-01T00:00:00Z', lastObservedAt: '2026-09-01T00:00:00Z' }],
  } }))
  await page.goto('/catalog/organizations')
  await page.getByRole('button', { name: '成果与证据' }).click()
  await expect(page.getByRole('heading', { name: '机构来源名称' })).toBeVisible()
  await expect(page.getByText('曾采集到的机构名称')).toBeVisible()
  await expect(page.getByText('仅显示最近观测的100项名称证据。')).toBeVisible()
  await expect(page.getByText('观测时间不代表更名时间', { exact: false })).toBeVisible()
  await page.screenshot({ path: 'test-results/catalog-organization-evidence.png', animations: 'disabled' })
})

test('作者区间区分出版年与任职，缺失年份不显示虚构区间', async ({ page }) => {
  await setup(page)
  await page.route('**/api/v1/catalog/authors/10/evidence', (route) => route.fulfill({ json: {
    entityId: 10, entityType: 'AUTHOR', namesTruncated: false, affiliationsTruncated: false, names: [],
    affiliations: [
      { organizationId: 20, displayName: '某大学', firstPublicationYear: 2018, lastPublicationYear: 2025, achievementCount: 3, datedAchievementCount: 2 },
      { organizationId: 21, displayName: '未定年机构', firstPublicationYear: null, lastPublicationYear: null, achievementCount: 1, datedAchievementCount: 0 },
    ],
  } }))
  await page.goto('/catalog/authors')
  await page.getByRole('button', { name: '成果与证据' }).click()
  await expect(page.getByText('署名观测区间：2018—2025')).toBeVisible()
  await expect(page.getByText('署名观测区间：年份未知')).toBeVisible()
  await expect(page.getByText('不代表连续任职或当前单位', { exact: false })).toBeVisible()
  await expect(page.getByText('3 项规范成果 · 2 项具有出版日期')).toBeVisible()
})

test('成果学术信息分来源呈现，保留未知并提供明确版本链接', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await setup(page)
  const observedAt = '2026-09-01T00:00:00Z'
  const source = (id: number, sourceCode: string, metadata: unknown) => ({
    sourceRecordId: id, rawRecordId: id, sourceCode, externalRecordId: `${sourceCode}-1`, sourceUrl: null,
    firstSeenAt: observedAt, lastSeenAt: observedAt, parserVersion: 'v2', scholarlyMetadata: metadata,
  })
  await page.route('**/api/v1/catalog/achievements/1', (route) => route.fulfill({ json: {
    summary: { id: 1, title: '来源证据与论文版本', doi: '10.1000/work', achievementType: 'article', publicationDate: '2026-08-01', primaryVenue: null, authors: [], topics: [] },
    language: 'en', abstractText: null, authorshipsMayBeIncomplete: false, authorships: [], referencedWorkIds: [], fields: [],
    sources: [
      source(1, 'OPENALEX', { observedAt, citedByCount: 0, retracted: true, openAccess: true, openAccessStatus: 'gold', versionRelations: [] }),
      source(2, 'CROSSREF', { observedAt, citedByCount: 8, retracted: null, openAccess: null, openAccessStatus: null,
        versionRelations: [{ relationType: 'is-preprint-of', targetDoi: '10.1000/published' }] }),
      source(3, 'OLDER', null),
    ],
  } }))
  await page.goto('/catalog/achievements/1')
  await expect(page.getByText('来源标记已撤稿')).toBeVisible()
  await expect(page.getByText('尚未采集', { exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: '10.1000/published' })).toHaveAttribute('href', 'https://doi.org/10.1000%2Fpublished')
  await expect(page.getByText('正式发表版本：')).toBeVisible()
  await page.getByRole('heading', { name: '来源学术指标与版本' }).scrollIntoViewIfNeeded()
  await page.screenshot({ path: 'test-results/catalog-scholarly-evidence.png', animations: 'disabled' })
  expect(errors).toEqual([])
})
