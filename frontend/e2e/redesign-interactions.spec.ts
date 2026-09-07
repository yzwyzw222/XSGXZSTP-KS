import { expect, test } from '@playwright/test'
import type { Core } from 'cytoscape'
import { achievement, fixture, time } from './fixtures/workbench'

for (const { width, height, theme } of [
  { width: 1440, height: 900, theme: 'light' },
  { width: 2550, height: 1275, theme: 'light' },
  { width: 1440, height: 900, theme: 'dark' },
  { width: 390, height: 844, theme: 'light' },
] as const) {
  test(`工作台主区不受右栏长列表撑高 ${theme} ${width}`, async ({ page }, testInfo) => {
    const errors: string[] = []
    page.on('pageerror', error => errors.push(error.message))
    await page.setViewportSize({ width, height })
    await page.emulateMedia({ colorScheme: theme, reducedMotion: 'reduce' })
    await page.addInitScript(value => localStorage.setItem('aacv-theme', value), theme)
    await fixture(page)
    await page.route('**/api/v1/analytics/collaboration*', route => route.fulfill({ json: {
      authors: Array.from({ length: 6 }, (_, index) => ({
        leftId: 1, leftLabel: '测试学者 1', rightId: index + 2,
        rightLabel: `测试学者 ${index + 2}`, sharedAchievementCount: 7 - index,
      })),
      organizations: [], scope: { source: 'MYSQL', filters: {} }, updatedAt: time,
    } }))
    await page.route('**/api/v1/crawl/tasks*', route => route.fulfill({ json: {
      items: Array.from({ length: 6 }, (_, index) => ({ id: index + 1, name: `布局验证采集任务 ${index + 1}`, sourceId: 1, enabled: true })),
      page: 0, size: 6, totalElements: 6, totalPages: 1,
    } }))
    await page.route('**/api/v1/operations/audits*', route => route.fulfill({ json: {
      items: [{ id: 1, action: 'CRAWL_TASK_CREATED', targetType: 'CRAWL_TASK', targetId: '1', result: 'SUCCESS', createdAt: time }],
      page: 0, size: 8, totalElements: 1, totalPages: 1,
    } }))
    await page.goto('/')
    await expect(page.getByText('按当前范围汇总', { exact: true })).toBeVisible()
    await expect(page.locator('.overview-ranking li')).toHaveCount(7)
    await expect(page.getByText('布局验证采集任务 6', { exact: true })).toBeVisible()

    const checkLayout = async () => {
      const names = ['trend', 'network', 'topics', 'activity', 'ranking', 'crawl'] as const
      const [trend, network, topics, activity, ranking, crawl] = await Promise.all(names.map(async name => {
        const panel = page.locator(`.overview-${name}`)
        await expect(panel).toBeVisible()
        return panel.evaluate(element => {
          const rect = element.getBoundingClientRect()
          return { top: rect.top, bottom: rect.bottom, left: rect.left, right: rect.right }
        })
      }))
      expect(network!.top - trend!.bottom).toBeCloseTo(16, 0)
      if (width >= 1280) {
        expect(topics!.top).toBeCloseTo(network!.top, 0)
        expect(activity!.top - Math.max(network!.bottom, topics!.bottom)).toBeCloseTo(16, 0)
        expect(ranking!.top).toBeCloseTo(trend!.top, 0)
        expect(ranking!.left).toBeGreaterThan(trend!.right)
        expect(crawl!.top - ranking!.bottom).toBeCloseTo(16, 0)
      } else {
        expect(topics!.top - network!.bottom).toBeCloseTo(16, 0)
        expect(activity!.top - topics!.bottom).toBeCloseTo(16, 0)
        expect(ranking!.top - activity!.bottom).toBeCloseTo(16, 0)
      }
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    }

    await checkLayout()
    await page.screenshot({ path: testInfo.outputPath('overview-compact.png'), fullPage: true, animations: 'disabled' })
    await page.getByText('查看趋势数据', { exact: true }).click()
    await expect(page.getByText('2026 年：374 项成果', { exact: true })).toBeVisible()
    await checkLayout()
    expect(errors).toEqual([])
  })
}

