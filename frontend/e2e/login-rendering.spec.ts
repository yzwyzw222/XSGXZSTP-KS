import { expect, test } from '@playwright/test'

test('登录页首次打开及刷新后均可操作，脚本和样式加载正常', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('requestfailed', (request) => {
    if (['script', 'stylesheet'].includes(request.resourceType())) {
      errors.push(`${request.method()} ${request.url()}: ${request.failure()?.errorText}`)
    }
  })
  page.on('response', (response) => {
    if (response.status() >= 400 && ['script', 'stylesheet'].includes(response.request().resourceType())) {
      errors.push(`${response.status()} ${response.url()}`)
    }
  })
  await page.route('**/api/v1/auth/me', (route) => route.fulfill({
    status: 401,
    contentType: 'application/json',
    body: JSON.stringify({ title: '未登录', status: 401 }),
  }))

  for (const viewport of [{ width: 1440, height: 900 }, { width: 390, height: 844 }]) {
    await page.setViewportSize(viewport)
    await page.goto('/login')
    for (let visit = 0; visit < 2; visit += 1) {
      if (visit > 0) await page.reload()
      await expect(page.getByRole('heading', { name: '登录 AACV System' })).toBeVisible()
      await expect(page.getByRole('textbox', { name: '用户名' })).toBeEditable()
      await expect(page.getByLabel('密码', { exact: true })).toBeEditable()
      await page.getByRole('button', { name: '进入工作台' }).click()
      await expect(page.getByText('请输入用户名和密码', { exact: true })).toBeVisible()
      expect(errors).toEqual([])
    }
  }
})
