import { describe, expect, it } from 'vitest'
import { filterOverview } from './graph-overview'
import type { GraphResponse } from '@/types/api'

const graph: GraphResponse = {
  nodes: [
    { id: 'a', type: 'AUTHOR', businessId: '1', label: '作者甲', properties: {} },
    { id: 'b', type: 'AUTHOR', businessId: '2', label: '作者乙', properties: {} },
    { id: 'w', type: 'ACHIEVEMENT', businessId: '3', label: '作品', properties: {} },
    { id: 'i', type: 'INSTITUTION', businessId: '4', label: '机构', properties: {} },
    { id: 'solo', type: 'AUTHOR', businessId: '5', label: '独立作者', properties: {} },
  ],
  edges: [
    { id: 'aw', type: 'AUTHORED', source: 'a', target: 'w', properties: {} },
    { id: 'bw', type: 'AUTHORED', source: 'b', target: 'w', properties: {} },
    { id: 'ab', type: 'COAUTHORED', source: 'a', target: 'b', properties: { sharedWorkIds: ['w'] } },
    { id: 'bad', type: 'AUTHORED', source: 'missing', target: 'w', properties: {} },
  ], rootNodeId: '', truncated: false, narrowingSuggestion: null,
  appliedLimits: { depth: 1, nodeLimit: 300, maxHops: 0 }, syncedAt: null, projectionLagSeconds: null, traceId: 'test',
}
describe('概览筛选', () => {
  it('保留作者作品和孤立作者，排除非领域节点及悬空边', () => {
    const result = filterOverview(graph, '', '', '')
    expect(result.nodes.map(node => node.id)).toEqual(['a', 'b', 'w', 'solo'])
    expect(result.edges).toHaveLength(3)
  })
  it('搜索保留一跳关系，未匹配时返回空结果', () => {
    expect(filterOverview(graph, ' 作品 ', '', '').nodes).toHaveLength(3)
    expect(filterOverview(graph, '不存在', '', '').nodes).toEqual([])
    expect(filterOverview(graph, '5', '', '').nodes.map(node => node.id)).toEqual(['solo'])
  })
  it('关系与类型筛选不重新计算合作或丢失证据', () => {
    const result = filterOverview(graph, '', 'AUTHOR', 'COAUTHORED')
    expect(result.nodes.map(node => node.id)).toEqual(['a', 'b', 'w'])
    expect(result.edges.find(edge => edge.type === 'COAUTHORED')?.properties.sharedWorkIds).toEqual(['w'])
    expect(filterOverview(graph, '', 'ACHIEVEMENT', '').edges).toEqual([])
    expect(filterOverview(graph, '作品', '', 'COAUTHORED').nodes.map(node => node.id)).toEqual(['a', 'b', 'w'])
    expect(graph.nodes).toHaveLength(5)
  })
  it('空网络可以反复筛选', () => {
    expect(filterOverview({ ...graph, nodes: [], edges: [] }, '', '', '').nodes).toEqual([])
  })
})
