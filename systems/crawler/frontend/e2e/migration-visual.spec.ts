import { expect, test } from '@playwright/test'
import { achievement, fixture } from './fixtures/workbench'


for (const theme of ['light', 'dark'] as const) {
  test(`代表页面视觉 ${theme} 1440`, async ({ page }) => {
    const errors: string[] = []
    page.on('pageerror', (error) => errors.push(error.message))
    page.on('console', (message) => { if (message.type() === 'error') errors.push(message.text()) })
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.emulateMedia({ colorScheme: theme, reducedMotion: 'reduce' })
    await page.addInitScript((value) => localStorage.setItem('aacv-theme', value), theme)
    await fixture(page)
    for (const [path, name, title] of [['/dashboard', 'overview', '科研成果分析中枢'], ['/catalog', 'catalog', '成果目录'], ['/graph?centerType=ACHIEVEMENT&centerId=42', 'graph', '高级查询']]) {
      await page.goto(path!)
      await expect(page.getByRole('heading', { name: title!, exact: true })).toBeVisible()
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
      if (name === 'overview') await expect(page.getByText('1,286', { exact: true }).first()).toBeVisible()
      if (name === 'catalog') await expect(page.getByRole('cell', { name: achievement.title })).toBeVisible()
      if (name === 'graph') await expect(page.getByRole('img', { name: '知识图谱，共2个节点和1条关系' })).toBeVisible()
      await page.screenshot({ path: `../.local/migration-visual/${name}-${theme}-1440.png`, fullPage: true, animations: 'disabled' })
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
      expect(await page.evaluate(() => (window as typeof window & { aacvTestErrors: string[] }).aacvTestErrors)).toEqual([])
    }
    expect(errors).toEqual([])
  })
}

const routeCases = [
  ['/dashboard', 'overview', '科研成果分析中枢'], ['/catalog', 'catalog', '成果目录'],
  ['/catalog/achievements/42', 'detail', achievement.title],
  ['/catalog/authors', 'authors', '作者编目'], ['/catalog/organizations', 'organizations', '机构编目'],
  ['/catalog/venues', 'venues', '期刊编目'], ['/catalog/topics', 'topics', '主题编目'],
  ['/author-import', 'author-import', '作者导入'],
  ['/graph?centerType=ACHIEVEMENT&centerId=42', 'graph', '高级查询'],
  ['/graph/path', 'path', '路径分析'], ['/graph/queries', 'queries', '常用查询'],
  ['/analytics', 'analytics', '统计分析'], ['/users', 'users', '用户管理'],
  ['/logs', 'logs', '日志管理'], ['/operations', 'operations', '日志管理'],
  ['/session-expired', 'expired', '登录会话已过期'], ['/forbidden', 'forbidden', '当前账号无权访问'],
  ['/missing-page', 'not-found', '页面不存在'],
] as const

