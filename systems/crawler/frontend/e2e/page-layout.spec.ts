import { expect, test, type Page } from '@playwright/test'
import { achievement, fixture } from './fixtures/workbench'

const routes = [
  ['/dashboard', '科研成果分析中枢'], ['/overview/research', '合作排行'], ['/overview/activity', '学者研究工作台'],
  ['/catalog', '成果目录'], ['/catalog/achievements/42', achievement.title], ['/catalog/authors', '作者编目'],
  ['/author-import', '作者导入'],
  ['/analytics', '统计分析'], ['/analytics/coverage', '字段覆盖率'], ['/analytics/distributions', '成果分布'],
  ['/analytics/research', '机构与主题'], ['/analytics/collaboration', '合作排行'],
  ['/operations', '日志管理'], ['/operations/alerts', '日志管理'], ['/operations/events', '日志管理'],
  ['/operations/maintenance', '日志管理'], ['/operations/audits', '日志管理'],
  ['/users', '用户管理'], ['/users/overview', '账号概况'], ['/logs', '日志管理'],
  ['/graph/explore', '高级查询'], ['/graph/path', '路径分析'], ['/graph/queries', '常用查询'],
] as const

const viewports = [{ width: 1440, height: 900 }, { width: 1920, height: 1080 }, { width: 390, height: 844 }, { width: 1280, height: 720 }]

/** 同时核对内容真实尺寸与面板边界，防止仅隐藏外层滚动条后把结果裁掉。 */
async function assertWorkspaceFits(page: Page, path: string): Promise<void> {
  const main = page.locator('#main-content')
  await expect.poll(() => page.evaluate(() => {
    const root = document.documentElement
    const main = document.querySelector<HTMLElement>('#main-content')!
    return {
      documentVertical: root.scrollHeight <= innerHeight + 1,
      documentHorizontal: root.scrollWidth <= innerWidth + 1,
      mainVertical: main.scrollHeight <= main.clientHeight + 1,
      mainHorizontal: main.scrollWidth <= main.clientWidth + 1,
    }
  }), { message: `${path} 页面或主工作区不应产生整页滚动` }).toEqual({
    documentVertical: true, documentHorizontal: true, mainVertical: true, mainHorizontal: true,
  })

  if (path === '/dashboard') {
    for (const panel of await main.locator('.dashboard-panel').all()) {
      await panel.scrollIntoViewIfNeeded()
      await expect(panel).toBeInViewport({ ratio: 1 })
      expect((await panel.boundingBox())!.height).toBeGreaterThan(100)
    }
    return
  }

  if (path === '/author-import') {
    // 导入表单按内容纵向滚动，校验各项操作可达，避免固定面板压缩并遮挡预览。
    await expect(main.getByLabel('学者名称检索')).toHaveCount(0)
    await main.getByLabel('选择信息表').setInputFiles({ name: '布局测试.csv', mimeType: 'text/csv', buffer: Buffer.from('SrcDatabase,Title,Author\n期刊,测试论文,张三') })
    for (const label of ['选择信息表', '工作表序号', '表头所在行']) {
      const control = main.getByLabel(label)
      await control.scrollIntoViewIfNeeded()
      await expect(control).toBeInViewport({ ratio: 1 })
    }
    const parse = main.getByRole('button', { name: '解析并预览' })
    await parse.scrollIntoViewIfNeeded()
    await expect(parse).toBeInViewport({ ratio: 1 })
    await expect(parse).toBeEnabled()
    return
  }

  const regions = await main.locator('.panel-section, .operations-overview').evaluateAll(elements => elements
    .filter(element => element.checkVisibility() && !element.parentElement?.closest('.panel-section'))
    .map(element => {
      const bounds = element.getBoundingClientRect()
      const mainBounds = document.querySelector('#main-content')!.getBoundingClientRect()
      return {
        name: element.querySelector('h2')?.textContent?.trim() || element.getAttribute('class'),
        width: bounds.width, height: bounds.height,
        inside: bounds.top >= mainBounds.top - 1 && bounds.bottom <= mainBounds.bottom + 1
          && bounds.left >= mainBounds.left - 1 && bounds.right <= mainBounds.right + 1,
      }
    }))
  expect(regions.length, `${path} 应显示主要内容面板`).toBeGreaterThan(0)
  for (const region of regions) {
    expect(region.inside, `${path} 面板“${region.name}”应完整位于主区内`).toBe(true)
    expect(region.width, `${path} 面板“${region.name}”宽度`).toBeGreaterThan(60)
    expect(region.height, `${path} 面板“${region.name}”高度`).toBeGreaterThan(40)
  }

  const tableBodies = main.locator('.data-table:visible .data-table__content')
  for (const chart of await main.locator('.analytics-chart-frame:visible').all()) {
    expect((await chart.boundingBox())!.height, `${path} 图表应保留可阅读高度`).toBeGreaterThan(150)
  }
  for (const body of await tableBodies.all()) {
    const bounds = await body.boundingBox()
    expect(bounds!.height, `${path} 表体必须保留可见空间`).toBeGreaterThan(60)
  }
  const pagination = main.locator('.data-table__pagination:visible')
  for (const bar of await pagination.all()) {
    await expect(bar, `${path} 分页不应被裁切`).toBeInViewport({ ratio: 1 })
    await expect(bar.locator('.btn-next'), `${path} 下一页入口应可达`).toBeInViewport({ ratio: 1 })
  }
}

