import { expect, test, type Page } from '@playwright/test'
import type { GraphResponse } from '../src/types/api'

const fullName = '完整的跨学科学术合作网络研究作品标题不得截断'
const sample: GraphResponse = {
  nodes: [
    { id: 'AUTHOR:1', businessId: '1', type: 'AUTHOR', label: '林研究员', properties: { extend_data: '{"机构":"研究院","成果数":0}' } },
    { id: 'ACHIEVEMENT:2', businessId: '2', type: 'ACHIEVEMENT', label: fullName, properties: { abstract: '<img src=x onerror=alert(1)>' } },
  ], edges: [{ id: 'authored-1', source: 'AUTHOR:1', target: 'ACHIEVEMENT:2', type: 'AUTHORED', properties: {} }],
  typeDefinitions: [
    { kind: 'NODE', code: 'AUTHOR', displayName: '作者', color: '#258ca3', size: 50, reviewStatus: 'APPROVED', version: 0 },
    { kind: 'NODE', code: 'ACHIEVEMENT', displayName: '作品', color: '#2363b8', size: 50, reviewStatus: 'APPROVED', version: 0 },
  ], rootNodeId: '', truncated: false, narrowingSuggestion: null, appliedLimits: { depth: 2, nodeLimit: 300, maxHops: 0 },
  syncedAt: null, projectionLagSeconds: 0, traceId: 'vis-e2e',
}

async function setup(page: Page, graphSample: GraphResponse = sample) {
  const requests: URL[] = []
  const writes: string[] = []
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'error' && !message.text().includes('Failed to load resource')) errors.push(message.text()) })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url())
    if (route.request().method() !== 'GET') writes.push(url.pathname)
    if (url.pathname === '/api/v1/auth/me') return route.fulfill({ json: { id: 9, username: 'graph-reader', roles: ['RESEARCHER'], permissions: ['GRAPH_READ', 'CATALOG_READ', 'ACCOUNT_SELF_READ'] } })
    if (url.pathname.startsWith('/api/v1/graph/')) {
      requests.push(url)
      if (url.pathname.endsWith('subgraph')) {
        expect(url.searchParams.get('depth')).toBe('2')
        expect(url.searchParams.get('nodeLimit')).toBe('300')
      }
      return route.fulfill({ json: { ...graphSample, rootNodeId: url.pathname.endsWith('subgraph') ? `${url.searchParams.get('centerType')}:${url.searchParams.get('centerId')}` : '' } })
    }
    return route.fulfill({ json: { items: [], totalElements: 0 } })
  })
  return { requests, writes, errors }
}

/** 从真实 Canvas 的分类色定位点击位置，不向产品暴露测试实例或调试接口。 */
async function nodePoint(page: Page, color: [number, number, number]) {
  const canvas = page.locator('.graph-canvas canvas')
  await expect.poll(async () => canvas.evaluate((element, rgb) => {
    const image = (element as HTMLCanvasElement).getContext('2d')!.getImageData(0, 0, (element as HTMLCanvasElement).width, (element as HTMLCanvasElement).height)
    let matches = 0
    for (let i = 0; i < image.data.length; i += 4) if (rgb.every((value, channel) => image.data[i + channel] === value) && image.data[i + 3]! > 200) matches++
    return matches
  }, color)).toBeGreaterThan(30)
  return canvas.evaluate((element, rgb) => {
    const surface = element as HTMLCanvasElement
    const image = surface.getContext('2d')!.getImageData(0, 0, surface.width, surface.height)
    let total = 0; let x = 0; let y = 0
    for (let i = 0; i < image.data.length; i += 4) if (rgb.every((value, channel) => image.data[i + channel] === value) && image.data[i + 3]! > 200) {
      total++; x += i / 4 % image.width; y += Math.floor(i / 4 / image.width)
    }
    const bounds = surface.getBoundingClientRect()
    return { x: bounds.x + x / total * bounds.width / surface.width, y: bounds.y + y / total * bounds.height / surface.height }
  }, color)
}

