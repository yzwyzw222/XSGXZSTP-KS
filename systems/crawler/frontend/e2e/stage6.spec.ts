import { expect, test, type Page } from '@playwright/test'

const researcher = {
  id: 8,
  username: 'researcher',
  roles: ['RESEARCHER'],
  permissions: ['ACCOUNT_SELF_READ', 'CATALOG_READ', 'GRAPH_READ'],
}

async function mockJson(page: Page, url: string, body: unknown, status = 200): Promise<void> {
  await page.route(url, (route) =>
    route.fulfill({
      status,
      contentType: status >= 400 ? 'application/problem+json' : 'application/json',
      body: JSON.stringify(body),
    }),
  )
}

test('未登录用户登录后返回成果目录并看到空状态', async ({ page }) => {
  await mockJson(page, '**/api/v1/auth/me', { title: 'Unauthorized', status: 401 }, 401)
  await mockJson(page, '**/api/v1/auth/csrf', {
    headerName: 'X-CSRF-TOKEN',
    parameterName: '_csrf',
    token: 'csrf-e2e',
  })
  await mockJson(page, '**/api/v1/auth/login', researcher)
  await mockJson(page, '**/api/v1/catalog/achievements**', {
    items: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  })

  await page.goto('/catalog')
  await expect(page).toHaveURL(/\/login\?redirect=/)
  await page.locator('input[autocomplete="username"]').fill('researcher')
  await page.locator('input[autocomplete="current-password"]').fill('safe-password-for-e2e')
  await page.getByRole('button', { name: '进入工作台' }).click()

  await expect(page).toHaveURL(/\/catalog$/)
  await expect(page.getByRole('heading', { name: '成果目录' })).toBeVisible()
  await expect(page.getByText('暂无符合条件的成果')).toBeVisible()
})

test('缺少权限时进入明确的 403 页面', async ({ page }) => {
  await mockJson(page, '**/api/v1/auth/me', researcher)

  await page.goto('/sources')

  await expect(page.getByText('当前账号无权访问')).toBeVisible()
  await expect(page).toHaveURL(/\/forbidden$/)
})

test('业务请求返回 401 时进入会话过期页', async ({ page }) => {
  await mockJson(page, '**/api/v1/auth/me', researcher)
  await mockJson(page, '**/api/v1/catalog/achievements**', { title: 'Unauthorized', status: 401 }, 401)

  await page.goto('/catalog')

  await expect(page.getByText('登录会话已过期')).toBeVisible()
  await expect(page).toHaveURL(/\/session-expired$/)
})