for (const width of [1440, 1920, 390]) {
  for (const theme of ['light', 'dark'] as const) {
    test(`全部路由 ${theme} ${width}`, async ({ page }) => {
      test.setTimeout(120_000)
      const errors: string[] = []
      page.on('pageerror', error => errors.push(error.message))
      page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
      await page.setViewportSize({ width, height: width === 1920 ? 1080 : width === 390 ? 844 : 900 })
      await page.emulateMedia({ colorScheme: theme, reducedMotion: 'reduce' })
      await page.addInitScript(value => localStorage.setItem('aacv-theme', value), theme)
      const requests = await fixture(page)
      for (const [path, name, title] of routeCases) {
        const before = requests.length
        await page.goto(path)
        await expect(page.getByRole('heading', { name: title, exact: true, level: 1 })).toBeVisible()
        await expect(page.locator('.el-skeleton')).toHaveCount(0)
        if (name === 'graph') await expect(page.getByRole('img', { name: '知识图谱，共2个节点和1条关系' })).toBeVisible()
        if (name === 'detail') await expect(page.getByText(achievement.title, { exact: true }).first()).toBeVisible()
        await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
        await page.screenshot({ path: `../.local/migration-visual/all/${name}-${theme}-${width}.png`, fullPage: true, animations: 'disabled' })
        const overflow = await page.evaluate(() => ({ document: document.documentElement.scrollWidth, viewport: innerWidth }))
        expect(overflow.document, `${path} 横向溢出`).toBeLessThanOrEqual(overflow.viewport + 1)
        expect(errors, `${path} 控制台`).toEqual([])
        expect(await page.evaluate(() => (window as typeof window & { aacvTestErrors: string[] }).aacvTestErrors), `${path} 运行时错误`).toEqual([])
        const requested = requests.slice(before)
        expect(requested.length, `${path} 重复初始请求`).toBe(new Set(requested).size)
      }
    })
    test(`登录页面 ${theme} ${width}`, async ({ page }) => {
      await page.setViewportSize({ width, height: width === 1920 ? 1080 : 900 })
      await page.emulateMedia({ colorScheme: theme, reducedMotion: 'reduce' })
      await page.addInitScript(value => localStorage.setItem('aacv-theme', value), theme)
      await fixture(page, false)
      await page.goto('/login')
      await expect(page.getByRole('button', { name: '进入工作台', exact: true })).toBeVisible()
      await page.screenshot({ path: `../.local/migration-visual/all/login-${theme}-${width}.png`, fullPage: true, animations: 'disabled' })
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
    })
  }
}

test('固定主题与图谱窄屏抽屉的键盘焦点、层级和滚动', async ({ page }) => {
  await fixture(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.emulateMedia({ colorScheme: 'light', reducedMotion: 'reduce' })
  await page.goto('/graph?centerType=ACHIEVEMENT&centerId=42')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.emulateMedia({ colorScheme: 'dark' })
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  const trigger = page.getByRole('button', { name: '查看图谱详情' })
  await trigger.click()
  const drawer = page.getByRole('dialog', { name: '图谱详情' })
  await expect(drawer).toBeVisible()
  await expect(drawer.getByRole('heading', { name: achievement.title })).toBeVisible()
  expect(await drawer.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
  await page.keyboard.press('Tab')
  expect(await drawer.evaluate(el => el.contains(document.activeElement))).toBe(true)
  await page.screenshot({ path: '../.local/migration-visual/graph-drawer-dark-390.png', animations: 'disabled' })
  await page.keyboard.press('Escape')
  await expect(drawer).not.toBeVisible()
  await expect(trigger).toBeFocused()
  await expect(page.getByRole('button', { name: '切换主题' })).toHaveCount(0)
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.reload()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
})

test('统计和运维单区域失败保留其他数据并明确错误', async ({ page }) => {
  await fixture(page)
  await page.route('**/api/v1/analytics/trends*', route => route.fulfill({ status: 503, contentType: 'application/problem+json', json: { detail: '趋势服务暂不可用', traceId: 'synthetic-partial' } }))
  await page.goto('/analytics')
  await expect(page.getByRole('alert')).toContainText('年度趋势：趋势服务暂不可用')
  await expect(page.getByRole('img', { name: '年度成果趋势折线图' })).toHaveCount(0)
  await expect(page.getByText('1,286', { exact: true }).first()).toBeVisible()
  await page.getByRole('navigation', { name: '模块页面' }).locator('a[href="/analytics/distributions"]').click()
  await expect(page.getByRole('img', { name: '成果类型分布条形图' })).toBeVisible()
  await expect(page.getByRole('alert')).toContainText('年度趋势：趋势服务暂不可用')
  await page.route('**/api/v1/operations/overview', route => route.fulfill({ status: 503, contentType: 'application/problem+json', json: { detail: '运维总览暂不可用' } }))
  await page.goto('/operations')
  await expect(page).toHaveURL(/\/logs$/)
  await expect(page.getByRole('heading', { name: '日志管理', exact: true })).toBeVisible()
  await expect(page.getByText('部分区域暂不可用：运维总览')).toHaveCount(0)
  await expect(page.getByText('应用存活 · 正常', { exact: true })).toHaveCount(0)
})
