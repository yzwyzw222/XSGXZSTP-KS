import type { ElementDefinition } from 'cytoscape'

import type { GraphNode, GraphNodeType, GraphResponse } from '@/types/api'

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
  for (const edge of incoming.edges) {
    if (nodeIds.has(edge.source) && nodeIds.has(edge.target)) {
      edges.set(edge.id, edge)
    }
  }
  return {
    ...incoming,
    nodes: [...nodes.values()],
    edges: [...edges.values()],
    rootNodeId: current.rootNodeId,
    truncated: current.truncated || incoming.truncated || droppedNode || nodes.size >= nodeLimit,
    narrowingSuggestion:
      incoming.narrowingSuggestion
      ?? current.narrowingSuggestion
      ?? (nodes.size >= nodeLimit ? '节点已达到300个上限，请缩小年份、类型或关系范围。' : null),
  }
}

export function toCytoscapeElements(graph: GraphResponse): ElementDefinition[] {
  return [
    ...graph.nodes.map((node) => ({
      data: {
        id: node.id,
        label: node.label,
        nodeType: node.type,
        businessId: node.businessId,
      },
    })),
    ...graph.edges.map((edge) => ({
      data: {
        id: edge.id,
        label: relationshipLabel(edge.type),
        relationshipType: edge.type,
        source: edge.source,
        target: edge.target,
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
    AFFILIATED_WITH: '隶属',
    PUBLISHED_IN: '发表于',
    HAS_TOPIC: '主题',
    CITES: '引用',
  }[type] ?? type
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