test('真实画布双击、历史截断、返回及刷新使用正确后端中心，复用画布', async ({ page }) => {
  const state = await setup(page)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/graph')
  await expect(page.getByRole('img', { name: '知识图谱，共2个节点和1条关系' })).toBeVisible()
  const history = page.getByRole('navigation', { name: '图谱浏览历史' })
  await expect(history).toHaveCount(0)
  const original = await page.locator('.graph-canvas canvas').elementHandle()
  const author = await nodePoint(page, [37, 140, 163])
  await page.mouse.dblclick(author.x, author.y)
  await expect(history).toBeVisible()
  await expect.poll(() => state.requests.at(-1)?.searchParams.get('centerId')).toBe('1')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  const work = await nodePoint(page, [35, 99, 184])
  await page.mouse.dblclick(work.x, work.y)
  await expect.poll(() => state.requests.at(-1)?.searchParams.get('centerId')).toBe('2')
  await page.getByRole('button', { name: '刷新图谱' }).click()
  await expect.poll(() => state.requests.at(-1)?.searchParams.get('centerId')).toBe('2')
  await history.getByRole('button', { name: '返回上一级' }).click()
  await expect.poll(() => state.requests.at(-1)?.searchParams.get('centerId')).toBe('1')
  await page.getByRole('button', { name: '刷新图谱' }).click()
  await expect.poll(() => state.requests.at(-1)?.searchParams.get('centerId')).toBe('1')
  await history.getByRole('button', { name: '全部', exact: true }).click()
  await expect(history).toHaveCount(0)
  await expect.poll(() => state.requests.at(-1)?.pathname).toBe('/api/v1/graph/overview')
  expect(await original!.evaluate(element => element.isConnected)).toBe(true)
  expect(state.errors).toEqual([])
  expect(state.writes).toEqual([])
  await page.mouse.move(20, 20)
  await page.screenshot({ path: 'test-results/graph-vis-desktop.png', fullPage: true })
})

test('CSS 压缩后的秒单位动效在首次适配和刷新后仍能显示节点', async ({ page }) => {
  const state = await setup(page)
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  const graph: GraphResponse = { ...sample, nodes: [], edges: [] }
  for (let id = 1; id <= 10; id++) {
    graph.nodes.push(
      { ...sample.nodes[0]!, id: `AUTHOR:${id}`, businessId: String(id) },
      { ...sample.nodes[1]!, id: `ACHIEVEMENT:${id}`, businessId: String(id) },
    )
    graph.edges.push({ ...sample.edges[0]!, id: `authored-${id}`, source: `AUTHOR:${id}`, target: `ACHIEVEMENT:${id}` })
  }
  await page.route('**/api/v1/graph/overview', async route => {
    // 数据返回前模拟正式构建的秒单位，确保首次适配也经过单位转换。
    await page.addStyleTag({ content: ':root { --duration-slow: .28s; }' })
    await route.fulfill({ json: graph })
  })
  await page.goto('/graph')
  await expect(page.getByRole('img', { name: '知识图谱，共20个节点和10条关系' })).toHaveAttribute('aria-busy', 'false')
  let paintedSamples = 0
  await expect.poll(async () => {
    const painted = await page.locator('.graph-canvas canvas').evaluate(element => {
      const canvas = element as HTMLCanvasElement
      const pixels = canvas.getContext('2d')!.getImageData(0, 0, canvas.width, canvas.height).data
      let authors = 0
      let works = 0
      for (let i = 0; i < pixels.length; i += 4) {
        if (pixels[i + 3]! <= 200) continue
        if (pixels[i] === 37 && pixels[i + 1] === 140 && pixels[i + 2] === 163) authors++
        if (pixels[i] === 35 && pixels[i + 1] === 99 && pixels[i + 2] === 184) works++
      }
      return authors > 30 && works > 30
    })
    paintedSamples = painted ? paintedSamples + 1 : 0
    return paintedSamples
  }, { intervals: [200], timeout: 5000 }).toBeGreaterThanOrEqual(3)
  await page.getByRole('button', { name: '刷新图谱', exact: true }).click()
  await page.getByRole('button', { name: '适应画布', exact: true }).click()
  await nodePoint(page, [37, 140, 163])
  await nodePoint(page, [35, 99, 184])
  expect(state.errors).toEqual([])
})

