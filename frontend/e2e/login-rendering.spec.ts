import { expect, test } from '@playwright/test'

for (const theme of ['light', 'dark'] as const) {
  for (const viewport of [
    { width: 1440, height: 900 },
    { width: 1920, height: 1080 },
    { width: 2556, height: 1272 },
    { width: 390, height: 844 },
    { width: 320, height: 740 },
  ]) {
    test(`登录布局与单层输入框 ${theme} ${viewport.width}`, async ({ page }, testInfo) => {
      const errors: string[] = []
      page.on('pageerror', error => errors.push(error.message))
      await page.setViewportSize(viewport)
      await page.emulateMedia({ colorScheme: theme, reducedMotion: 'reduce' })
      await page.addInitScript(value => localStorage.setItem('aacv-theme', value), theme)
      await page.route('**/api/v1/auth/me', route => route.fulfill({
        status: 401,
        json: { title: '未登录', status: 401 },
      }))
      await page.goto('/login')
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme)
      await expect(page.getByRole('heading', { name: '登录 AACV System' })).toBeVisible()
      const username = page.getByRole('textbox', { name: '用户名' })
      const password = page.getByLabel('密码', { exact: true })
      const submit = page.getByRole('button', { name: '进入工作台' })
      const desktop = viewport.width >= 1024
      const panel = await page.locator('.login-panel').boundingBox()
      expect(panel).not.toBeNull()
      expect(panel!.width).toBeGreaterThanOrEqual(desktop ? 1200 : viewport.width - 34)
      if (desktop) expect(panel!.height).toBeGreaterThanOrEqual(640)
      await expect(page.locator('.login-page__constellation')).toBeVisible({ visible: desktop })
      await expect(page.locator('.login-panel__intro')).toBeVisible({ visible: desktop })
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)

      for (const input of [username, password]) {
        await expect(input).toBeEditable()
        await expect(input).toBeInViewport()
        await input.hover()
        await expect(input).toHaveCSS('border-top-width', '0px')
        await expect(input).toHaveCSS('outline-style', 'none')
        await expect(input).toHaveCSS('box-shadow', 'none')
      }
      await page.keyboard.press('Tab')
      await expect(username).toBeFocused()
      await page.keyboard.press('Tab')
      await expect(password).toBeFocused()

      for (const input of [username, password]) {
        await input.focus()
        await expect(input).toHaveCSS('outline-style', 'none')
        const wrapper = input.locator('..')
        await expect(wrapper).toHaveClass(/is-focus/)
        await expect(wrapper).toHaveCSS('outline-style', 'none')
        await expect(wrapper).toHaveCSS('box-shadow', /0px 0px 0px 2px inset$/)
        expect(await wrapper.evaluate(element => element.getBoundingClientRect().height)).toBeGreaterThanOrEqual(52)
      }

      await username.fill('layout-check')
      await page.locator('.login-form__input .el-input__clear').click()
      await expect(username).toHaveValue('')
      await password.fill('visual-check-only')
      await page.locator('.login-form__input .el-input__password').click()
      await expect(password).toHaveAttribute('type', 'text')
      await page.locator('.login-form__input .el-input__password').click()
      await expect(password).toHaveAttribute('type', 'password')
      await password.fill('')
      await username.focus()
      await expect(submit).toBeInViewport()
      await page.screenshot({ path: testInfo.outputPath('login-focused.png'), fullPage: true, animations: 'disabled' })
      await submit.click()
      await expect(page.getByText('请输入用户名和密码', { exact: true })).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
      expect(errors).toEqual([])
    })
  }
}

for (const [reason, message] of [
  ['expired', '会话已过期，请重新登录'],
  ['unavailable', '会话检查失败，请确认服务可用后重试'],
] as const) {
  test(`窄屏登录提示保留且表单可操作 ${reason}`, async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.route('**/api/v1/auth/me', route => route.fulfill({
      status: 401,
      json: { title: '未登录', status: 401 },
    }))
    await page.goto(`/login?reason=${reason}`)
    await expect(page.getByText(message, { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '进入工作台' }).click()
    await expect(page.getByText('请输入用户名和密码', { exact: true })).toBeVisible()
    await expect(page.getByRole('textbox', { name: '用户名' })).toBeEditable()
    await expect(page.getByLabel('密码', { exact: true })).toBeEditable()
    await expect(page.getByRole('button', { name: '进入工作台' })).toBeInViewport()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  })
}

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
