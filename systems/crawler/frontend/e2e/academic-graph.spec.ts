import { expect, test, type Page } from '@playwright/test'
import { fixture } from './fixtures/workbench'
import type { GraphNode, GraphResponse } from '../src/types/api'

const works: GraphNode[] = [
  { id: 'ACHIEVEMENT:11', businessId: '11', type: 'ACHIEVEMENT', label: '基于共同署名的学术关系研究', properties: { achievementType: 'article', publicationDate: '2020-01-01' } },
  { id: 'ACHIEVEMENT:12', businessId: '12', type: 'ACHIEVEMENT', label: '开放科研信息的独立研究', properties: { achievementType: 'review', publicationDate: '2020-01-01' } },
  { id: 'ACHIEVEMENT:13', businessId: '13', type: 'ACHIEVEMENT', label: '一种学术成果关联检索方法', properties: { achievementType: 'patent', publicationDate: '2024-02-03' } },
  { id: 'ACHIEVEMENT:14', businessId: '14', type: 'ACHIEVEMENT', label: '面向可信研究数据的硕士学位论文', properties: { achievementType: 'master-thesis', publicationDate: '2022-06-01' } },
  { id: 'ACHIEVEMENT:15', businessId: '15', type: 'ACHIEVEMENT', label: '跨领域知识演化的博士学位论文', properties: { achievementType: 'doctoral-thesis' } },
]
const author = (id: number): GraphNode => ({ id: `AUTHOR:${id}`, businessId: String(id), type: 'AUTHOR', label: id === 1 ? '林研究员' : id === 2 ? '张研究员' : '无成果作者', properties: { orcid: 'test-orcid' } })

function response(url: URL) {
  const id = Number(url.pathname.split('/').at(-1))
  const collaborations = url.searchParams.get('collaborationsOnly') === 'true'
  const chronological = url.searchParams.get('chronological') === 'true'
  const page = Number(url.searchParams.get('page') ?? 0)
  const size = Number(url.searchParams.get('size') ?? 20)
  const category = url.searchParams.get('category')
  const types: Record<string, string[]> = { PAPER: ['article', 'review'], PATENT: ['patent'], MASTER_THESIS: ['master-thesis'], DOCTORAL_THESIS: ['doctoral-thesis'] }
  const selected = (id === 5 ? [] : works).filter(work => (!collaborations || ['11', '13'].includes(work.businessId))
    && (!category || types[category]?.includes(String(work.properties.achievementType))))
    .sort((a, b) => {
      const left = String(a.properties.publicationDate ?? ''), right = String(b.properties.publicationDate ?? '')
      if (!left || !right) return !left && !right ? 0 : !left ? 1 : -1
      return left.localeCompare(right) * (chronological ? 1 : -1) || Number(a.businessId) - Number(b.businessId)
    })
  const items = selected.slice(page * size, (page + 1) * size)
  const root = author(id)
  const graph: GraphResponse = { nodes: [root, ...items], edges: items.map(work => ({ id: `edge-${id}-${work.businessId}`, source: root.id, target: work.id,
    type: String(work.properties.achievementType).endsWith('thesis') ? 'SUPERVISED' : 'AUTHORED', properties: {} })),
  rootNodeId: root.id, truncated: false, narrowingSuggestion: null, appliedLimits: { depth: collaborations ? 2 : 1, nodeLimit: 300, maxHops: 0 },
  syncedAt: null, projectionLagSeconds: null, traceId: 'academic-browser-test' }
  if (collaborations && items.length) {
    const other = author(id === 1 ? 2 : 1)
    graph.nodes.push(other)
    graph.edges.push(...items.map(work => ({ id: `edge-${other.businessId}-${work.businessId}`, source: other.id, target: work.id, type: 'AUTHORED' as const, properties: {} })))
    graph.edges.push({ id: 'cooperation', source: root.id, target: other.id, type: 'COAUTHORED', properties: { derived: true, sharedWorkIds: items.map(work => work.id), sharedWorkCount: items.length } })
  }
  return { graph, page, size, totalWorks: selected.length }
}

async function setup(page: Page) {
  const requests: URL[] = []
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await fixture(page)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.route('**/api/v1/catalog/authors?*', route => route.fulfill({ json: { items: [1, 2].map(id => ({ id, displayName: author(id).label, externalId: null, entityType: 'AUTHOR', achievementCount: 5 })), page: 0, size: 8, totalElements: 2, totalPages: 1 } }))
  await page.route('**/api/v1/graph/authors/*', route => {
    const url = new URL(route.request().url())
    requests.push(url)
    return route.fulfill({ json: response(url) })
  })
  await page.route('**/api/v1/catalog/authors/*/evidence', route => route.fulfill({ json: {
    entityId: Number(new URL(route.request().url()).pathname.split('/').at(-2)), entityType: 'AUTHOR', names: [],
    affiliations: [{ organizationId: 7, displayName: '测试大学', firstPublicationYear: 2020, lastPublicationYear: 2024, achievementCount: 3, datedAchievementCount: 3 }],
    namesTruncated: false, affiliationsTruncated: false,
  } }))
  await page.route('**/api/v1/catalog/achievements/*', route => {
    const node = works.find(work => work.businessId === new URL(route.request().url()).pathname.split('/').at(-1))!
    return route.fulfill({ json: { summary: { id: Number(node.businessId), title: node.label, achievementType: node.properties.achievementType,
      publicationDate: node.properties.publicationDate ?? null, doi: null, primaryVenue: '测试来源', authors: ['测试作者'], topics: [] },
    language: 'zh', abstractText: `${node.label}的完整摘要。`, authorshipsMayBeIncomplete: false, authorships: [], referencedWorkIds: [], sources: [], fields: [] } })
  })
  return { requests, errors }
}