test('节点和关系右键菜单、完整名称、扩展字段与只读入口可用', async ({ page }) => {
  const state = await setup(page)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/graph')
  const author = await nodePoint(page, [37, 140, 163])
  await page.mouse.click(author.x, author.y, { button: 'right' })
  const menu = page.getByRole('menu', { name: '图谱操作' })
  await expect(menu).toBeVisible()
  await expect(menu.getByRole('menuitem', { name: '编辑节点（暂不可用）' })).toBeDisabled()
  await menu.getByRole('menuitem', { name: '查看详情', exact: true }).click()
  const detail = page.getByRole('dialog')
  await expect(detail).toContainText('研究院')
  await expect(detail).toContainText('成果数0')
  await page.keyboard.press('Escape')
  await expect(detail).not.toBeVisible()
  const work = await nodePoint(page, [35, 99, 184])
  await page.mouse.click(work.x, work.y)
  await expect(detail).toContainText(fullName)
  await expect(detail.locator('img')).toHaveCount(0)
  await page.keyboard.press('Escape')
  await expect(detail).not.toBeVisible()
  const currentAuthor = await nodePoint(page, [37, 140, 163])
  const currentWork = await nodePoint(page, [35, 99, 184])
  const midpoint = { x: (currentAuthor.x + currentWork.x) / 2, y: (currentAuthor.y + currentWork.y) / 2 }
  const edge = await page.locator('.graph-canvas canvas').evaluate((element, middle) => {
    const surface = element as HTMLCanvasElement
    const bounds = surface.getBoundingClientRect()
    const pixels = surface.getContext('2d')!.getImageData(0, 0, surface.width, surface.height)
    let nearest: { x: number; y: number } | null = null
    let distance = Infinity
    for (let i = 0; i < pixels.data.length; i += 4) {
      const isEdge = [[118, 144, 168], [245, 158, 11]].some(rgb => rgb.every((color, channel) => Math.abs(pixels.data[i + channel]! - color) < 5))
      if (!isEdge || pixels.data[i + 3]! < 100) continue
      const point = { x: bounds.x + (i / 4 % surface.width) * bounds.width / surface.width,
        y: bounds.y + Math.floor(i / 4 / surface.width) * bounds.height / surface.height }
      const next = Math.hypot(point.x - middle.x, point.y - middle.y)
      if (next < distance) { nearest = point; distance = next }
    }
    return nearest
  }, midpoint)
  expect(edge).not.toBeNull()
  await page.mouse.click(edge!.x, edge!.y, { button: 'right' })
  await expect(menu.getByRole('menuitem', { name: '删除关系（暂不可用）' })).toBeDisabled()
  await page.keyboard.press('Escape')
  await expect(menu).toHaveCount(0)
  await expect(page.getByRole('button', { name: '新增节点', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '拖动建立关系', exact: true })).toHaveCount(0)
  expect(state.writes).toEqual([])
  expect(state.errors).toEqual([])
})