type GraphElement = HTMLElement & { _cyreg: { cy: Core } }

test('图谱展开保留位置，键盘选择与画布同步，减少动画和卸载释放实例', async ({ page }) => {
  await fixture(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.emulateMedia({ reducedMotion: 'no-preference', colorScheme: 'light' })
  const response = page.waitForResponse(url => url.url().includes('/graph/subgraph'))
  await page.goto('/graph?centerType=ACHIEVEMENT&centerId=42')
  const graph = await (await response).json()
  const canvas = page.locator('.graph-canvas')
  await expect(canvas).toBeVisible()
  const initial = await canvas.evaluate(el => {
    const cy = (el as GraphElement)._cyreg.cy
    cy.getElementById('ACHIEVEMENT:42').position({ x: 120, y: 160 })
    return { positions: cy.nodes().map(node => ({ id: node.id(), position: { ...node.position() } })), zoom: cy.zoom() }
  })
  expect(initial.zoom).toBeLessThanOrEqual(1.2)
  await page.getByRole('button', { name: '节点表', exact: true }).click()
  const select = page.getByRole('row').filter({ hasText: '林研究员' }).getByRole('button', { name: '查看', exact: true })
  await select.focus()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('heading', { name: '林研究员', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '图形', exact: true }).click()
  await expect.poll(() => canvas.evaluate(el => (el as GraphElement)._cyreg.cy.getElementById('AUTHOR:7').selected())).toBe(true)
  await page.route('**/api/v1/graph/subgraph*', route => route.fulfill({ json: {
    ...graph, rootNodeId: 'AUTHOR:7',
    nodes: [...graph.nodes, { id: 'TOPIC:5', businessId: '5', type: 'TOPIC', label: '可信计算与软件工程', properties: {} }],
    edges: [...graph.edges, { id: 'HAS_TOPIC:42:5', type: 'HAS_TOPIC', source: 'ACHIEVEMENT:42', target: 'TOPIC:5', properties: {} }],
  } }))
  await page.getByRole('button', { name: '展开一跳', exact: true }).click()
  await expect(canvas).toHaveAttribute('aria-label', '知识图谱，共3个节点和2条关系')
  const positions = await canvas.evaluate(el => (el as GraphElement)._cyreg.cy.nodes().filter(node => node.id() !== 'TOPIC:5').nodes().map(node => ({ id: node.id(), position: { ...node.position() } })))
  expect(positions).toEqual(initial.positions)
  await page.getByRole('button', { name: '聚焦所选' }).click()
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await expect.poll(() => canvas.evaluate(el => {
    const cy = (el as GraphElement)._cyreg.cy
    return cy.animated() || cy.elements().some(element => element.animated())
  })).toBe(false)
  await expect.poll(() => canvas.evaluate(el => (el as GraphElement)._cyreg.cy.nodes().first().style('transition-duration'))).toBe('0ms')
  await page.getByRole('button', { name: '取消选择' }).click()
  await expect(page.getByRole('heading', { name: '选择图中元素' })).toBeVisible()
  await page.screenshot({ path: '../.local/frontend-redesign/states/graph-expanded.png', fullPage: true, animations: 'disabled' })
  await canvas.evaluate(el => { (window as typeof window & { inspectedGraph: Core }).inspectedGraph = (el as GraphElement)._cyreg.cy })
  await page.getByRole('link', { name: '成果目录', exact: true }).first().click()
  await expect(page.getByRole('heading', { name: '成果目录', exact: true })).toBeVisible()
  expect(await page.evaluate(() => (window as typeof window & { inspectedGraph: Core }).inspectedGraph.destroyed())).toBe(true)
})

test('连续折叠与键盘导航不中断操作，减少动画清理平移动画', async ({ page }) => {
  await fixture(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await page.goto('/catalog')
  await expect(page.getByRole('cell', { name: achievement.title })).toBeVisible()
  for (let index = 0; index < 4; index++) await page.keyboard.press('Control+b')
  await expect(page.getByRole('button', { name: '折叠侧栏', exact: true })).toBeVisible()
  await page.keyboard.press('Control+b')
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await expect.poll(() => page.locator('#main-content').evaluate(el => el.getAnimations().filter(animation => animation.playState === 'running').length)).toBe(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
  await page.keyboard.press('Control+k')
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await page.keyboard.press('Tab')
  expect(await dialog.evaluate(el => el.contains(document.activeElement))).toBe(true)
  await page.keyboard.press('Escape')
  await expect(dialog).not.toBeVisible()
})

test('深浅主题的主操作与说明文字满足对比度，窄屏按钮达到触摸尺寸', async ({ page }) => {
  await fixture(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/catalog')
  for (const theme of ['light', 'dark'] as const) {
    await page.emulateMedia({ colorScheme: theme })
    await expect(page.locator('html')).toHaveAttribute('data-theme', theme)
    const values = await page.getByRole('button', { name: '查询成果' }).evaluate(el => {
      const luminance = (color: string) => {
        const rgb = color.match(/[\d.]+/g)!.slice(0, 3).map(Number).map(value => {
          const channel = value / 255
          return channel <= .04045 ? channel / 12.92 : ((channel + .055) / 1.055) ** 2.4
        })
        return rgb[0]! * .2126 + rgb[1]! * .7152 + rgb[2]! * .0722
      }
      const ratio = (a: string, b: string) => {
        const light = luminance(a), dark = luminance(b)
        return (Math.max(light, dark) + .05) / (Math.min(light, dark) + .05)
      }
      const button = getComputedStyle(el)
      const label = getComputedStyle(document.querySelector('.filter-field__label')!)
      const surface = getComputedStyle(document.querySelector('.filter-bar')!)
      return { button: ratio(button.color, button.backgroundColor), label: ratio(label.color, surface.backgroundColor), height: el.getBoundingClientRect().height }
    })
    expect(values.button).toBeGreaterThanOrEqual(4.5)
    expect(values.label).toBeGreaterThanOrEqual(4.5)
    expect(values.height).toBeGreaterThanOrEqual(40)
  }
})

test('目录加载、空结果和错误状态可辨识，迟到响应不覆盖最新筛选', async ({ page }) => {
  await fixture(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.emulateMedia({ reducedMotion: 'reduce', colorScheme: 'dark' })
  let release!: () => void
  const pending = new Promise<void>(resolve => { release = resolve })
  await page.route('**/api/v1/catalog/achievements?*', async route => {
    await pending
    await route.fulfill({ json: { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } })
  })
  await page.goto('/catalog')
  await expect(page.locator('.data-table')).toHaveAttribute('aria-busy', 'true')
  await page.screenshot({ path: '../.local/frontend-redesign/states/catalog-loading-dark-390.png', fullPage: true, animations: 'disabled' })
  release()
  await expect(page.locator('.data-table')).toHaveAttribute('aria-busy', 'false')
  await expect(page.getByText('尝试减少筛选条件，或在采集任务中补充当前研究范围。')).toBeVisible()
  await page.screenshot({ path: '../.local/frontend-redesign/states/catalog-empty-dark-390.png', fullPage: true, animations: 'disabled' })
  await page.route('**/api/v1/catalog/achievements?*', route => route.fulfill({ status: 503, contentType: 'application/problem+json', json: { detail: '目录暂不可用，请稍后重试' } }))
  const title = page.getByPlaceholder('按题名关键词模糊检索')
  await title.press('Enter')
  await expect(page.getByRole('alert')).toContainText('目录暂不可用，请稍后重试')
  await page.screenshot({ path: '../.local/frontend-redesign/states/catalog-error-dark-390.png', fullPage: true, animations: 'disabled' })
  let releaseOld!: () => void
  const oldPending = new Promise<void>(resolve => { releaseOld = resolve })
  let requestedOld!: () => void
  const oldStarted = new Promise<void>(resolve => { requestedOld = resolve })
  await page.route('**/api/v1/catalog/achievements?*', async route => {
    const old = new URL(route.request().url()).searchParams.get('title') === '旧条件'
    if (old) { requestedOld(); await oldPending }
    await route.fulfill({ json: { items: [{ ...achievement, title: old ? '过期筛选结果' : achievement.title }], page: 0, size: 20, totalElements: 1, totalPages: 1 } })
  })
  await title.fill('旧条件')
  await title.press('Enter')
  await oldStarted
  await title.fill('新条件')
  await title.press('Enter')
  await expect(page.getByRole('cell', { name: achievement.title })).toBeVisible()
  const oldResponse = page.waitForResponse(response => response.url().includes(encodeURIComponent('旧条件')))
  releaseOld()
  await oldResponse
  await expect(page.getByRole('cell', { name: achievement.title })).toBeVisible()
  await expect(page.getByText('过期筛选结果')).toHaveCount(0)
})

test('窄屏下拉框和多页导航尺寸可用，筛选与任意页跳转保持正确', async ({ page }) => {
  await fixture(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' })
  const queries: URLSearchParams[] = []
  await page.route('**/api/v1/catalog/achievements?*', route => {
    const params = new URL(route.request().url()).searchParams
    queries.push(params)
    const currentPage = Number(params.get('page') ?? 0)
    return route.fulfill({ json: {
      items: Array.from({ length: 20 }, (_, index) => ({ ...achievement, id: currentPage * 20 + index + 1 })),
      page: currentPage, size: 20, totalElements: 240, totalPages: 12,
    } })
  })
  await page.goto('/catalog')
  await expect(page.getByText('显示 1–20，共 240 条')).toBeVisible()
  const checkSelects = async () => {
    for (const select of await page.locator('.el-select__wrapper:visible').all()) {
      expect(await select.evaluate(el => el.getBoundingClientRect().height)).toBeGreaterThanOrEqual(44)
    }
  }
  await checkSelects()
  await page.getByPlaceholder('按题名关键词模糊检索').fill('可信')
  await page.getByRole('button', { name: '查询成果' }).click()
  await expect.poll(() => queries.at(-1)?.get('title')).toBe('可信')
  const pagination = page.locator('.el-pagination')
  for (const button of await pagination.locator('button:not(:disabled)').all()) {
    const rect = await button.boundingBox()
    expect(rect!.width).toBeGreaterThanOrEqual(40)
    expect(rect!.height).toBeGreaterThanOrEqual(40)
  }
  expect(await pagination.locator('.el-input').evaluate(el => el.getBoundingClientRect().height)).toBeGreaterThanOrEqual(44)
  await pagination.locator('.btn-next').click()
  await expect(page.getByText('显示 21–40，共 240 条')).toBeVisible()
  const jump = pagination.getByRole('spinbutton')
  await jump.fill('7')
  await jump.press('Enter')
  await expect(page.getByText('显示 121–140，共 240 条')).toBeVisible()
  expect(queries.at(-1)?.get('title')).toBe('可信')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
  await pagination.scrollIntoViewIfNeeded()
  await page.screenshot({ path: '../.local/frontend-redesign/states/catalog-pagination-dark-390.png', animations: 'disabled' })
  await page.goto('/graph?centerType=ACHIEVEMENT&centerId=42')
  await expect(page.getByRole('img', { name: '知识图谱，共2个节点和1条关系' })).toBeVisible()
  await checkSelects()
})
