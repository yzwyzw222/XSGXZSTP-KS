import { expect, test, type Page } from '@playwright/test'
import type { GraphResponse, GraphTypeDefinition } from '../src/types/api'

const defaultTypes: GraphTypeDefinition[] = [
  { kind: 'NODE', code: 'AUTHOR', displayName: '作者', color: '#258ca3', size: 30, reviewStatus: 'PENDING', version: 0 },
  { kind: 'NODE', code: 'ACHIEVEMENT', displayName: '作品', color: '#2363b8', size: 40, reviewStatus: 'PENDING', version: 0 },
  { kind: 'RELATIONSHIP', code: 'AUTHORED', displayName: '创作', color: '#7690a8', size: 2, reviewStatus: 'APPROVED', version: 0 },
  { kind: 'RELATIONSHIP', code: 'COAUTHORED', displayName: '合作', color: '#258ca3', size: 2, reviewStatus: 'PENDING', version: 0 },
]

async function setup(page: Page, options: { readonly?: boolean; conflict?: boolean; conflictAfterFirst?: boolean; dense?: boolean; multipleWorks?: boolean } = {}) {
  let definitions = structuredClone(defaultTypes)
  const updates: GraphTypeDefinition[] = []
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '')
    let data: unknown
    if (path === '/auth/me') data = { id: 9, username: 'graph-reviewer', roles: [options.readonly ? 'RESEARCHER' : 'DATA_OPERATOR'],
      permissions: ['ACCOUNT_SELF_READ', 'GRAPH_READ', 'CATALOG_READ', ...(options.readonly ? [] : ['GRAPH_SYNC_MANAGE'])] }
    else if (path === '/auth/csrf') data = { headerName: 'X-CSRF-TOKEN', token: 'synthetic-browser-test' }
    else if (path === '/graph/types') data = definitions
    else if (path.startsWith('/graph/types/')) {
      if (options.conflict || options.conflictAfterFirst && updates.length === 1) return route.fulfill({ status: 409, contentType: 'application/problem+json', body: JSON.stringify({ detail: '类型配置已被修改，请刷新后重试', errorCode: 'RESOURCE_CONFLICT' }) })
      const input = route.request().postDataJSON()
      const existing = definitions.find(value => path.endsWith(`/${value.kind}/${value.code}`))!
      data = { ...existing, ...input, version: existing.version + 1 }
      updates.push(data as GraphTypeDefinition)
      definitions = definitions.map(value => value === existing ? data as GraphTypeDefinition : value)
    } else if (path === '/graph/subgraph' || path === '/graph/overview') {
      if (path === '/graph/subgraph') expect(new URL(route.request().url()).searchParams.get('includeCoauthors')).toBe('true')
      data = {
        nodes: [
          { id: 'AUTHOR:1', businessId: '1', type: 'AUTHOR', label: '林研究员', properties: {} },
          { id: 'AUTHOR:2', businessId: '2', type: 'AUTHOR', label: '张研究员', properties: {} },
          { id: 'ACHIEVEMENT:42', businessId: '42', type: 'ACHIEVEMENT', label: '学术合作网络研究', properties: {} },
        ],
        edges: [
          { id: 'a1', type: 'AUTHORED', source: 'AUTHOR:1', target: 'ACHIEVEMENT:42', properties: {} },
          { id: 'a2', type: 'AUTHORED', source: 'AUTHOR:2', target: 'ACHIEVEMENT:42', properties: {} },
          { id: 'co', type: 'COAUTHORED', source: 'AUTHOR:1', target: 'AUTHOR:2', properties: { derived: true, sharedWorkIds: ['ACHIEVEMENT:42'], sharedWorkCount: 1 } },
        ],
        rootNodeId: 'AUTHOR:1', typeDefinitions: definitions, truncated: false, narrowingSuggestion: null,
        appliedLimits: { depth: 2, nodeLimit: 100, maxHops: 0 }, syncedAt: null, projectionLagSeconds: null, traceId: 'graph-types-browser-test',
      } satisfies GraphResponse
      if (options.multipleWorks && path === '/graph/overview') {
        const sample = data as GraphResponse
        sample.nodes.push({ id: 'ACHIEVEMENT:43', businessId: '43', type: 'ACHIEVEMENT', label: '跨学科知识图谱的合作创作方法', properties: {} }, { id: 'ACHIEVEMENT:44', businessId: '44', type: 'ACHIEVEMENT', label: '个人独立作品', properties: {} })
        sample.edges.push({ id: 'b1', type: 'AUTHORED', source: 'AUTHOR:1', target: 'ACHIEVEMENT:43', properties: {} }, { id: 'b2', type: 'AUTHORED', source: 'AUTHOR:2', target: 'ACHIEVEMENT:43', properties: {} }, { id: 'solo', type: 'AUTHORED', source: 'AUTHOR:1', target: 'ACHIEVEMENT:44', properties: {} })
        sample.edges.find(edge => edge.id === 'co')!.properties.sharedWorkIds = ['ACHIEVEMENT:42', 'ACHIEVEMENT:43']
      }
      if (options.dense && path === '/graph/overview') {
        const sample = data as GraphResponse
        const original = structuredClone(sample)
        for (let cluster = 1; cluster < 24; cluster++) {
          sample.nodes.push(...original.nodes.map(node => ({ ...node, id: `${node.id}-${cluster}`, label: `${node.label} ${cluster}` })))
          sample.edges.push(...original.edges.map(edge => ({ ...edge, id: `${edge.id}-${cluster}`, source: `${edge.source}-${cluster}`, target: `${edge.target}-${cluster}`, properties: edge.type === 'COAUTHORED' ? { sharedWorkIds: [`ACHIEVEMENT:42-${cluster}`] } : {} })))
        }
      }
    } else data = { items: [], totalElements: 0, page: 0, size: 20 }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) })
  })
  return { updates }
}