test('1024px 子图历史与合作详情同时显示时操作栏不重叠', async ({ page }) => {
  const state = await setup(page, {
    ...sample,
    nodes: [...sample.nodes, { id: 'AUTHOR:3', businessId: '3', type: 'AUTHOR', label: '张研究员', properties: {} }],
    edges: [...sample.edges,
      { id: 'authored-3', source: 'AUTHOR:3', target: 'ACHIEVEMENT:2', type: 'AUTHORED', properties: {} },
      { id: 'coauthored-1-3', source: 'AUTHOR:1', target: 'AUTHOR:3', type: 'COAUTHORED',
        properties: { derived: true, sharedWorkIds: ['ACHIEVEMENT:2'], sharedWorkCount: 1 } },
    ],
  })
  await page.setViewportSize({ width: 1024, height: 900 })
  await page.goto('/graph')
  const work = await nodePoint(page, [35, 99, 184])
  await page.mouse.dblclick(work.x, work.y)
  const history = page.getByRole('navigation', { name: '图谱浏览历史' })
  await expect(history).toBeVisible()
  await expect.poll(() => state.requests.at(-1)?.searchParams.get('centerId')).toBe('2')
  await page.getByRole('button', { name: '合作作品', exact: true }).click()
  const drawer = page.getByRole('dialog', { name: '合作作品', exact: true })
  await drawer.getByRole('button', { name: '在图中查看', exact: true }).click()
  const panel = page.getByRole('region', { name: '合作作品详情' })
  await expect(panel).toContainText('共同作品依据（1）')
  await expect(history.getByRole('button', { name: '返回上一级' })).toBeInViewport({ ratio: 1 })
  const controlsBounds = await page.locator('.canvas-controls').boundingBox()
  const panelBounds = await panel.boundingBox()
  expect(controlsBounds!.x + controlsBounds!.width).toBeLessThanOrEqual(panelBounds!.x)
  await expect(panel.getByRole('button', { name: '返回完整图谱' })).toBeInViewport({ ratio: 1 })
  await panel.getByRole('button', { name: '返回完整图谱' }).click()
  await expect(panel).toHaveCount(0)
  await expect(history).toBeVisible()
  expect(state.writes).toEqual([])
  expect(state.errors).toEqual([])
})

test('空态、非法响应、快速筛选和容器缩放后视图保持一致', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/graph')
  await expect(page.getByRole('img', { name: '知识图谱，共2个节点和1条关系' })).toBeVisible()
  const search = page.getByRole('textbox', { name: '搜索当前图谱' })
  await search.fill('不存在')
  await expect(page.getByText('没有匹配的节点或关系', { exact: true })).toBeVisible()
  await search.fill('不存在')
  await search.fill('林研究员')
  await search.fill('')
  await expect(page.getByRole('img', { name: '知识图谱，共2个节点和1条关系' })).toBeVisible()
  await page.route('**/api/v1/graph/overview', route => route.fulfill({ json: { ...sample, nodes: [...sample.nodes, sample.nodes[0]] } }))
  await page.getByRole('button', { name: '刷新图谱' }).click()
  await expect(page.getByText(/图谱数据异常：.*ID 重复/)).toBeVisible()
  await expect(page.getByRole('img', { name: '知识图谱，共2个节点和1条关系' })).toBeVisible()
  await page.route('**/api/v1/graph/overview', route => route.fulfill({ json: { ...sample, nodes: [], edges: [] } }))
  await page.getByRole('button', { name: '刷新图谱' }).click()
  await expect(page.getByText('暂无已同步的作者和作品', { exact: true })).toBeVisible()
  await page.unroute('**/api/v1/graph/overview')
  await page.getByRole('button', { name: '刷新图谱' }).click()
  await page.setViewportSize({ width: 390, height: 844 })
  await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' })
  await expect(page.locator('.graph-canvas canvas')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await expect.poll(async () => page.locator('.graph-canvas canvas').evaluate(element => Math.abs(element.getBoundingClientRect().width - element.parentElement!.getBoundingClientRect().width))).toBeLessThan(2)
  await page.screenshot({ path: 'test-results/graph-vis-mobile-dark.png', fullPage: true })
  for (let i = 0; i < 2; i++) {
    await page.getByRole('link', { name: '高级查询', exact: true }).click()
    await expect(page).toHaveURL(/\/graph\/explore$/)
    await page.getByRole('button', { name: '打开导航菜单' }).click()
    await page.getByRole('dialog').getByRole('navigation', { name: '业务导航' }).getByRole('link', { name: '图谱概览', exact: true }).click()
    await expect(page).toHaveURL(/\/graph$/)
    await expect(page.locator('.graph-canvas canvas')).toHaveCount(1)
    await expect(page.getByRole('img', { name: '知识图谱，共2个节点和1条关系' })).toHaveAttribute('aria-busy', 'false')
  }
  expect(state.errors).toEqual([])
})