async function openReadyPage(page: Page, path: string, title: string): Promise<void> {
  await page.goto(path)
  const main = page.locator('#main-content')
  await expect(main.getByRole('heading', { name: title, exact: true, level: 1 })).toBeVisible()
  await expect(main.locator('.el-skeleton')).toHaveCount(0)
  await expect(main.locator('[aria-busy="true"]')).toHaveCount(0)
  await expect(main.locator('.el-alert--error')).toHaveCount(0)
}

for (const viewport of viewports) {
  test(`各模块与子页固定在视口内 ${viewport.width}×${viewport.height}`, async ({ page }) => {
    test.setTimeout(150_000)
    const errors: string[] = []
    page.on('pageerror', error => errors.push(error.message))
    await fixture(page)
    await page.setViewportSize(viewport)
    await page.emulateMedia({ colorScheme: 'light', reducedMotion: 'reduce' })
    await page.addInitScript(() => localStorage.setItem('aacv-theme', 'light'))
    for (const [path, title] of routes) {
      await test.step(path, async () => {
        await openReadyPage(page, path, title)
        await assertWorkspaceFits(page, path)
        if (path === '/analytics' && viewport.width !== 1920) {
          await page.screenshot({ path: `../.local/page-layout/analytics-${viewport.width}.png`, fullPage: true, animations: 'disabled' })
        }
        if (viewport.width < 768) {
          const switches = page.getByRole('group', { name: /^(研究内容|活动内容|分布内容|合作内容)$/ })
          for (const button of await switches.getByRole('button').all()) {
            await expect(button).toBeInViewport({ ratio: 1 })
            await button.click()
            await expect(button).toHaveAttribute('aria-pressed', 'true')
            await assertWorkspaceFits(page, `${path} ${await button.innerText()}`)
          }
        }
        if (path === '/catalog/achievements/42') {
          for (const name of ['署名与引用', '来源指标', '来源与字段']) {
            const tab = page.getByRole('tab', { name, exact: true })
            await tab.click()
            await expect(tab).toHaveAttribute('aria-selected', 'true')
            await assertWorkspaceFits(page, `${path} ${name}`)
          }
        }
      })
    }
    expect(errors).toEqual([])
  })
}

test('深色窄屏的统计、账号与图谱查询仍保持可用空间', async ({ page }) => {
  await fixture(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' })
  await page.addInitScript(() => localStorage.setItem('aacv-theme', 'dark'))
  for (const [path, title] of routes.filter(([path]) => ['/analytics/coverage', '/users', '/graph/explore'].includes(path))) {
    await openReadyPage(page, path, title)
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
    await assertWorkspaceFits(page, path)
  }
  await page.screenshot({ path: '../.local/page-layout/query-390-dark.png', fullPage: true, animations: 'disabled' })
})

for (const viewport of [viewports[0]!, viewports[2]!]) {
  test(`长成果目录仅表体滚动且分页可用 ${viewport.width}`, async ({ page }) => {
    await fixture(page)
    await page.setViewportSize(viewport)
    await page.emulateMedia({ reducedMotion: 'reduce' })
    const requestedPages: number[] = []
    const items = Array.from({ length: 40 }, (_, index) => ({
      ...achievement, id: index + 1,
      title: `第 ${index + 1} 项：面向跨机构开放学术成果的规范化处理、可信证据溯源与复杂知识图谱合作关系分析方法研究`,
    }))
    await page.route('**/api/v1/catalog/achievements?**', async route => {
      const url = new URL(route.request().url())
      const currentPage = Number(url.searchParams.get('page') ?? 0)
      const size = Number(url.searchParams.get('size') ?? 20)
      requestedPages.push(currentPage)
      await route.fulfill({ json: {
        items: items.slice(currentPage * size, (currentPage + 1) * size), page: currentPage, size,
        totalElements: items.length, totalPages: Math.ceil(items.length / size),
      } })
    })
    await openReadyPage(page, '/catalog', '成果目录')
    await expect(page.getByRole('link', { name: items[0]!.title, exact: true })).toBeVisible()
    await assertWorkspaceFits(page, '/catalog')
    if (viewport.width === 390) {
      await page.screenshot({ path: '../.local/page-layout/catalog-390.png', fullPage: true, animations: 'disabled' })
    }
    const body = page.locator('.data-table .el-table__body-wrapper .el-scrollbar__wrap')
    await expect.poll(() => body.evaluate(element => element.scrollHeight - element.clientHeight)).toBeGreaterThan(100)
    await body.evaluate(element => { element.scrollTop = element.scrollHeight })
    await expect(page.getByRole('link', { name: items[19]!.title, exact: true })).toBeInViewport()
    await expect.poll(() => page.locator('#main-content').evaluate(element => element.scrollTop)).toBe(0)
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0)
    const next = page.locator('.data-table__pagination .btn-next')
    await expect(next).toBeInViewport({ ratio: 1 })
    await next.click()
    await expect.poll(() => requestedPages.at(-1)).toBe(1)
    await expect(page.getByRole('link', { name: items[20]!.title, exact: true })).toBeVisible()
    await expect(page.locator('.data-table__pagination')).toContainText('显示 21–40，共 40 条')
    await assertWorkspaceFits(page, '/catalog?page=1')
  })
}
