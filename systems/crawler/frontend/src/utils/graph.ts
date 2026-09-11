import type { ElementDefinition } from 'cytoscape'

import type { GraphNode, GraphNodeType, GraphResponse, GraphTypeDefinition } from '@/types/api'
import { cooperationEvidence, cooperationGraph, cooperationLabel } from '@/utils/graph-cooperation'

export const GRAPH_NODE_LIMIT = 300

export function mergeGraph(
  current: GraphResponse | null,
  incoming: GraphResponse,
  nodeLimit = GRAPH_NODE_LIMIT,
): GraphResponse {
  if (!current) {
    return limitGraph(incoming, nodeLimit)
  }

  const nodes = new Map(current.nodes.map((node) => [node.id, node]))
  let droppedNode = false
  for (const node of incoming.nodes) {
    if (nodes.has(node.id) || nodes.size < nodeLimit) {
      nodes.set(node.id, node)
    } else {
      droppedNode = true
    }
  }
  const nodeIds = new Set(nodes.keys())
  const edges = new Map(current.edges.map((edge) => [edge.id, edge]))
  let coauthorCount = current.edges.filter(edge => edge.type === 'COAUTHORED').length
  let droppedCoauthor = false
  for (const edge of incoming.edges) {
    if (nodeIds.has(edge.source) && nodeIds.has(edge.target)) {
      const previous = edges.get(edge.id)
      if (edge.type === 'COAUTHORED') {
        if (!previous && coauthorCount >= 1000) { droppedCoauthor = true; continue }
        const sharedWorkIds = [...new Set([
          ...(Array.isArray(previous?.properties.sharedWorkIds) ? previous.properties.sharedWorkIds : []),
          ...(Array.isArray(edge.properties.sharedWorkIds) ? edge.properties.sharedWorkIds : []),
        ])].filter(id => typeof id === 'string' && nodeIds.has(id))
        if (!sharedWorkIds.length) continue
        edges.set(edge.id, { ...edge, properties: { ...edge.properties, sharedWorkIds, sharedWorkCount: sharedWorkIds.length } })
        if (!previous) coauthorCount++
      } else edges.set(edge.id, edge)
    }
  }
  return {
    ...incoming,
    nodes: [...nodes.values()],
    edges: [...edges.values()],
    rootNodeId: current.rootNodeId,
    truncated: current.truncated || incoming.truncated || droppedNode || droppedCoauthor || nodes.size >= nodeLimit,
    narrowingSuggestion:
      (droppedCoauthor ? '合作关系达到1000条上限，请缩小查询范围。' : incoming.narrowingSuggestion)
      ?? current.narrowingSuggestion
      ?? (nodes.size >= nodeLimit ? '节点已达到300个上限，请缩小年份、类型或关系范围。' : null),
  }
}

export function toCytoscapeElements(graph: GraphResponse, evidenceGraph = graph): ElementDefinition[] {
  const evidence = new Map(cooperationEvidence(evidenceGraph).map(item => [item.edge.id, item]))
  return [
    ...graph.nodes.map((node) => ({
      data: {
        id: node.id,
        label: node.label,
        nodeType: node.type,
        businessId: node.businessId,
        ...elementStyle(graph, 'NODE', node.type),
      },
    })),
    ...graph.edges.map((edge) => ({
      data: {
        id: edge.id,
        label: edge.type === 'COAUTHORED'
          ? cooperationLabel(typeDefinition(graph, 'RELATIONSHIP', edge.type)?.displayName ?? relationshipLabel(edge.type), evidence.get(edge.id))
          : typeDefinition(graph, 'RELATIONSHIP', edge.type)?.displayName ?? relationshipLabel(edge.type),
        evidenceIds: evidence.has(edge.id) ? [
          ...evidence.get(edge.id)!.works.map(work => work.id),
          ...evidence.get(edge.id)!.authoredEdges.map(authored => authored.id),
        ] : [],
        relationshipType: edge.type,
        source: edge.source,
        target: edge.target,
        ...elementStyle(graph, 'RELATIONSHIP', edge.type),
      },
    })),
  ]
}

export function nodeTarget(node: GraphNode): string | null {
  if (node.type === 'ACHIEVEMENT') {
    return `/catalog/achievements/${encodeURIComponent(node.businessId)}`
  }
  const collections: Partial<Record<GraphNodeType, string>> = {
    AUTHOR: 'authors',
    INSTITUTION: 'organizations',
    VENUE: 'venues',
    TOPIC: 'topics',
  }
  const collection = collections[node.type]
  return collection ? `/catalog/${collection}` : null
}

export function relationshipLabel(type: string): string {
  return {
    AUTHORED: '创作',
    SUPERVISED: '指导',
    PRODUCED_AT: '所属机构',
    AFFILIATED_WITH: '隶属',
    PUBLISHED_IN: '发表于',
    HAS_TOPIC: '主题',
    CITES: '引用',
    COAUTHORED: '合作',
  }[type] ?? type
}

export function typeDefinition(graph: GraphResponse | null, kind: GraphTypeDefinition['kind'], code: string) {
  return graph?.typeDefinitions?.find(value => value.kind === kind && value.code === code)
}

export function reviewStatusLabel(status?: string): string {
  return { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }[status ?? ''] ?? '--'
}

/** 旧响应可以沿用主题默认值；新接口的类型配置直接绑定到 Canvas 元素。 */
function elementStyle(graph: GraphResponse, kind: GraphTypeDefinition['kind'], code: string) {
  const definition = typeDefinition(graph, kind, code)
  return definition ? { displayColor: definition.color, displaySize: definition.size,
    typeName: definition.displayName, reviewStatus: definition.reviewStatus } : {}
}

/** 只切换已读取图的展示范围，不在浏览器内推断或生成合作关系。 */
export function filterGraphView(graph: GraphResponse, mode: 'all' | 'authorship' | 'coauthors'): GraphResponse {
  if (mode === 'all') return graph
  if (mode === 'coauthors') return cooperationGraph(graph, cooperationEvidence(graph))
  const edges = graph.edges.filter(edge => edge.type === 'AUTHORED')
  const ids = new Set(edges.flatMap(edge => [edge.source, edge.target]))
  const nodes = graph.nodes.filter(node => ids.has(node.id))
  return { ...graph, nodes, edges, rootNodeId: ids.has(graph.rootNodeId) ? graph.rootNodeId : nodes[0]?.id ?? '' }
}

function limitGraph(graph: GraphResponse, nodeLimit: number): GraphResponse {
  if (graph.nodes.length <= nodeLimit) {
    return graph
  }
  const nodes = graph.nodes.slice(0, nodeLimit)
  const nodeIds = new Set(nodes.map((node) => node.id))
  return {
    ...graph,
    nodes,
    edges: graph.edges.filter((edge) => nodeIds.has(edge.source) && nodeIds.has(edge.target)),
    truncated: true,
    narrowingSuggestion: graph.narrowingSuggestion ?? '节点已达到300个上限，请缩小年份、类型或关系范围。',
  }
}