/** 点击真实绘图像素，验证 Canvas 事件到业务详情的完整交互。 */
async function pointForColor(page: Page, rgb: number[], rightHalf = false) {
  const canvas = page.locator('.graph-canvas canvas[data-id="layer2-node"]')
  const point = async () => canvas.evaluate((element, { color, rightHalf }) => {
    const canvas = element as HTMLCanvasElement
    const data = canvas.getContext('2d')!.getImageData(0, 0, canvas.width, canvas.height).data
    const matching: number[] = []
    for (let i = 0; i < data.length; i += 4) if ((!rightHalf || (i / 4) % canvas.width > canvas.width / 2)
      && color.every((value, channel) => data[i + channel] === value) && data[i + 3]! > 220) matching.push(i / 4)
    if (matching.length < 15) return null
    // 靠近颜色区域中心选取实际像素，避开连线端点附近的节点命中范围。
    const centerX = matching.reduce((sum, pixel) => sum + pixel % canvas.width, 0) / matching.length
    const centerY = matching.reduce((sum, pixel) => sum + Math.floor(pixel / canvas.width), 0) / matching.length
    const distance = (pixel: number) => (pixel % canvas.width - centerX) ** 2 + (Math.floor(pixel / canvas.width) - centerY) ** 2
    const pixel = matching.reduce((nearest, candidate) => distance(candidate) < distance(nearest) ? candidate : nearest)
    const box = canvas.getBoundingClientRect()
    return { x: box.x + (pixel % canvas.width) * box.width / canvas.width, y: box.y + Math.floor(pixel / canvas.width) * box.height / canvas.height }
  }, { color: rgb, rightHalf })
  await expect.poll(point).not.toBeNull()
  return (await point())!
}

