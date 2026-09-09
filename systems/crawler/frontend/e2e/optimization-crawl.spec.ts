import { expect, test, type Page } from '@playwright/test'

async function setup(page: Page, reason: 'QUOTA_EXHAUSTED' | 'PAGE_LIMIT') {
  const run = {
    id: 15, taskId: 1, runNumber: 'run-coverage-15', status: reason === 'PAGE_LIMIT' ? 'PARTIAL_SUCCESS' : 'PAUSED',
    readCount: 100, parsedCount: 100, createdCount: 100, updatedCount: 0, duplicateCount: 0,
    failureCount: 0, requestCount: 1, completionReason: reason as string,
    deferredUntil: reason === 'QUOTA_EXHAUSTED' ? '2099-01-01T00:00:00Z' : null,
    quotaDeferrals: 1, startedAt: null,
  }
  await page.route('**/api/v1/**', (route) => route.fulfill({
    json: { items: [], totalElements: 0, totalPages: 0, page: 0, size: 20 },
  }))
  await page.route('**/api/v1/auth/me', (route) => route.fulfill({ json: {
    id: 3, username: 'crawl-test', roles: ['DATA_OPERATOR'],
    permissions: ['CRAWL_TASK_READ', 'CRAWL_RUN_READ', 'CRAWL_TASK_CREATE', 'CRAWL_TASK_CONTROL'],
  } }))
  await page.route('**/api/v1/auth/csrf', (route) => route.fulfill({ json: {
    headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'csrf-test-only',
  } }))
  await page.route('**/api/v1/crawl/runs/15', (route) => route.fulfill({ json: run }))
  await page.route('**/api/v1/crawl/runs/15/pause', (route) => {
    run.completionReason = 'USER_PAUSED'
    run.deferredUntil = null
    return route.fulfill({ json: run })
  })
  await page.goto('/crawl')
}

test('达到采集上限明确提示未完整，历史复查提供日期范围', async ({ page }) => {
  await setup(page, 'PAGE_LIMIT')
  await page.getByRole('button', { name: '历史复查', exact: true }).click()
  await expect(page.locator('#dateFrom')).toHaveValue(/^\d{4}-\d{2}-01$/)
  await expect(page.locator('#dateTo')).toHaveValue(/^\d{4}-\d{2}-(28|29|30|31)$/)
  await expect(page.locator('#taskName')).toHaveValue(/^历史复查 \d{4}-\d{2}$/)
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.getByRole('spinbutton', { name: '运行编号' }).fill('15')
  await page.getByRole('button', { name: '查询运行' }).click()
  await expect(page.getByText('达到页数上限，范围尚未采集完整。', { exact: false })).toBeVisible()
  await expect(page.getByText('下方百分比表示本次任务执行进度，不表示来源数据覆盖率。')).toBeVisible()
})

test('每日额度等待展示恢复安排并允许停止自动恢复', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await setup(page, 'QUOTA_EXHAUSTED')
  await page.getByRole('spinbutton', { name: '运行编号' }).fill('15')
  await page.getByRole('button', { name: '查询运行' }).click()
  await expect(page.getByText('来源每日额度耗尽，已保留检查点；额度恢复后自动继续，最多三次。')).toBeVisible()
  await expect(page.getByRole('button', { name: '恢复', exact: true })).toHaveCount(0)
  await page.screenshot({ path: 'test-results/crawl-quota.png', animations: 'disabled' })
  await page.getByRole('button', { name: '停止自动恢复' }).click()
  await expect(page.getByText('已由用户暂停，等待手动恢复。')).toBeVisible()
  await expect(page.getByRole('button', { name: '恢复', exact: true })).toBeVisible()
  expect(errors).toEqual([])
})
