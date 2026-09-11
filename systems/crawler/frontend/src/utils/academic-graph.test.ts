import { describe, expect, it } from 'vitest'
import type { AuthorGraphQuery } from '@/services/academic-graph'
import type { GraphNode, GraphResponse } from '@/types/api'
import { academicElements, parseAuthorGraph, timelineGroups, workCategory } from '@/utils/academic-graph'

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

  it('跨作者响应、错误分页、非学术实体和损坏拓扑都不能当作有效结果', () => {
    const response = { graph, page: 0, size: 20, totalWorks: 1 }
    expect(parseAuthorGraph(response, query).totalWorks).toBe(1)
    expect(() => parseAuthorGraph(response, { ...query, authorId: 2 })).toThrow('不一致')
    expect(() => parseAuthorGraph({ ...response, page: 1 }, query)).toThrow('不一致')
    expect(() => parseAuthorGraph({ ...response, totalWorks: -1 }, query)).toThrow('分页')
    expect(() => parseAuthorGraph({ ...response, graph: { ...graph, nodes: [root, work(2, 'book')] } }, query)).toThrow('实体类型')
    expect(() => parseAuthorGraph({ ...response, graph: { ...graph, nodes: [root, root] } }, query)).toThrow('ID 重复')
  })
})
