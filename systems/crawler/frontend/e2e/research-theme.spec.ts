import { expect, test } from '@playwright/test'
import { fixture } from './fixtures/workbench'

for (const theme of ['light', 'dark'] as const) {
  for (const viewport of [{ width: 1280, height: 720 }, { width: 1366, height: 768 }, { width: 1440, height: 900 }, { width: 1920, height: 1080 }]) {
    test(`历史 ${theme} 偏好统一迁移浅色且顶部模块导航可用 ${viewport.width}×${viewport.height}`, async ({ page }) => {
      await fixture(page)
      await page.setViewportSize(viewport)
      await page.emulateMedia({ colorScheme: theme, reducedMotion: 'reduce' })
      await page.addInitScript(value => localStorage.setItem('aacv-theme', value), theme)
      await page.goto('/dashboard')
      await expect(page.locator('.dashboard-kpis')).toContainText('1,286')
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
      expect(await page.evaluate(() => localStorage.getItem('aacv-theme'))).toBe('light')
      const navigation = page.getByRole('navigation', { name: '模块导航', exact: true })
      await expect(navigation.getByRole('link')).toHaveCount(10)
      await expect(navigation.getByRole('link')).toHaveText(['可视化大屏', '成果目录', '实体编目', '学术关系图谱', '学术成果图谱', '学术背景图谱', '统计分析', '作者导入', '日志管理', '账号管理'])
      await expect(navigation.getByRole('link', { name: '可视化大屏', exact: true })).toHaveAttribute('aria-current', 'page')
      await expect(page.getByRole('navigation', { name: '大屏模块导航', exact: true })).toHaveCount(0)
      for (const link of await navigation.getByRole('link').all()) {
        await link.scrollIntoViewIfNeeded()
        await expect(link).toBeInViewport({ ratio: 1 })
      }
      expect(await page.evaluate(() => document.documentElement.scrollHeight <= innerHeight)).toBe(true)
      expect(await page.locator('body').evaluate(element => getComputedStyle(element).backgroundColor)).toBe('rgb(243, 247, 252)')
      await expect(page.locator('.app-shell__sidebar')).toHaveCount(0)
      await page.locator('.dashboard-scroll').evaluate(element => { element.scrollTop = 0 })
      await expect(page.getByRole('button', { name: '系统通知' })).toHaveCount(0)
      await expect(page.getByText(/MySQL|Neo4j|应用存活|设计系统/)).toHaveCount(0)
      await page.screenshot({ path: `../.local/research-ui/overview-${theme}-${viewport.width}.png`, animations: 'disabled' })
    })
  }
}

test('合并后的编目、统计和账号入口均可切换且模块导航保持正确选中', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await fixture(page)
  await page.goto('/catalog/authors')
  for (const label of ['机构', '期刊', '主题', '作者']) {
    await page.getByRole('navigation', { name: '编目分类' }).getByRole('link', { name: `${label}编目` }).click()
    await expect(page.getByRole('heading', { name: `${label}编目`, exact: true })).toBeVisible()
    await expect(page.locator('.module-navigation a[aria-current="page"]')).toHaveText('实体编目')
  }
  await page.goto('/analytics')
  await page.getByRole('navigation', { name: '统计分类' }).getByRole('link', { name: '字段覆盖' }).click()
  await expect(page.getByRole('heading', { name: '本地字段覆盖率', exact: true })).toBeVisible()
  await expect(page.locator('.module-navigation a[aria-current="page"]')).toHaveText('统计分析')
  await page.goto('/analytics/distributions')
  await page.getByRole('navigation', { name: '统计分类' }).getByRole('link', { name: '机构与主题' }).click()
  await expect(page.getByRole('heading', { name: '机构与主题', exact: true })).toBeVisible()
  await expect(page.locator('.module-navigation a[aria-current="page"]')).toHaveText('统计分析')
  await page.goto('/users')
  await page.getByRole('navigation', { name: '账号管理内容' }).getByRole('link', { name: '账号概况' }).click()
  await expect(page.getByRole('heading', { name: '账号概况', exact: true })).toBeVisible()
  await expect(page.locator('.module-navigation a[aria-current="page"]')).toHaveText('账号管理')
  expect(errors).toEqual([])
})

test('移除技术状态后不再读取健康或同步状态，旧工作台和运维入口分别返回工作台与日志', async ({ page }) => {
  const requests: string[] = []
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('request', request => requests.push(new URL(request.url()).pathname))
  await fixture(page)
  for (const path of ['/', '/graph/explore', '/operations', '/operations/alerts', '/operations/events', '/operations/maintenance', '/operations/audits', '/overview/activity']) {
    await page.goto(path)
    if (path.startsWith('/operations')) {
      await expect(page).toHaveURL(/\/logs$/)
      await expect(page.getByRole('heading', { name: '日志管理', exact: true })).toBeVisible()
    } else if (path === '/' || path === '/overview/activity') {
      await expect(page).toHaveURL(url => url.pathname === '/')
      await expect(page.getByRole('heading', { name: '学者研究工作台', exact: true })).toBeVisible()
    } else await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await expect(page.getByText(/MySQL|Neo4j|应用存活|图同步存在积压|设计系统/)).toHaveCount(0)
  }
  await page.goto('/operations/audits')
  await expect(page).toHaveURL(/\/logs$/)
  await page.getByRole('tab', { name: '登录日志' }).click()
  await expect(page).toHaveURL(/category=LOGIN/)
  await expect(page.getByRole('tab', { name: '登录日志', exact: true })).toHaveAttribute('aria-selected', 'true')
  expect(requests.filter(path => /^\/actuator\/|^\/api\/v1\/(operations\/(overview|alerts)|graph\/sync-status)/.test(path))).toEqual([])
  expect(requests.some(path => path.endsWith('/operations/audits'))).toBe(true)
  expect(errors).toEqual([])
})
