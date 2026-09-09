import { expect, test, type Page } from '@playwright/test'

async function mockUser(page: Page, admin: boolean) {
  await page.route('**/api/v1/**', (route) => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ items: [], totalElements: 0, page: 0, size: 20 }),
  }))
  await page.route('**/api/v1/auth/me', (route) => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({
      id: 1, username: 'navigation-test', roles: [admin ? 'ADMIN' : 'RESEARCHER'],
      permissions: admin
        ? ['CATALOG_READ', 'GRAPH_READ', 'ANALYTICS_READ', 'SOURCE_READ', 'CRAWL_TASK_READ', 'GOVERNANCE_READ', 'OPERATIONS_READ', 'AUDIT_READ', 'USER_LIST']
        : ['CATALOG_READ', 'GRAPH_READ', 'ANALYTICS_READ'],
    }),
  }))
}

test('管理员横向导航、命令面板与窄屏入口一致', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await mockUser(page, true)
  await page.goto('/catalog')
  const sidebar = page.getByRole('navigation', { name: '模块导航', exact: true })
  await expect(sidebar.getByRole('link')).toHaveCount(10)
  await expect(sidebar.getByRole('link', { name: '账号管理' })).toHaveAttribute('href', '/users')
  await expect(page.locator('.app-shell__sidebar')).toHaveCount(0)
  await page.keyboard.press('Control+k')
  await expect(page.getByRole('textbox', { name: '命令面板搜索' })).toBeFocused()
  await page.getByRole('textbox', { name: '命令面板搜索' }).fill('数据管理')
  await expect(page.getByRole('option')).toHaveCount(4)
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.screenshot({ path: 'test-results/navigation-desktop.png', fullPage: true, animations: 'disabled' })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('button', { name: '打开导航菜单' }).click()
  const mobile = page.getByRole('dialog').getByRole('navigation', { name: '业务导航' })
  await expect(mobile.getByRole('heading')).toHaveText(['研究工作', '数据管理', '管理工具'])
  await page.screenshot({ path: 'test-results/navigation-mobile.png', fullPage: true, animations: 'disabled' })
  expect(errors).toEqual([])
})

test('科研用户仅显示研究工作分组', async ({ page }) => {
  await mockUser(page, false)
  await page.goto('/catalog')
  const sidebar = page.getByRole('navigation', { name: '模块导航', exact: true })
  await expect(sidebar.getByRole('link')).toHaveText(['成果目录', '实体编目', '知识图谱', '统计分析'])
  await expect(sidebar.getByRole('link', { name: '账号管理' })).toHaveCount(0)
})