test('图谱模块页签支持键盘访问并保持当前页高亮', async ({ page }) => {
  await setup(page)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/graph')
  const nav = page.getByRole('navigation', { name: '模块页面' })
  await expect(nav.getByRole('link')).toHaveText(['图谱概览', '节点样式', '关系样式', '高级查询', '路径分析', '保存的查询'])
  await expect(nav.getByRole('link', { name: '图谱概览', exact: true })).toHaveAttribute('aria-current', 'page')
  const entities = nav.getByRole('link', { name: '节点样式' })
  await entities.focus()
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/graph\/settings\/nodes$/)
  await expect(entities).toHaveAttribute('aria-current', 'page')
})

test('保存 MySQL 类型配置后 Canvas、图例和审核展示一致，合作关系可核对作品依据', async ({ page }) => {
  const { updates } = await setup(page)
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'warning' && message.text().includes('style property')) errors.push(message.text()) })
  await page.setViewportSize({ width: 1600, height: 1100 })
  await page.goto('/graph/entities')
  await page.getByRole('row').filter({ hasText: 'AUTHOR' }).getByRole('button', { name: '编辑' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('textbox', { name: '类型名称', exact: true }).fill('学者')
  await dialog.getByRole('textbox', { name: '颜色值' }).fill('#123456')
  await dialog.getByRole('spinbutton', { name: '尺寸' }).fill('52')
  await dialog.getByRole('combobox', { name: '编辑类型审核状态' }).press('Enter')
  await page.getByRole('option', { name: '已通过' }).click()
  await dialog.getByRole('button', { name: '保存配置' }).click()
  await expect(dialog).not.toBeVisible()
  expect(updates[0]).toMatchObject({ displayName: '学者', color: '#123456', size: 52, reviewStatus: 'APPROVED', version: 1 })
  await page.goto('/graph?centerType=AUTHOR&centerId=1')
  await expect(page.getByRole('img', { name: '知识图谱，共3个节点和3条关系' })).toBeVisible()
  await expect(page.getByRole('list', { name: '节点类型图例' })).toContainText('学者')
  await expect(page.getByText('类型审核：已通过 · 52 px')).toBeVisible()
  await expect.poll(() => page.locator('.graph-canvas canvas').evaluateAll(elements => elements.some(element => {
    const canvas = element as HTMLCanvasElement
    const pixels = canvas.getContext('2d')!.getImageData(0, 0, canvas.width, canvas.height).data
    for (let i = 0; i < pixels.length; i += 4) if (pixels[i] === 18 && pixels[i + 1] === 52 && pixels[i + 2] === 86 && pixels[i + 3]! > 0) return true
    return false
  }))).toBe(true)
  await page.getByRole('button', { name: '作者—作品', exact: true }).click()
  await expect(page.getByRole('img', { name: '知识图谱，共3个节点和2条关系' })).toBeVisible()
  await page.getByRole('button', { name: '作者—作者', exact: true }).click()
  await expect(page.getByRole('img', { name: '知识图谱，共3个节点和3条关系' })).toBeVisible()
  await page.getByRole('button', { name: '关系表', exact: true }).click()
  await page.getByRole('row').filter({ has: page.getByRole('cell', { name: '合作', exact: true }) }).getByRole('button', { name: '查看', exact: true }).click()
  await expect(page.getByText('派生合作 · 当前子图共同作品 1 部')).toBeVisible()
  await expect(page.getByText('学术合作网络研究', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '图形', exact: true }).click()
  await page.screenshot({ path: 'test-results/graph-coauthors.png', fullPage: true })
  await page.getByRole('button', { name: '全部关系' }).click()
  await page.screenshot({ path: 'test-results/graph-overview-canvas.png', fullPage: true })
  await page.getByRole('button', { name: '作者—作者', exact: true }).click()
  await page.getByRole('button', { name: '查询条件', exact: true }).click()
  await page.getByRole('button', { name: '加载图谱', exact: true }).click()
  await expect(page.getByRole('button', { name: '全部关系', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByText('类型审核：已通过 · 52 px')).toBeVisible()
  expect(errors).toEqual([])
})

test('版本冲突保留表单内容，科研用户只能读取配置', async ({ page }) => {
  await setup(page, { conflict: true })
  await page.goto('/graph/entities')
  await page.getByRole('row').filter({ hasText: 'AUTHOR' }).getByRole('button', { name: '编辑' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('textbox', { name: '类型名称', exact: true }).fill('修改后的作者')
  await dialog.getByRole('button', { name: '保存配置' }).click()
  await expect(dialog.getByText('数据已被其他操作更新，请刷新后重试')).toBeVisible()
  await expect(dialog.getByRole('textbox', { name: '类型名称', exact: true })).toHaveValue('修改后的作者')
  await setup(page, { readonly: true })
  await page.goto('/graph/entities')
  await expect(page.getByText('只读', { exact: true })).toHaveCount(2)
  await expect(page.getByRole('button', { name: '编辑', exact: true })).toHaveCount(0)
})

test('窄屏深色图谱保持 Canvas 与详情入口可用', async ({ page }) => {
  await setup(page, { readonly: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' })
  await page.goto('/graph?centerType=AUTHOR&centerId=1')
  await expect(page.locator('.graph-canvas canvas').first()).toBeVisible()
  await page.getByRole('button', { name: '作者—作者', exact: true }).click()
  await page.getByRole('button', { name: '查看图谱详情' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: 'test-results/graph-mobile-dark.png', fullPage: true })
})


test('概览自动加载多簇 Canvas，筛选与统计一致并可核对合作依据', async ({ page }) => {
  await setup(page, { dense: true })
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.setViewportSize({ width: 1600, height: 1000 })
  await page.goto('/graph')
  await expect(page.getByRole('img', { name: '知识图谱，共72个节点和72条关系' })).toBeVisible()
  const stage = await page.locator('.overview-stage').boundingBox()
  expect(stage!.height).toBeGreaterThan(650)
  expect(stage!.width).toBeGreaterThan(1250)
  await expect(page.locator('.canvas-statistics')).toContainText('节点类型数：2')
  await expect(page.locator('.canvas-statistics')).toContainText('关系类型数：2')
  await expect(page.locator('.graph-canvas canvas').first()).toBeVisible()
  await page.screenshot({ path: 'test-results/graph-overview-reference.png', fullPage: true })
  await page.getByRole('textbox', { name: '搜索当前图谱' }).fill('不存在的作品')
  await expect(page.getByText('没有匹配的节点或关系', { exact: true })).toBeVisible()
  await page.getByRole('textbox', { name: '搜索当前图谱' }).clear()
  await page.getByRole('combobox', { name: '关系类型', exact: true }).press('Enter')
  await page.getByRole('option', { name: '合作', exact: true }).click()
  await expect(page.getByRole('img', { name: '知识图谱，共72个节点和72条关系' })).toBeVisible()
  await page.getByRole('button', { name: '刷新图谱' }).click()
  await expect(page.getByRole('img', { name: '知识图谱，共72个节点和72条关系' })).toBeVisible()
  await page.getByRole('button', { name: '关系表', exact: true }).click()
  await page.getByRole('dialog').getByRole('row').filter({ has: page.getByRole('cell', { name: '合作', exact: true }) }).first().getByRole('button', { name: '详情', exact: true }).click()
  await expect(page.getByRole('region', { name: '合作作品详情' }).getByText('共同作品依据（1）')).toBeVisible()
  await expect(page.getByRole('region', { name: '合作作品详情' }).getByRole('link', { name: '《学术合作网络研究》', exact: true })).toBeVisible()
  expect(errors).toEqual([])
})

test('实体与关系仅展示两类配置，支持详情及带版本的批量审核', async ({ page }) => {
  const { updates } = await setup(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/graph/entities')
  await expect(page.locator('.types-table tbody tr')).toHaveCount(2)
  const colors = await page.locator('.types-table .type-color').evaluateAll(items => items.map(item => getComputedStyle(item).backgroundColor))
  expect(new Set(colors).size).toBe(2)
  await page.screenshot({ path: 'test-results/graph-entities-reference.png', fullPage: true })
  await page.locator('.types-table thead .el-checkbox').click()
  await expect(page.locator('.types-table thead').getByRole('checkbox')).toBeChecked()
  await page.getByRole('combobox', { name: '批量修改状态' }).press('Enter')
  await page.getByRole('option', { name: '已通过', exact: true }).click()
  await expect(page.getByText('已更新 2 / 2 项类型状态。', { exact: true })).toBeVisible()
  expect(updates).toHaveLength(2)
  expect(updates.every(item => item.reviewStatus === 'APPROVED' && item.version === 1)).toBe(true)
  await page.getByRole('navigation', { name: '模块页面' }).getByRole('link', { name: '关系样式' }).click()
  await expect(page.locator('.types-table tbody tr')).toHaveCount(2)
  await expect(page.getByText('作者 → 作品', { exact: true })).toBeVisible()
  await expect(page.getByText('作者 ↔ 作者 · 共同作品', { exact: true })).toBeVisible()
  await expect(page.locator('.types-table').getByText('已通过', { exact: true })).toHaveCount(1)
  await expect(page.locator('.types-table').getByText('待审核', { exact: true })).toHaveCount(1)
  await expect(page.locator('.types-table thead').getByRole('checkbox')).not.toBeChecked()
  await page.mouse.move(1000, 10)
  await page.screenshot({ path: 'test-results/graph-relations-reference.png', fullPage: true, animations: 'disabled' })
  await page.getByRole('row').filter({ hasText: 'COAUTHORED' }).getByRole('button', { name: '详情' }).click()
  await expect(page.getByRole('dialog')).toContainText('由作者共同作品推导合作')
})

test('窄屏深色概览自动加载并允许通过表格打开详情', async ({ page }) => {
  await setup(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' })
  await page.goto('/graph')
  await expect(page.getByRole('img', { name: '知识图谱，共3个节点和3条关系' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: 'test-results/graph-overview-mobile.png', fullPage: true })
  await page.getByRole('button', { name: '节点表', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '详情' }).first().click()
  await expect(page.getByText('业务ID：1', { exact: true })).toBeVisible()
})

test('概览刷新失败保留结果，重试后恢复；空网络显示明确空态', async ({ page }) => {
  await setup(page)
  await page.goto('/graph')
  await expect(page.getByRole('img', { name: '知识图谱，共3个节点和3条关系' })).toBeVisible()
  await page.route('**/api/v1/graph/overview', route => route.fulfill({ status: 503, contentType: 'application/problem+json', body: JSON.stringify({ detail: 'Neo4j 暂不可用' }) }))
  await page.getByRole('button', { name: '刷新图谱' }).click()
  await expect(page.locator('.overview-notice')).toBeVisible()
  await expect(page.getByRole('img', { name: '知识图谱，共3个节点和3条关系' })).toBeVisible()
  await page.unroute('**/api/v1/graph/overview')
  await page.getByRole('button', { name: '刷新图谱' }).click()
  await expect(page.locator('.overview-notice')).toHaveCount(0)
  await page.route('**/api/v1/graph/overview', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ nodes: [], edges: [], typeDefinitions: defaultTypes, rootNodeId: '', truncated: false, appliedLimits: { depth: 1, nodeLimit: 300, maxHops: 0 } }) }))
  await page.getByRole('button', { name: '刷新图谱' }).click()
  await expect(page.getByText('暂无已同步的作者和作品', { exact: true })).toBeVisible()
})


test('批量审核部分冲突时保留已保存结果并报告未更新数量', async ({ page }) => {
  const { updates } = await setup(page, { conflictAfterFirst: true })
  await page.goto('/graph/entities')
  await expect(page.locator('.types-table tbody tr')).toHaveCount(2)
  await page.locator('.types-table thead .el-checkbox').click()
  await page.getByRole('combobox', { name: '批量修改状态' }).press('Enter')
  await page.getByRole('option', { name: '已通过', exact: true }).click()
  await expect(page.getByText('已更新 1 / 2 项类型状态。其余未更新，请刷新配置后重试。', { exact: true })).toBeVisible()
  await expect(page.getByText('数据已被其他操作更新，请刷新后重试')).toBeVisible()
  expect(updates).toHaveLength(1)
  await expect(page.locator('.types-table').getByText('已通过', { exact: true })).toHaveCount(1)
  await expect(page.locator('.types-table').getByText('待审核', { exact: true })).toHaveCount(1)
})


test('合作作者共同创作的多部作品可直接在 Canvas 和作品清单中核对', async ({ page }) => {
  await setup(page, { multipleWorks: true })
  await page.setViewportSize({ width: 1500, height: 1000 })
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('/graph')
  await expect(page.getByRole('img', { name: '知识图谱，共5个节点和6条关系' })).toBeVisible()
  await page.getByRole('button', { name: '合作作品', exact: true }).click()
  const drawer = page.getByRole('dialog', { name: '合作作品', exact: true })
  await expect(drawer).toContainText('林研究员 × 张研究员')
  await expect(drawer).toContainText('学术合作网络研究')
  await expect(drawer).toContainText('跨学科知识图谱的合作创作方法')
  await expect(drawer).not.toContainText('个人独立作品')
  await drawer.getByRole('button', { name: '在图中查看', exact: true }).click()
  const panel = page.getByRole('region', { name: '合作作品详情' })
  await expect(panel).toContainText('共同作品依据（2）')
  await expect(panel.getByRole('link')).toHaveCount(2)
  await expect(page.getByRole('img', { name: '知识图谱，共4个节点和5条关系' })).toBeVisible()
  await page.screenshot({ path: 'test-results/cooperation-works-desktop.png', fullPage: true, animations: 'disabled' })
  await page.getByRole('button', { name: '返回完整图谱' }).click()
  await expect(page.getByRole('img', { name: '知识图谱，共5个节点和6条关系' })).toBeVisible()
  await page.getByRole('combobox', { name: '关系类型', exact: true }).press('Enter')
  await page.getByRole('option', { name: '合作', exact: true }).click()
  await expect(page.getByRole('img', { name: '知识图谱，共4个节点和5条关系' })).toBeVisible()
  await expect(page.getByRole('combobox', { name: '节点类型', exact: true })).toBeDisabled()
  await page.getByRole('textbox', { name: '搜索当前图谱' }).fill('跨学科')
  await expect(page.getByRole('img', { name: '知识图谱，共4个节点和5条关系' })).toBeVisible()
  expect(errors).toEqual([])
})

test('窄屏可查看合作作品完整名称并返回原图谱', async ({ page }) => {
  await setup(page, { multipleWorks: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' })
  await page.goto('/graph')
  await page.getByRole('button', { name: '合作作品', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '在图中查看', exact: true }).click()
  const panel = page.getByRole('region', { name: '合作作品详情' })
  await expect(panel).toContainText('跨学科知识图谱的合作创作方法')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: 'test-results/cooperation-works-mobile.png', fullPage: true, animations: 'disabled' })
  await panel.getByRole('button', { name: '返回完整图谱' }).click()
  await expect(page.getByRole('img', { name: '知识图谱，共5个节点和6条关系' })).toBeVisible()
})