test('图谱概览在桌面和窄屏均保持单屏，统计靠上且不遮挡画布操作', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/graph')
  await expect(page.getByRole('img', { name: '知识图谱，共2个节点和1条关系' })).toBeVisible()
  await expect(page.locator('.overview-navigation, .scope-description, .overview-write-actions')).toHaveCount(0)
  await expect(page.getByText('筛选仅作用于当前读取范围。双击节点可查看两跳子图；右键查看操作。全局统计暂未提供。')).toHaveCount(0)
  for (const viewport of [{ width: 1440, height: 900 }, { width: 390, height: 844 }]) {
    await page.setViewportSize(viewport)
    await expect.poll(() => page.evaluate(() => {
      const main = document.querySelector('main')!
      return document.documentElement.scrollHeight <= innerHeight + 1
        && main.scrollHeight <= main.clientHeight + 1
        && document.documentElement.scrollWidth <= innerWidth + 1
    })).toBe(true)
    const stage = await page.locator('.overview-stage').boundingBox()
    const statistics = await page.getByLabel('当前图谱统计').boundingBox()
    const controls = await page.locator('.canvas-controls').boundingBox()
    const graph = await page.locator('.graph-canvas').boundingBox()
    expect(stage).not.toBeNull()
    expect(statistics).not.toBeNull()
    expect(controls).not.toBeNull()
    expect(graph!.height).toBeGreaterThan(100)
    expect(statistics!.y - stage!.y).toBeLessThan(viewport.width < 768 ? controls!.height + 16 : 20)
    expect(statistics!.x >= controls!.x + controls!.width || statistics!.y >= controls!.y + controls!.height).toBe(true)
    const legend = await page.getByRole('list', { name: '节点类型图例' }).boundingBox()
    expect(legend!.y + legend!.height).toBeLessThanOrEqual(viewport.height)
    await expect(page.getByRole('button', { name: '节点表', exact: true })).toBeInViewport()
    await expect(page.getByRole('button', { name: '关系表', exact: true })).toBeInViewport()
  }
  expect(state.errors).toEqual([])
})

test('画布支持节点拖动、平移、滚轮缩放和完整名称悬停提示', async ({ page }) => {
  const state = await setup(page)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/graph')
  const point = await nodePoint(page, [35, 99, 184])
  await page.mouse.move(point.x, point.y)
  await expect(page.locator('.vis-tooltip')).toContainText(fullName)
  await page.mouse.down()
  await page.mouse.move(point.x + 80, point.y + 40, { steps: 8 })
  await page.mouse.up()
  const moved = await nodePoint(page, [35, 99, 184])
  expect(moved.x - point.x).toBeGreaterThan(50)
  // vis-network 用50ms窗口合并双 Hammer 事件，下一次独立手势需越过该窗口。
  await page.waitForTimeout(60)
  const stage = await page.locator('.overview-stage').boundingBox()
  await page.mouse.move(stage!.x + 40, stage!.y + 220)
  await page.mouse.down()
  await page.mouse.move(stage!.x + 110, stage!.y + 250, { steps: 8 })
  await page.mouse.up()
  const panned = await nodePoint(page, [35, 99, 184])
  expect(panned.x - moved.x).toBeGreaterThan(40)
  const pixels = () => page.locator('.graph-canvas canvas').evaluate(element => {
    const surface = element as HTMLCanvasElement
    const data = surface.getContext('2d')!.getImageData(0, 0, surface.width, surface.height).data
    let count = 0
    for (let i = 0; i < data.length; i += 4) if (data[i] === 35 && data[i + 1] === 99 && data[i + 2] === 184) count++
    return count
  })
  const beforeZoom = await pixels()
  await page.mouse.move(panned.x, panned.y)
  await page.mouse.wheel(0, -200)
  await expect.poll(pixels).toBeGreaterThan(beforeZoom * 1.1)
  expect(state.errors).toEqual([])
})

