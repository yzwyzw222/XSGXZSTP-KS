import { describe, expect, it } from 'vitest'
import type { AuthorGraphQuery } from '@/services/academic-graph'
import type { GraphNode, GraphResponse } from '@/types/api'
import { academicElements, parseAuthorGraph, timelineGroups, workCategory, workInstitutionNames } from '@/utils/academic-graph'

const work = (id: number, type: string, date?: string): GraphNode => ({
  id: `ACHIEVEMENT:${id}`, businessId: String(id), type: 'ACHIEVEMENT', label: `成果 ${id}`,
  properties: { achievementType: type, publicationDate: date },
})
const root: GraphNode = { id: 'AUTHOR:1', businessId: '1', type: 'AUTHOR', label: '作者', properties: {} }
const graph: GraphResponse = { nodes: [root, work(2, 'article')], edges: [], rootNodeId: root.id,
  truncated: false, narrowingSuggestion: null, appliedLimits: { depth: 1, nodeLimit: 300, maxHops: 0 },
  syncedAt: null, projectionLagSeconds: null, traceId: 'academic-test' }
const query: AuthorGraphQuery = { authorId: 1, collaborationsOnly: false, chronological: true, page: 0, size: 20 }

describe('学术实体与时间线', () => {
  it('论文子类合并展示，硕博和专利独立，其他成果不冒充论文', () => {
    expect(['article', 'review', 'preprint', 'proceedings-article'].map(type => workCategory(work(2, type))))
      .toEqual(['PAPER', 'PAPER', 'PAPER', 'PAPER'])
    expect(['patent', 'master-thesis', 'doctoral-thesis', 'scientific-result', 'book', ''].map(type => workCategory(work(2, type))))
      .toEqual(['PATENT', 'MASTER_THESIS', 'DOCTORAL_THESIS', undefined, undefined, undefined])
  })

  it('按日期、规范编号稳定排序，缺日期和无效日期单独收尾且不改写输入', () => {
    const works = [work(4, 'doctoral-thesis'), work(3, 'article', '2024-01-01'), work(2, 'patent', '2024-01-01'), work(1, 'master-thesis', '2020-06-01'), work(5, 'article', 'unknown')]
    expect(timelineGroups(works).map(group => [group.year, group.works.map(item => item.businessId)]))
      .toEqual([['2020', ['1']], ['2024', ['2', '3']], ['日期未知', ['4', '5']]])
    expect(works[0]!.businessId).toBe('4')
    expect(timelineGroups([])).toEqual([])
  })

  it('画布五种实体具有独立样式并保留规范ID', () => {
    const nodes = [root, ...['article', 'patent', 'master-thesis', 'doctoral-thesis'].map((type, i) => work(i + 2, type))]
    const elements = academicElements({ ...graph, nodes })
    expect(new Set(elements.map(item => item.data.displayColor)).size).toBe(5)
    expect(elements.map(item => item.data.id)).toEqual(nodes.map(item => item.id))
    expect(elements.map(item => item.data.typeName)).toEqual(['作者', '论文', '专利', '指导硕论', '指导博论'])
  })

  it('普通创作和指导关系按需显示标签，共同创作标签与证据保持完整', () => {
    const partner: GraphNode = { ...root, id: 'AUTHOR:3', businessId: '3', label: '合作作者' }
    const elements = academicElements({ ...graph, nodes: [...graph.nodes, partner, work(4, 'master-thesis')], edges: [
      { id: 'authored', source: root.id, target: 'ACHIEVEMENT:2', type: 'AUTHORED', properties: {} },
      { id: 'partner-work', source: partner.id, target: 'ACHIEVEMENT:2', type: 'AUTHORED', properties: {} },
      { id: 'supervised', source: root.id, target: 'ACHIEVEMENT:4', type: 'SUPERVISED', properties: {} },
      { id: 'coauthored', source: root.id, target: partner.id, type: 'COAUTHORED', properties: { sharedWorkIds: ['ACHIEVEMENT:2'] } },
    ] })
    expect(elements.find(item => item.data.id === 'authored')?.data).toMatchObject({ label: '创作', labelMode: 'interaction', source: root.id, target: 'ACHIEVEMENT:2' })
    expect(elements.find(item => item.data.id === 'supervised')?.data).toMatchObject({ label: '指导', labelMode: 'interaction', source: root.id, target: 'ACHIEVEMENT:4' })
    const cooperation = elements.find(item => item.data.id === 'coauthored')!.data
    expect(cooperation.labelMode).toBeUndefined()
    expect(cooperation.label).toBe('共同创作')
    expect(cooperation.evidenceIds).toContain('ACHIEVEMENT:2')
  })

  it('跨作者响应、错误分页、非学术实体和损坏拓扑都不能当作有效结果', () => {
    const response = { graph, page: 0, size: 20, totalWorks: 1 }
    expect(parseAuthorGraph(response, query).totalWorks).toBe(1)
    expect(() => parseAuthorGraph(response, { ...query, authorId: 2 })).toThrow('不一致')
    expect(() => parseAuthorGraph({ ...response, page: 1 }, query)).toThrow('不一致')
    expect(() => parseAuthorGraph({ ...response, totalWorks: -1 }, query)).toThrow('分页')
    expect(() => parseAuthorGraph({ ...response, graph: { ...graph, nodes: [root, work(2, 'book')] } }, query)).toThrow('实体类型')
    expect(() => parseAuthorGraph({ ...response, graph: { ...graph, nodes: [root, root] } }, query)).toThrow('ID 重复')
  })

  it('逐篇机构保留同名不同身份，缺少字段、空列表与空名称分别展示', () => {
    const node = work(2, 'article')
    expect(workInstitutionNames(node)).toBe('暂未返回')
    node.properties.institutions = []
    expect(workInstitutionNames(node)).toBe('未收录')
    node.properties.institutions = [{ id: '1', name: ' 测试大学 ' }, { id: '2', name: '测试大学' }, { id: '3', name: '' }]
    expect(workInstitutionNames(node)).toBe('测试大学、测试大学、机构 #3')
  })

  it.each([
    { institutions: null },
    { institutions: '测试大学' },
    { institutions: [{ id: '0', name: '测试大学' }] },
    { institutions: [{ id: '1', name: null }] },
    { institutions: [{ id: '1', name: '甲' }, { id: '1', name: '乙' }] },
    { institutions: [], institutionsTruncated: 'false' },
    { institutions: Array.from({ length: 101 }, (_, i) => ({ id: String(i + 1), name: '机构' })) },
  ])('拒绝损坏或超出约定范围的机构信息：%j', properties => {
    const node = work(2, 'article')
    Object.assign(node.properties, properties)
    expect(() => parseAuthorGraph({ graph: { ...graph, nodes: [root, node] }, page: 0, size: 20, totalWorks: 1 }, query)).toThrow('成果机构')
  })
})
