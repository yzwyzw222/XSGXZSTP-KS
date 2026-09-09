import { describe, expect, it } from 'vitest'

import type { GraphResponse } from '@/types/api'
import { parseGraphExtend, parseGraphResponse, toVisGraph } from '@/utils/graph-vis'

function response(): GraphResponse {
  return {
    nodes: [
      { id: 'AUTHOR:1', businessId: '1', type: 'AUTHOR', label: '完整的长名称不能因为画布缩写而丢失', properties: {} },
      { id: 'ACHIEVEMENT:2', businessId: '2', type: 'ACHIEVEMENT', label: '作品', properties: {} },
      { id: 'AUTHOR:3', businessId: '3', type: 'AUTHOR', label: '孤立作者', properties: {} },
    ], edges: [{ id: 'backend-edge-1', type: 'AUTHORED', source: 'AUTHOR:1', target: 'ACHIEVEMENT:2', properties: {} }],
    rootNodeId: '', truncated: false, narrowingSuggestion: null,
    appliedLimits: { depth: 2, nodeLimit: 300, maxHops: 0 }, syncedAt: null, projectionLagSeconds: 0, traceId: 'adapter-test',
  }
}

describe('vis 图谱数据契约', () => {
  it('保持完整名称、后端关系 ID、孤立节点，缺少配置使用默认颜色与尺寸', () => {
    const raw = response()
    const graph = parseGraphResponse(raw)
    const canvas = toVisGraph(graph)
    expect(canvas.nodes).toHaveLength(3)
    expect(canvas.nodes[0]).toMatchObject({ name: raw.nodes[0]!.label, title: raw.nodes[0]!.label,
      label: '完整的长…', type: 'AUTHOR', typeName: '作者', color: '#258ca3', widthConstraint: 50, heightConstraint: 50 })
    expect(raw.nodes[0]!.label).toBe('完整的长名称不能因为画布缩写而丢失')
    expect(canvas.edges[0]).toMatchObject({ id: 'backend-edge-1', from: 'AUTHOR:1', to: 'ACHIEVEMENT:2', label: '创作', directed: true })
    expect(graph.projectionLagSeconds).toBe(0)
    expect(toVisGraph(parseGraphResponse({ ...raw, nodes: [], edges: [] }))).toEqual({ nodes: [], edges: [] })
  })

  it('样式使用类型配置，零值与无向合作的业务语义保持不变', () => {
    const raw = response()
    raw.typeDefinitions = [{ kind: 'NODE', code: 'AUTHOR', displayName: '学者', color: '#123456', size: 0, version: 0, reviewStatus: 'APPROVED' }]
    raw.edges.push({ id: 'co', type: 'COAUTHORED', source: 'AUTHOR:1', target: 'AUTHOR:3', properties: { sharedWorkIds: [] } })
    const canvas = toVisGraph(parseGraphResponse(raw))
    expect(canvas.nodes[0]).toMatchObject({ color: '#123456', typeName: '学者', widthConstraint: 0, heightConstraint: 0 })
    expect(canvas.edges[1]).toMatchObject({ id: 'co', directed: false })
    const missingStyle = parseGraphResponse({ ...raw, typeDefinitions: raw.typeDefinitions.map(type => ({ ...type, color: null, size: undefined })) })
    expect(toVisGraph(missingStyle).nodes[0]).toMatchObject({ color: '#258ca3', widthConstraint: 50 })
  })

  it('安全解析扩展 JSON，允许空对象但拒绝格式错误和非对象', () => {
    const raw = response()
    raw.nodes[0]!.properties = { extend_data: '{"机构":"研究院","作品数":0}', active: false }
    expect(toVisGraph(parseGraphResponse(raw)).nodes[0]!.extend).toMatchObject({ 机构: '研究院', 作品数: 0, active: false })
    expect(parseGraphExtend(null, '节点')).toEqual({})
    for (const input of ['{invalid', '[]', 'null', '42', [], true]) expect(() => parseGraphExtend(input, '节点')).toThrow('图谱数据异常')
    raw.nodes[0]!.properties.extend_data = '{bad'
    expect(() => parseGraphResponse(raw)).toThrow('extend_data 不是有效 JSON')
  })

  it.each([
    ['重复节点', (raw: GraphResponse) => raw.nodes.push(raw.nodes[0]!)],
    ['重复关系', (raw: GraphResponse) => raw.edges.push(raw.edges[0]!)],
    ['缺少端点', (raw: GraphResponse) => { raw.edges[0]!.target = 'missing' }],
    ['空关系 ID', (raw: GraphResponse) => { raw.edges[0]!.id = '' }],
    ['空名称', (raw: GraphResponse) => { raw.nodes[0]!.label = '' }],
    ['超限', (raw: GraphResponse) => { raw.nodes = Array.from({ length: 301 }, () => raw.nodes[0]!) }],
    ['超出精度的业务 ID', (raw: GraphResponse) => { raw.nodes[0]!.businessId = '9007199254740993' }],
    ['缺少中心', (raw: GraphResponse) => { raw.rootNodeId = 'missing' }],
  ])('%s时拒绝整个响应，不能静默丢失数据', (_name, mutate) => {
    const raw = response()
    mutate(raw)
    expect(() => parseGraphResponse(raw)).toThrow('图谱数据异常')
  })

  it('缺少数组、非法配置以及重复类型配置均被报告', () => {
    for (const raw of [null, {}, { ...response(), edges: null }, { ...response(), typeDefinitions: [{ kind: 'NODE', code: 'AUTHOR', size: -1 }] }]) {
      expect(() => parseGraphResponse(raw)).toThrow('图谱数据异常')
    }
    const raw = response()
    const definition = { kind: 'NODE', code: 'AUTHOR', displayName: '作者', color: '#123456', size: 50, version: 0, reviewStatus: 'APPROVED' } as const
    expect(() => parseGraphResponse({ ...raw, typeDefinitions: [definition, definition] })).toThrow('类型配置重复')
  })
})