for (const width of [1440, 390]) {
  test(`三模块作者、共同创作、成果详情与时间线 ${width}px`, async ({ page }, testInfo) => {
    const state = await setup(page)
    await page.setViewportSize({ width, height: width === 390 ? 844 : 1100 })
    await page.goto('/graph')
    await expect(page.getByRole('navigation', { name: '模块页面', exact: true })).toHaveCount(0)
    const navigate = async (name: string) => {
      if (width < 1024) await page.getByRole('button', { name: '打开导航菜单' }).click()
      const navigation = page.getByRole('navigation', { name: width < 1024 ? '业务导航' : '模块导航', exact: true })
      await navigation.getByRole('link', { name, exact: true }).click()
    }
    await page.getByRole('list', { name: '可选作者' }).getByRole('button', { name: /林研究员/ }).click()
    await expect(page).toHaveURL(/academic-relations\?authorId=1/)
    await expect(page.getByRole('img', { name: '林研究员的学术关系图谱，4个节点' })).toBeVisible()
    await expect(page.getByRole('complementary', { name: '图谱内容清单' })).toHaveCount(0)
    if (width === 1440) {
      const box = await page.locator('.academic-canvas-stage').boundingBox()
      expect(box!.width).toBeGreaterThan(1350)
      expect(box!.height).toBeGreaterThan(750)
    }
    await page.screenshot({ path: testInfo.outputPath(`relations-${width}.png`), animations: 'disabled' })
    await page.getByRole('button', { name: '林研究员 的作者详情' }).click()
    const drawer = page.getByRole('dialog')
    await expect(drawer).toContainText('test-orcid')
    await expect(drawer).toContainText('测试大学')
    await page.keyboard.press('Escape')
    await expect(drawer).not.toBeVisible()

    const partnerPoint = await pointForColor(page, [37, 140, 159], true)
    await page.mouse.click(partnerPoint.x, partnerPoint.y)
    await expect(drawer.getByRole('heading', { name: '张研究员', exact: true })).toBeVisible()
    await drawer.getByRole('button', { name: '以此作者查看图谱', exact: true }).click()
    await expect(page).toHaveURL(/academic-relations\?authorId=2/)
    await expect(page.getByRole('img', { name: '张研究员的学术关系图谱，4个节点' })).toBeVisible()
    await page.goBack()
    await expect(page.getByRole('img', { name: '林研究员的学术关系图谱，4个节点' })).toBeVisible()

    const workPoint = await pointForColor(page, [189, 123, 50])
    await page.mouse.click(workPoint.x, workPoint.y)
    await expect(drawer).toContainText('一种学术成果关联检索方法的完整摘要。')
    await page.keyboard.press('Escape')
    await expect(drawer).not.toBeVisible()
    if (width === 1440) {
      await page.mouse.move(0, 0)
      await page.getByRole('img', { name: '林研究员的学术关系图谱，4个节点' }).focus()
      await page.keyboard.press('Escape')
      const edgePoint = await pointForColor(page, [103, 148, 155])
      await page.mouse.click(edgePoint.x, edgePoint.y)
    } else {
      await page.getByRole('button', { name: '内容清单', exact: true }).click()
      await page.getByRole('button', { name: /合作作者 1/ }).click()
      await page.getByRole('button', { name: '与张研究员共同创作的作品' }).click()
    }
    await expect(drawer).toContainText('林研究员 × 张研究员')
    await drawer.getByRole('button', { name: /基于共同署名的学术关系研究/ }).click()
    await expect(drawer).toContainText('基于共同署名的学术关系研究的完整摘要。')
    await page.keyboard.press('Escape')
    await expect(drawer).not.toBeVisible()

    if (await page.getByRole('button', { name: '内容清单', exact: true }).getAttribute('aria-pressed') === 'true') await page.getByRole('button', { name: '内容清单', exact: true }).click()
    await navigate('学术成果图谱')
    await expect(page).toHaveURL(/academic-achievements\?authorId=1/)
    await expect(page.getByRole('img', { name: '林研究员的学术成果图谱，6个节点' })).toBeVisible()
    await expect(page.getByRole('list', { name: '实体类型图例' }).getByRole('listitem')).toHaveText(['作者', '专利', '论文', '指导硕论', '指导博论'])
    await page.screenshot({ path: testInfo.outputPath(`achievements-${width}.png`), animations: 'disabled' })
    const masterPoint = await pointForColor(page, [51, 138, 112])
    await page.mouse.click(masterPoint.x, masterPoint.y)
    await expect(drawer).toContainText('面向可信研究数据的硕士学位论文的完整摘要。')
    await page.keyboard.press('Escape')
    await expect(drawer).not.toBeVisible()

    await navigate('学术背景图谱')
    await expect(page).toHaveURL(/academic-background\?authorId=1/)
    const timeline = page.getByRole('region', { name: '学术成果时间线' })
    await expect(timeline.getByRole('heading')).toHaveText(['2020', '2022', '2024', '日期未知'])
    await page.screenshot({ path: testInfo.outputPath(`background-${width}.png`), animations: 'disabled' })
    await timeline.getByRole('button', { name: /跨领域知识演化的博士学位论文/ }).click()
    await expect(drawer).toContainText('跨领域知识演化的博士学位论文的完整摘要。')
    await page.keyboard.press('Escape')
    expect(state.requests.at(-1)?.searchParams.get('chronological')).toBe('true')
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
    expect(state.errors).toEqual([])
  })
}

test('类型筛选、分页、直达刷新和错误重试保留正确作者', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/academic-background?authorId=1&size=1')
  const timeline = page.getByRole('region', { name: '学术成果时间线' })
  await expect(timeline).toContainText('基于共同署名的学术关系研究')
  await page.getByRole('button', { name: '下一页', exact: true }).click()
  await expect(timeline).toContainText('开放科研信息的独立研究')
  await page.reload()
  await expect(timeline).toContainText('开放科研信息的独立研究')
  await page.getByRole('combobox', { name: '成果类型', exact: true }).press('Enter')
  await page.getByRole('option', { name: '指导博论', exact: true }).click()
  await expect(timeline).toContainText('跨领域知识演化的博士学位论文')
  expect(state.requests.at(-1)?.searchParams.get('page')).toBe('0')
  await expect(page.getByRole('button', { name: '下一页', exact: true })).toBeDisabled()
  await page.route('**/api/v1/graph/authors/*', route => route.fulfill({ status: 503, contentType: 'application/problem+json', body: JSON.stringify({ detail: '图谱暂不可用' }) }), { times: 1 })
  await page.getByRole('button', { name: '刷新', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('图谱暂不可用')
  await expect(timeline).toHaveCount(0)
  await page.getByRole('button', { name: '刷新', exact: true }).click()
  await expect(timeline).toContainText('跨领域知识演化的博士学位论文')
  await page.goto('/academic-achievements?authorId=5')
  await expect(page.getByText('当前范围内暂无成果', { exact: true })).toBeVisible()
  await page.goto('/academic-achievements?authorId=invalid')
  await expect(page.getByRole('alert')).toContainText('作者编号无效')
  expect(state.requests.some(url => url.pathname.endsWith('/invalid'))).toBe(false)
  expect(state.errors).toEqual([])
})
