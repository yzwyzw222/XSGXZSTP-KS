import { expect, test, type Route } from '@playwright/test'

const researcher = {
  id: 8,
  username: 'researcher',
  roles: ['RESEARCHER'],
  permissions: ['ACCOUNT_SELF_READ', 'CATALOG_READ', 'EXPORT_CREATE', 'EXPORT_READ'],
}
const exportId = '18e0f81b-e07a-4f19-9265-a2fe48e35b41'
const downloadToken = 'abcdefghijklmnopqrstuvwxyz1234567890ABCDEFG'

async function fulfill(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

test('研究人员按当前目录筛选创建、轮询并下载CSV导出', async ({ page }) => {
  await page.route('**/api/v1/auth/me', (route) => fulfill(route, researcher))
  await page.route('**/api/v1/auth/csrf', (route) => fulfill(route, {
    headerName: 'X-CSRF-TOKEN',
    parameterName: '_csrf',
    token: 'csrf-token',
  }))
  await page.route('**/api/v1/catalog/achievements**', (route) => fulfill(route, {
    items: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  }))
  await page.route('**/api/v1/exports', async (route) => {
    expect(route.request().method()).toBe('POST')
    expect(route.request().headers()['x-csrf-token']).toBe('csrf-token')
    expect(route.request().postDataJSON()).toEqual({
      format: 'CSV',
      filters: {
        title: '可信计算',
        publicationYearFrom: 2026,
        publicationYearTo: 2026,
        achievementType: 'article',
        sourceType: 'OPENALEX',
      },
    })
    await fulfill(route, {
      id: exportId,
      format: 'CSV',
      status: 'PENDING',
      requestedBy: 8,
      requestedCount: 1,
      exportedCount: 0,
      downloadAvailable: false,
      downloadToken: null,
      createdAt: '2026-09-03T00:00:00Z',
      startedAt: null,
      completedAt: null,
      expiresAt: null,
      errorCode: null,
      errorMessage: null,
    }, 202)
  })
  await page.route(`**/api/v1/exports/${exportId}`, (route) => fulfill(route, {
    id: exportId,
    format: 'CSV',
    status: 'SUCCEEDED',
    requestedBy: 8,
    requestedCount: 1,
    exportedCount: 1,
    downloadAvailable: true,
    downloadToken,
    createdAt: '2026-09-03T00:00:00Z',
    startedAt: '2026-09-03T00:00:01Z',
    completedAt: '2026-09-03T00:00:02Z',
    expiresAt: '2026-09-04T00:00:02Z',
    errorCode: null,
    errorMessage: null,
  }))
  await page.route(`**/api/v1/exports/${exportId}/download?token=*`, (route) => route.fulfill({
    status: 200,
    contentType: 'text/csv',
    body: 'title\r\n可信计算',
  }))

  await page.goto('/catalog')
  await page.locator('label:has-text("题名") input').fill('可信计算')
  await page.locator('label:has-text("出版年份") input').fill('2026')
  await page.locator('label:has-text("成果类型") input').fill('article')
  await page.locator('label:has-text("来源代码") input').fill('openalex')
  await page.getByRole('button', { name: '导出 CSV' }).click()

  await expect(page.getByText('导出完成')).toBeVisible()
  await expect(page.getByText(`任务 ${exportId}`)).toBeVisible()
  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: '下载文件' }).click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe(`aacv-achievements-${exportId}.csv`)
})
