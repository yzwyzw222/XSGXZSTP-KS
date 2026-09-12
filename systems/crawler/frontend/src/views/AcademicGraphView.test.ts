import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import AcademicGraphView from './AcademicGraphView.vue'
import { ApiError } from '@/services/api'
import type { AuthorGraphQuery } from '@/services/academic-graph'
import type { GraphNode } from '@/types/api'

const mocks = vi.hoisted(() => ({ permissions: [] as string[], graph: vi.fn(), evidence: vi.fn(), entities: vi.fn() }))
vi.mock('@/stores/session', () => ({ useSessionStore: () => ({ hasPermission: (permission: string) => mocks.permissions.includes(permission) }) }))
vi.mock('@/services/academic-graph', () => ({ academicGraphApi: { load: mocks.graph } }))
vi.mock('@/services/business', () => ({ catalogApi: { entityEvidence: mocks.evidence, entities: mocks.entities } }))

const works: GraphNode[] = [
  { id: 'ACHIEVEMENT:11', businessId: '11', type: 'ACHIEVEMENT', label: '机构甲论文', properties: { achievementType: 'article', publicationDate: '2022-06-01',
    institutions: [{ id: '1', name: '第一研究所' }, { id: '2', name: '联合实验室' }], institutionsTruncated: false } },
  { id: 'ACHIEVEMENT:12', businessId: '12', type: 'ACHIEVEMENT', label: '机构乙论文', properties: { achievementType: 'article', publicationDate: '2023-09-02',
    institutions: [{ id: '3', name: '第二大学' }], institutionsTruncated: false } },
  { id: 'ACHIEVEMENT:13', businessId: '13', type: 'ACHIEVEMENT', label: '未知资料论文', properties: { achievementType: 'article', institutions: [], institutionsTruncated: false } },
]

function response(query: AuthorGraphQuery) {
  const root: GraphNode = { id: `AUTHOR:${query.authorId}`, businessId: String(query.authorId), type: 'AUTHOR', label: `作者${query.authorId}`, properties: {} }
  const items = structuredClone(works.slice(query.page * query.size, (query.page + 1) * query.size))
  if (query.authorId === 2) for (const item of items) item.properties.institutions = [{ id: '21', name: '作者2对应机构' }]
  return { graph: {
    nodes: [root, ...items],
    edges: items.map(item => ({ id: `AUTHORED:${item.businessId}`, source: root.id, target: item.id, type: 'AUTHORED', properties: {} })),
    rootNodeId: root.id, truncated: false, appliedLimits: { depth: 1, nodeLimit: 300, maxHops: 0 },
  }, page: query.page, size: query.size, totalWorks: works.length }
}

let wrapper: VueWrapper | undefined

async function render(authorId = '1', size = '20') {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }] })
  await router.push({ path: '/academic-background', query: authorId ? { authorId, size } : {} })
  wrapper = mount(AcademicGraphView, { props: { mode: 'background' }, global: { plugins: [router], stubs: {
    GraphCanvas: true, EntitySuggestInput: true, AcademicNodeDetail: true, ElDrawer: true, ElSelect: true,
  } } })
  await flushPromises()
  return { view: wrapper, router }
}

beforeEach(() => {
  vi.resetAllMocks()
  mocks.permissions = ['CATALOG_READ', 'GRAPH_READ']
  mocks.entities.mockResolvedValue({ items: [] })
  mocks.graph.mockImplementation(async (query: AuthorGraphQuery) => response(query))
})
afterEach(() => { wrapper?.unmount(); wrapper = undefined })

