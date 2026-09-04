import { describe, expect, it } from 'vitest'

import type { GraphNode, GraphResponse } from '@/types/api'
import { mergeGraph, nodeTarget, relationshipLabel, toCytoscapeElements } from '@/utils/graph'

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