for (const reducedMotion of ['reduce', 'no-preference'] as const) test(`密集网络默认展开全部节点（${reducedMotion}），自动适配后节点圆面不叠成一团`, async ({ page }) => {
  const state = await setup(page)
  await page.emulateMedia({ reducedMotion })
  const graph: GraphResponse = { ...sample, nodes: [], edges: [] }
  for (let index = 0; index < 40; index++) {
    graph.nodes.push(
      { ...sample.nodes[0]!, id: `AUTHOR:${index + 1}`, businessId: String(index + 1), label: `作者${index + 1}` },
      { ...sample.nodes[1]!, id: `ACHIEVEMENT:${index + 1}`, businessId: String(index + 1), label: `作品${index + 1}` },
    )
    for (const offset of [0, 1, 3]) graph.edges.push({ ...sample.edges[0]!, id: `edge-${index}-${offset}`,
      source: `AUTHOR:${index + 1}`, target: `ACHIEVEMENT:${(index + offset) % 40 + 1}` })
  }
  await page.route('**/api/v1/graph/overview', route => route.fulfill({ json: graph }))
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/graph')
  await expect(page.getByRole('img', { name: '知识图谱，共80个节点和120条关系' })).toBeVisible()
  const geometry = () => page.locator('.graph-canvas canvas').evaluate(element => {
    const canvas = element as HTMLCanvasElement
    const { width, height } = canvas
    const pixels = canvas.getContext('2d')!.getImageData(0, 0, width, height).data
    const seen = new Uint8Array(width * height)
    const colored = (index: number) => pixels[index * 4 + 3]! > 200 && (
      pixels[index * 4] === 37 && pixels[index * 4 + 1] === 140 && pixels[index * 4 + 2] === 163
      || pixels[index * 4] === 35 && pixels[index * 4 + 1] === 99 && pixels[index * 4 + 2] === 184)
    const regions: Array<{ x: number; y: number }> = []
    for (let start = 0; start < seen.length; start++) {
      if (seen[start] || !colored(start)) continue
      const pending = [start]
      seen[start] = 1
      let count = 0; let x = 0; let y = 0
      while (pending.length) {
        const index = pending.pop()!
        const px = index % width; const py = Math.floor(index / width)
        count++; x += px; y += py
        for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
          const nx = px + dx!; const ny = py + dy!
          const next = ny * width + nx
          if (nx >= 0 && nx < width && ny >= 0 && ny < height && !seen[next] && colored(next)) {
            seen[next] = 1; pending.push(next)
          }
        }
      }
      if (count >= 6) regions.push({ x: x / count, y: y / count })
    }
    return { count: regions.length,
      width: (Math.max(...regions.map(point => point.x)) - Math.min(...regions.map(point => point.x))) / width,
      height: (Math.max(...regions.map(point => point.y)) - Math.min(...regions.map(point => point.y))) / height }
  })
  let lastGeometry = ''
  let stableSamples = 0
  await expect.poll(async () => {
    const current = await geometry()
    const signature = JSON.stringify(current)
    stableSamples = signature === lastGeometry && current.count === 80 ? stableSamples + 1 : 0
    lastGeometry = signature
    return stableSamples
  }, { intervals: [150], timeout: 10000 }).toBeGreaterThanOrEqual(3)
  const spread = await geometry()
  expect(spread.width).toBeGreaterThan(.35)
  expect(spread.height).toBeGreaterThan(.35)
  await page.screenshot({ path: `test-results/graph-vis-default-spread-${reducedMotion}.png`, fullPage: true })
  expect(state.errors).toEqual([])
})
