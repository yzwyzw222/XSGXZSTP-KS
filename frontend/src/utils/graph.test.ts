import { describe, expect, it } from 'vitest'

import type { GraphNode, GraphResponse } from '@/types/api'
import { filterGraphView, mergeGraph, nodeTarget, relationshipLabel, toCytoscapeElements } from '@/utils/graph'

function response(nodes: GraphNode[], edges: GraphResponse['edges'] = []): GraphResponse {
  return {
    nodes,
    edges,
    rootNodeId: nodes[0]?.id ?? '',
    truncated: false,
    narrowingSuggestion: null,
    appliedLimits: { depth: 1, nodeLimit: 100, maxHops: 0 },
    syncedAt: '2026-09-02T00:00:00Z',
    projectionLagSeconds: 0,
    traceId: 'trace-stage7',
  }
}

function node(id: string, type: GraphNode['type'] = 'ACHIEVEMENT'): GraphNode {
  return { id, businessId: id.replace(/\D/g, '') || '1', type, label: id, properties: {} }
}

describe('图谱前端模型', () => {
  it('Canvas 使用 MySQL 类型名称、颜色、尺寸与审核状态', () => {
    const graph = response([node('a', 'AUTHOR'), node('w')], [{ id: 'e', type: 'AUTHORED', source: 'a', target: 'w', properties: {} }])
    graph.typeDefinitions = [
      { kind: 'NODE', code: 'AUTHOR', displayName: '学者', color: '#123456', size: 52, reviewStatus: 'PENDING', version: 1 },
      { kind: 'RELATIONSHIP', code: 'AUTHORED', displayName: '署名创作', color: '#654321', size: 3, reviewStatus: 'APPROVED', version: 2 },
    ]
    const elements = toCytoscapeElements(graph)
    expect(elements[0]?.data).toMatchObject({ typeName: '学者', displayColor: '#123456', displaySize: 52, reviewStatus: 'PENDING' })
    expect(elements[2]?.data).toMatchObject({ label: '署名创作', displayColor: '#654321', displaySize: 3 })
  })

  it('关系视图只显示后端提供的关系，不从同名作者构造合作', () => {
    const graph = response([node('a', 'AUTHOR'), node('b', 'AUTHOR'), node('w')], [
      { id: 'aw', type: 'AUTHORED', source: 'a', target: 'w', properties: {} },
      { id: 'bw', type: 'AUTHORED', source: 'b', target: 'w', properties: {} },
    ])
    expect(filterGraphView(graph, 'coauthors').nodes).toHaveLength(0)
    expect(filterGraphView(graph, 'authorship').edges).toHaveLength(2)
    graph.edges.push({ id: 'ab', type: 'COAUTHORED', source: 'a', target: 'b', properties: { sharedWorkIds: ['w'] } })
    expect(filterGraphView(graph, 'coauthors').nodes.map(item => item.id)).toEqual(['a', 'b', 'w'])
    expect(filterGraphView(graph, 'coauthors').edges.map(item => item.id)).toEqual(['aw', 'bw', 'ab'])
  })

  it('增量展开合并共同作品依据并去重，不丢失先前合作证据', () => {
    const nodes = [node('a', 'AUTHOR'), node('b', 'AUTHOR'), node('w1')]
    const edge = { id: 'ab', type: 'COAUTHORED' as const, source: 'a', target: 'b', properties: { sharedWorkIds: ['w1'], sharedWorkCount: 1 } }
    const incoming = response([...nodes, node('w2')], [{ ...edge, properties: { sharedWorkIds: ['w1', 'w2'], sharedWorkCount: 2 } }])
    expect(mergeGraph(response(nodes, [edge]), incoming).edges[0]?.properties).toMatchObject({ sharedWorkIds: ['w1', 'w2'], sharedWorkCount: 2 })
  })
  it('合并展开结果时按稳定ID去重并保留初始根节点', () => {
    const current = response([node('n1'), node('n2')], [
      { id: 'e1', type: 'CITES', source: 'n1', target: 'n2', properties: {} },
    ])
    const incoming = response([node('n2'), node('n3')], [
      { id: 'e2', type: 'CITES', source: 'n2', target: 'n3', properties: {} },
    ])

    const merged = mergeGraph(current, incoming)

    expect(merged.nodes.map((item) => item.id)).toEqual(['n1', 'n2', 'n3'])
    expect(merged.edges.map((item) => item.id)).toEqual(['e1', 'e2'])
    expect(merged.rootNodeId).toBe('n1')
  })

  it('达到节点上限时丢弃越界节点及悬空关系并给出收窄提示', () => {
    const current = response([node('n1'), node('n2')])
    const incoming = response([node('n3')], [
      { id: 'e3', type: 'CITES', source: 'n2', target: 'n3', properties: {} },
    ])

    const merged = mergeGraph(current, incoming, 2)

    expect(merged.nodes).toHaveLength(2)
    expect(merged.edges).toHaveLength(0)
    expect(merged.truncated).toBe(true)
    expect(merged.narrowingSuggestion).toContain('上限')
  })

  it('生成Cytoscape元素并只返回受控业务路由', () => {
    const graph = response([node('n1')])

    expect(toCytoscapeElements(graph)[0]?.data).toMatchObject({ id: 'n1', nodeType: 'ACHIEVEMENT' })
    expect(nodeTarget(node('author-2', 'AUTHOR'))).toBe('/catalog/authors')
    expect(relationshipLabel('AUTHORED')).toBe('创作')
  })
})
