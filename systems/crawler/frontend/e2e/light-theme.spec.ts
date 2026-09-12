import { expect, test } from '@playwright/test'
import { fixture, time, achievement } from './fixtures/workbench'

test('浅色页面、真实图形绘制与参考视口截图', async ({ page }, testInfo) => {
  await fixture(page)
  await page.setViewportSize({ width: 1672, height: 941 })
  await page.emulateMedia({ reducedMotion: 'reduce', colorScheme: 'dark' })
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
  const pageOf = (items: unknown[]) => ({ items, page: 0, size: 20, totalElements: items.length, totalPages: 1 })
  await page.route('**/api/v1/catalog/achievements?*', route => route.fulfill({ json: pageOf(Array.from({ length: 14 }, (_, i) => ({ ...achievement, id: 42 + i, title: `${achievement.title} ${i + 1}`, authors: ['林研究员', '张研究员'], primaryVenue: '计算机学报', topics: ['知识图谱', '可信计算'] }))) }))
  await page.route('**/api/v1/author-import', route => route.fulfill({ json: Array.from({ length: 6 }, (_, i) => ({ id: i + 1, authorId: 7, scholarName: '林研究员', fileName: `学者成果资料-${i + 1}.xlsx`, importMode: 'AUTHOR', importedCount: i + 2, skippedCount: 0, createdAt: time })) }))
  await page.route('**/api/v1/operations/audits?*', route => route.fulfill({ json: pageOf(Array.from({ length: 14 }, (_, i) => ({ id: i + 1, username: 'research-demo', action: 'AUTHOR_IMPORT', result: 'SUCCESS', targetType: 'AUTHOR', targetId: '7', createdAt: time, ipAddress: '127.0.0.1', userAgent: 'Mozilla/5.0 Edg/140.0', details: {} }))) }))
  await page.route('**/api/v1/graph/authors/7?*', route => route.fulfill({ json: {
    graph: { nodes: [{ id: 'AUTHOR:7', businessId: '7', type: 'AUTHOR', label: '林研究员', properties: {} }, ...Array.from({ length: 7 }, (_, i) => ({ id: `ACHIEVEMENT:${42 + i}`, businessId: `${42 + i}`, type: 'ACHIEVEMENT', label: `学术数据与知识关联研究 ${i + 1}`, properties: { achievementType: 'article', publicationDate: `${2020 + i}-06-01` } }))],
      edges: Array.from({ length: 7 }, (_, i) => ({ id: `edge-${i}`, source: 'AUTHOR:7', target: `ACHIEVEMENT:${42 + i}`, type: 'AUTHORED', properties: {} })), rootNodeId: 'AUTHOR:7', truncated: false, appliedLimits: { depth: 2, nodeLimit: 300, maxHops: 0 }, syncedAt: null, projectionLagSeconds: null }, page: 0, size: 20, totalWorks: 7,
  } }))
  const routes = [['workbench', '/'], ['dashboard', '/dashboard'], ['catalog', '/catalog'], ['entities', '/catalog/authors'], ['relations', '/academic-relations?authorId=7'], ['achievements', '/academic-achievements?authorId=7'], ['background', '/academic-background?authorId=7'], ['analytics', '/analytics'], ['import', '/author-import'], ['logs', '/logs'], ['users', '/users']] as const
  for (const [name, path] of routes) {
    await page.goto(path)
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
    await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible()
    await expect(page.locator('.research-topbar__campus')).toHaveJSProperty('complete', true)
    expect(await page.locator('.research-topbar__campus').evaluate(image => (image as HTMLImageElement).naturalWidth)).toBeGreaterThan(0)
    await expect(page.locator('[aria-busy="true"]')).toHaveCount(0)
    if (['relations', 'achievements'].includes(name)) await expect(page.locator('.graph-canvas canvas[data-id="layer2-node"]')).toBeVisible()
    if (name === 'background') await expect(page.getByRole('region', { name: '学术成果时间线' })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath(`${name}.png`), animations: 'disabled' })
    if (name === 'users') {
      await page.getByRole('button', { name: '编辑', exact: true }).first().click()
      await expect(page.getByRole('dialog')).toBeVisible()
      await page.screenshot({ path: testInfo.outputPath('user-dialog.png'), animations: 'disabled' })
      await page.keyboard.press('Escape')
    }
  }
  expect(errors).toEqual([])
})

for (const width of [320, 390, 768]) {
  test(`窄屏检索、导入及导航 ${width}`, async ({ page }, testInfo) => {
    await fixture(page)
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/')
    await page.getByRole('textbox', { name: '查找研究成果' }).fill('学术数据')
    await page.getByRole('button', { name: '检索成果' }).click()
    await expect(page).toHaveURL(/title=/)
    await page.getByRole('button', { name: '打开导航菜单' }).click()
    await page.getByRole('dialog').getByRole('link', { name: /作者导入/ }).click()
    await expect(page.getByRole('heading', { level: 1, name: '作者导入' })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    const fileInput = page.getByRole('button', { name: '选择信息表' })
    await expect(fileInput).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath(`import-${width}.png`), animations: 'disabled' })
  })
}

test('登录及弹出控件使用浅色，文字和主操作可读', async ({ page }, testInfo) => {
  await fixture(page, false)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.emulateMedia({ colorScheme: 'dark' })
  await page.goto('/login')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await expect(page.getByRole('textbox', { name: '用户名' })).toBeVisible()
  const contrast = await page.locator('.login-panel__form-section .el-button--primary').evaluate(button => {
    const luminance = (value: string) => {
      const channels = value.match(/[\d.]+/g)!.slice(0, 3).map(value => {
        const component = Number(value) / 255
        return component <= .04045 ? component / 12.92 : ((component + .055) / 1.055) ** 2.4
      })
      return channels[0]! * .2126 + channels[1]! * .7152 + channels[2]! * .0722
    }
    const style = getComputedStyle(button)
    const foreground = luminance(style.color), background = luminance(style.backgroundColor)
    return (Math.max(foreground, background) + .05) / (Math.min(foreground, background) + .05)
  })
  expect(contrast).toBeGreaterThanOrEqual(4.5)
  await page.screenshot({ path: testInfo.outputPath('login.png'), animations: 'disabled' })
})
