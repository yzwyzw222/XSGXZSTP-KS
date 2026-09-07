import { describe, expect, it } from 'vitest'
import type { GraphNode, GraphResponse } from '@/types/api'
import { cooperationEvidence, cooperationGraph, cooperationLabel } from './graph-cooperation'
import { toCytoscapeElements } from './graph'

function fixture(): GraphResponse {
  const node = (id: string, type: GraphNode['type']): GraphNode => ({ id, type, businessId: id, label: id, properties: {} })
  return {
    nodes: [node('甲', 'AUTHOR'), node('乙', 'AUTHOR'), node('丙', 'AUTHOR'), node('作品一', 'ACHIEVEMENT'), node('作品二', 'ACHIEVEMENT'), node('单独作品', 'ACHIEVEMENT')],
    edges: [
      ...['甲', '乙', '丙'].map(source => ({ id: `${source}1`, type: 'AUTHORED' as const, source, target: '作品一', properties: {} })),
      ...['甲', '乙'].map(source => ({ id: `${source}2`, type: 'AUTHORED' as const, source, target: '作品二', properties: {} })),
      { id: '单独', type: 'AUTHORED', source: '甲', target: '单独作品', properties: {} },
      { id: '合作', type: 'COAUTHORED', source: '甲', target: '乙', properties: { sharedWorkIds: ['作品一', '作品二', '作品一'] } },
    ],
    rootNodeId: '', truncated: false, narrowingSuggestion: null,
    appliedLimits: { depth: 1, nodeLimit: 300, maxHops: 0 }, syncedAt: null, projectionLagSeconds: null, traceId: 'cooperation-test',
  }
}

describe('合作作品依据', () => {
  it('去重共同作品，聚焦双方实际创作边且不带入单独作品或第三作者', () => {
    const graph = fixture()
    const evidence = cooperationEvidence(graph)
    expect(evidence).toHaveLength(1)
    expect(evidence[0]?.works.map(work => work.id)).toEqual(['作品一', '作品二'])
    expect(evidence[0]?.incomplete).toBe(false)
    const focused = cooperationGraph(graph, evidence)
    expect(focused.nodes.map(node => node.id)).toEqual(['甲', '乙', '作品一', '作品二'])
    expect(focused.edges.map(edge => edge.id)).toEqual(['甲1', '乙1', '甲2', '乙2', '合作'])
    expect(graph.nodes).toHaveLength(6)
  })
  it('缺失、错误类型和只有单方创作的依据明确标记为不完整', () => {
    const graph = fixture()
    graph.edges.at(-1)!.properties.sharedWorkIds = ['作品一', '不存在', '甲', '单独作品']
    const evidence = cooperationEvidence(graph)[0]!
    expect(evidence.works.map(work => work.id)).toEqual(['作品一'])
    expect(evidence.incomplete).toBe(true)
    expect(cooperationLabel('合作', evidence)).toContain('共同1部（部分）')
  })
  it('不存在合作边时不自行推导；空或非法依据不报假作品数', () => {
    const graph = fixture()
    graph.edges = graph.edges.filter(edge => edge.type === 'AUTHORED')
    expect(cooperationEvidence(graph)).toEqual([])
    expect(cooperationEvidence({ ...graph, nodes: [], edges: [] })).toEqual([])
    graph.edges.push({ id: '合作', type: 'COAUTHORED', source: '甲', target: '乙', properties: { sharedWorkIds: null } })
    expect(cooperationLabel('合作', cooperationEvidence(graph)[0])).toBe('合作 · 依据未返回')
    graph.edges.at(-1)!.target = '甲'
    expect(cooperationEvidence(graph)).toEqual([])
  })
  it('Canvas 文案和高亮包含作品依据，即使展示图暂时隐藏作品', () => {
    const graph = fixture()
    graph.typeDefinitions = [{ kind: 'RELATIONSHIP', code: 'COAUTHORED', displayName: '协作', color: '#123456', size: 2, reviewStatus: 'APPROVED', version: 1 }]
    const filtered = { ...graph, nodes: graph.nodes.filter(node => node.type === 'AUTHOR'), edges: graph.edges.filter(edge => edge.type === 'COAUTHORED') }
    const element = toCytoscapeElements(filtered, graph).find(item => item.data.id === '合作')!
    expect(element.data.label).toBe('协作 · 共同2部\n《作品一》等')
    expect(element.data.evidenceIds).toEqual(['作品一', '作品二', '甲1', '乙1', '甲2', '乙2'])
  })
})
