import { expect, test, type Page } from '@playwright/test'
import type { CrawlTask, CrawlTaskParameters } from '../src/types/api'

const task: CrawlTask = { id: 7, sourceId: 31, name: '已有名称筛选任务', enabled: true, version: 0, parameterVersion: 1,
  createdAt: '2026-09-10T00:00:00Z', updatedAt: '2026-09-10T00:00:00Z', parameters: {
    authorIds: ['https://openalex.org/A2'], institutionIds: ['I3'], keyword: 'graph neural networks',
    publicationDateFrom: null, publicationDateTo: null, updatedFrom: null, updatedUntil: null,
    dois: [], orcids: [], rorIds: [], maxPages: 1, maxRecords: 100,
  } }
const authors = [
  { id: 'A1', displayName: '张三', hint: '清华大学', worksCount: 80 },
  { id: 'A2', displayName: '张三', hint: '北京大学', worksCount: 32 },
]
const institutions = [{ id: 'I3', displayName: 'Peking University', hint: 'Beijing, China', worksCount: 1000 }]

/** 所有接口均使用夹具，不连接当前业务库或外部来源。 */
async function setup(page: Page, existing = false) {
  const saved: Array<{ sourceId: number; parameters: CrawlTaskParameters }> = []
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url())
    let body: unknown = { items: [], totalElements: 0, totalPages: 0, page: 0, size: 20 }
    if (url.pathname.endsWith('/auth/me')) body = { id: 3, username: '名称选择测试', roles: ['DATA_OPERATOR'], permissions: [
      'SOURCE_READ', 'CRAWL_TASK_READ', 'CRAWL_TASK_CREATE', 'CRAWL_TASK_UPDATE', 'CRAWL_RUN_READ',
    ] }
    else if (url.pathname.endsWith('/auth/csrf')) body = { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'test-csrf' }
    else if (url.pathname === '/api/v1/sources') body = { items: [{ id: 31, sourceType: 'OPENALEX', enabled: true }], totalPages: 1 }
    else if (url.pathname.includes('/entities/authors')) body = url.pathname.endsWith('/resolve') ? [authors[1]] : authors
    else if (url.pathname.includes('/entities/institutions')) body = institutions
    else if (url.pathname === '/api/v1/crawl/tasks' && route.request().method() === 'POST') {
      saved.push(route.request().postDataJSON())
      body = task
    } else if (url.pathname === '/api/v1/crawl/tasks') body = {
      items: existing ? [task] : [], totalElements: existing ? 1 : 0, totalPages: 1, page: 0, size: 20,
    }
    await route.fulfill({ json: body })
  })
  await page.goto('/crawl')
  return saved
}

for (const viewport of [{ width: 1366, height: 900 }, { width: 390, height: 844 }]) {
  test(`名称多选和关键词表单可用：${viewport.width}px`, async ({ page }) => {
    await page.setViewportSize(viewport)
    const errors: string[] = []
    page.on('pageerror', error => errors.push(error.message))
    const saved = await setup(page)
    await page.getByRole('button', { name: '新建采集任务', exact: true }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByText('作者名称', { exact: true })).toBeVisible()
    await expect(dialog.getByText('机构名称', { exact: true })).toBeVisible()
    await expect(dialog.getByText('标题、摘要及可检索全文', { exact: false })).toBeVisible()
    await dialog.locator('#taskName').fill('按名称限定范围')
    await dialog.locator('#keyword').fill('graph neural networks')
    await dialog.locator('#authorNames').fill('张三')
    await expect(dialog.getByRole('button', { name: /张三.*北京大学/ })).toBeVisible()
    await dialog.getByRole('button', { name: '保存', exact: true }).click()
    expect(saved).toHaveLength(0)
    await expect(dialog.getByText('未选中的搜索文字请先清空', { exact: false })).toBeVisible()
    await dialog.getByRole('button', { name: /张三.*北京大学/ }).click()
    await dialog.locator('#institutionNames').fill('Peking')
    await dialog.getByRole('button', { name: /Peking University.*Beijing/ }).click()
    await expect(dialog.locator('.el-tag')).toHaveText(['张三', 'Peking University'])
    await expect(dialog.getByText('未选中的搜索文字请先清空', { exact: false })).toHaveCount(0)
    expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true)
    await page.screenshot({ path: `test-results/crawl-entity-names-${viewport.width}.png`, fullPage: true, animations: 'disabled' })
    await dialog.getByRole('button', { name: '保存', exact: true }).click()
    await expect(dialog).toHaveCount(0)
    expect(saved[0]).toMatchObject({ sourceId: 31, parameters: { authorIds: ['A2'], institutionIds: ['I3'], keyword: 'graph neural networks' } })
    expect(errors).toEqual([])
  })
}

test('编辑旧任务只显示名称，取消不会产生写请求', async ({ page }) => {
  const saved = await setup(page, true)
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.locator('.el-tag')).toHaveText(['张三', 'Peking University'])
  await expect(dialog).not.toContainText('https://openalex.org/A2')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  expect(saved).toHaveLength(0)
})
