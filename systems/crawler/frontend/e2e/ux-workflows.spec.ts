import { expect, test } from '@playwright/test'
import { achievement, fixture, time } from './fixtures/workbench'

test('同名候选保留 ID，详情返回与刷新恢复分页，导出使用已显示的结果条件', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await fixture(page)
  const queries: URL[] = []
  const exports: unknown[] = []
  await page.route('**/api/v1/catalog/authors?*', route => route.fulfill({ json: {
    items: [{ id: 7, displayName: '王伟', achievementCount: 10 }, { id: 8, displayName: '王伟', achievementCount: 2 }], totalElements: 2,
  } }))
  await page.route('**/api/v1/catalog/achievements?*', route => {
    const url = new URL(route.request().url()); queries.push(url)
    return route.fulfill({ json: { items: [achievement], page: Number(url.searchParams.get('page') ?? 0), size: 20, totalElements: 45, totalPages: 3 } })
  })
  await page.route('**/api/v1/exports', route => {
    exports.push(route.request().postDataJSON())
    return route.fulfill({ status: 202, json: { id: 'test-export', format: 'CSV', status: 'FAILED', requestedCount: 45, exportedCount: 0, downloadAvailable: false, createdAt: time, errorMessage: '测试终态' } })
  })
  await page.goto('/catalog?title=开放&page=0&size=20')
  await page.getByRole('textbox', { name: '作者名称检索' }).fill('王伟')
  await page.getByRole('list', { name: '作者候选' }).getByRole('button').filter({ hasText: '2 成果' }).click()
  await page.getByRole('button', { name: '查询成果' }).click()
  await expect.poll(() => queries.at(-1)?.searchParams.get('authorId')).toBe('8')
  await page.locator('.el-pagination .btn-next').click()
  await expect.poll(() => queries.at(-1)?.searchParams.get('page')).toBe('1')
  await page.getByRole('link', { name: achievement.title, exact: true }).click()
  await expect(page.getByRole('heading', { name: achievement.title })).toBeVisible()
  await page.locator('a[href^="/catalog?"]').click()
  await expect(page.getByRole('textbox', { name: '作者名称检索' })).toHaveValue('王伟')
  await expect(page).toHaveURL(/page=1/)
  await page.reload()
  await expect.poll(() => queries.at(-1)?.searchParams.get('authorId')).toBe('8')
  await page.locator('label:has-text("题名") input').fill('尚未提交的条件')
  await page.getByRole('button', { name: '导出 CSV' }).click()
  await expect.poll(() => exports.length).toBe(1)
  expect(exports[0]).toEqual({ format: 'CSV', filters: { title: '开放', author: '王伟', authorId: 8 } })
  expect(errors).toEqual([])
})

test('自由输入模糊条件可直接导出，无需匹配唯一实体', async ({ page }) => {
  await fixture(page)
  let filters: unknown
  await page.route('**/api/v1/exports', route => {
    filters = route.request().postDataJSON().filters
    return route.fulfill({ status: 202, json: { id: 'fuzzy-export', format: 'JSON', status: 'FAILED', requestedCount: 0, exportedCount: 0, createdAt: time } })
  })
  await page.goto('/catalog?author=王&organization=研究院')
  await page.getByRole('button', { name: '导出 JSON' }).click()
  await expect.poll(() => filters).toEqual({ author: '王', organization: '研究院' })
})

for (const width of [1440, 390]) test(`工作台提供导入记录与学者图谱入口，${width}px 下无水平溢出`, async ({ page }) => {
  await fixture(page)
  await page.setViewportSize({ width, height: 950 })
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: { id: 1, username: 'tester', roles: ['DATA_OPERATOR'], permissions: ['CATALOG_READ', 'AUTHOR_IMPORT', 'GRAPH_READ'] } }))
  await page.route('**/api/v1/author-import', route => route.fulfill({ json: [{ id: 2, authorId: 12, scholarName: '测试学者', fileName: '论文.csv', importMode: 'AUTHOR', importedCount: 3, skippedCount: 1, createdAt: time }] }))
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '学者研究工作台', exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: '测试学者', exact: true })).toHaveAttribute('href', '/graph/explore?centerType=AUTHOR&centerId=12&depth=2')
  await expect(page.getByText('新增 3 项 · 已存在 1 项')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: `test-results/workbench-${width}.png`, fullPage: true })
  await page.getByRole('textbox', { name: '查找研究成果' }).fill('开放科学')
  await page.getByRole('button', { name: '检索成果' }).click()
  await expect(page).toHaveURL(/catalog\?title=/)
})