describe('学术背景逐篇发布信息', () => {
  it('机构和发布时间对应到每篇论文，多个机构完整展示，缺日期或机构明确标注', async () => {
    const { view } = await render()
    const cards = view.findAll('.timeline-year button')
    expect(cards).toHaveLength(3)
    expect(cards[0]!.text()).toContain('机构：第一研究所、联合实验室')
    expect(cards[0]!.text()).toContain('发布时间2022-06-01')
    expect(cards[0]!.get('time').attributes('datetime')).toBe('2022-06-01')
    expect(cards[0]!.text()).not.toContain('第二大学')
    expect(cards[1]!.text()).toContain('机构：第二大学')
    expect(cards[1]!.get('time').text()).toBe('2023-09-02')
    expect(cards[2]!.text()).toContain('发布时间未收录')
    expect(cards[2]!.text()).toContain('机构：未收录')
    expect(cards[2]!.find('time').exists()).toBe(false)
    expect(view.findAll('.timeline-year h2').map(heading => heading.text())).toEqual(['2022', '2023', '日期未知'])
    expect(view.find('[aria-label="所在机构"]').exists()).toBe(false)
    expect(mocks.evidence).not.toHaveBeenCalled()
  })

  it('仅有图谱权限仍可展示图谱返回的逐篇机构，不另行读取作者目录证据', async () => {
    mocks.permissions = ['GRAPH_READ']
    const { view } = await render()
    expect(view.get('.timeline-institutions').text()).toContain('第一研究所')
    expect(mocks.evidence).not.toHaveBeenCalled()
  })

  it.each(['', 'invalid'])('未选择或提供无效作者时不发出图谱或机构请求：%s', async authorId => {
    const { view } = await render(authorId)
    expect(mocks.graph).not.toHaveBeenCalled()
    expect(mocks.evidence).not.toHaveBeenCalled()
    expect(view.find('.academic-timeline').exists()).toBe(false)
  })

  it('分页时机构随成果一起切换，每页仅发出一次作者图谱请求', async () => {
    const { view } = await render('1', '1')
    expect(view.get('.timeline-year').text()).toContain('第一研究所')
    await view.findAll('.academic-pagination button').find(button => button.text() === '下一页')!.trigger('click')
    await flushPromises()
    expect(view.get('.timeline-year').text()).toContain('第二大学')
    expect(view.text()).not.toContain('第一研究所')
    expect(mocks.graph).toHaveBeenCalledTimes(2)
    expect(mocks.evidence).not.toHaveBeenCalled()
  })

  it('旧响应未返回机构字段、机构未命名及机构截断都有对应提示', async () => {
    mocks.graph.mockImplementationOnce(async (query: AuthorGraphQuery) => {
      const data = response(query)
      delete data.graph.nodes[1]!.properties.institutions
      data.graph.nodes[2]!.properties.institutions = [{ id: '3', name: '' }]
      data.graph.nodes[2]!.properties.institutionsTruncated = true
      return data
    })
    const { view } = await render()
    const cards = view.findAll('.timeline-year button')
    expect(cards[0]!.text()).toContain('机构：暂未返回')
    expect(cards[1]!.text()).toContain('机构：机构 #3')
    expect(cards[1]!.text()).toContain('仅显示该成果的前 100 家机构')
  })

  it('请求失败时不显示旧发布信息，刷新可恢复成果及其机构', async () => {
    const { view } = await render()
    const refresh = view.findAll('.academic-toolbar button').find(button => button.text() === '刷新')!
    mocks.graph.mockRejectedValueOnce(new ApiError('图谱暂不可用', 503))
    await refresh.trigger('click')
    await flushPromises()
    expect(view.get('[role="alert"]').text()).toContain('图谱暂不可用')
    expect(view.find('.academic-timeline').exists()).toBe(false)
    await refresh.trigger('click')
    await flushPromises()
    expect(view.get('.timeline-institutions').text()).toContain('第一研究所')
  })

  it.each(['success', 'failure'])('切换作者后忽略旧请求的迟到结果：%s', async outcome => {
    let resolve!: (data: ReturnType<typeof response>) => void
    let reject!: (reason: unknown) => void
    mocks.graph.mockReturnValueOnce(new Promise<ReturnType<typeof response>>((done, fail) => { resolve = done; reject = fail }))
    const { view, router } = await render()
    const initialQuery = mocks.graph.mock.calls[0]![0] as AuthorGraphQuery
    await router.push({ path: '/academic-background', query: { authorId: '2' } })
    await flushPromises()
    expect(view.get('.timeline-institutions').text()).toContain('作者2对应机构')
    if (outcome === 'success') resolve(response(initialQuery))
    else reject(new ApiError('旧作者错误', 503))
    await flushPromises()
    expect(view.get('.timeline-institutions').text()).toContain('作者2对应机构')
    expect(view.text()).not.toContain('第一研究所')
    expect(view.find('[role="alert"]').exists()).toBe(false)
  })
})
