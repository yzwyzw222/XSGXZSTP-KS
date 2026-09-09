import type { GraphResponse } from '@/types/api'
import { cooperationEvidence, cooperationGraph } from '@/utils/graph-cooperation'

/** 只筛选已读取的网络，保留命中名称的一跳邻居；合作证据仍来自原响应。 */
export function filterOverview(graph: GraphResponse, keyword: string, nodeType: string, relationship: string, allTypes = false): GraphResponse {
  if (relationship === 'COAUTHORED') {
    const term = keyword.trim().toLowerCase()
    const evidence = cooperationEvidence(graph).filter(item =>
      [item.source, item.target, ...item.works].some(node => `${node.label} ${node.businessId}`.toLowerCase().includes(term)))
    return cooperationGraph(graph, evidence)
  }
  const domainNodes = graph.nodes.filter(node => allTypes || ['AUTHOR', 'ACHIEVEMENT'].includes(node.type))
  const domainIds = new Set(domainNodes.map(node => node.id))
  let edges = graph.edges.filter(edge => (allTypes || ['AUTHORED', 'COAUTHORED'].includes(edge.type))
    && (!relationship || edge.type === relationship) && domainIds.has(edge.source) && domainIds.has(edge.target))
  const term = keyword.trim().toLowerCase()
  const matches = new Set(domainNodes.filter(node => `${node.label} ${node.businessId}`.toLowerCase().includes(term)).map(node => node.id))
  const neighborhood = new Set(matches)
  if (term) edges.forEach(edge => {
    if (matches.has(edge.source) || matches.has(edge.target)) { neighborhood.add(edge.source); neighborhood.add(edge.target) }
  })
  const endpoints = new Set(edges.flatMap(edge => [edge.source, edge.target]))
  const nodes = domainNodes.filter(node => (!nodeType || node.type === nodeType)
    && (!term || neighborhood.has(node.id)) && (!relationship || endpoints.has(node.id)))
  const ids = new Set(nodes.map(node => node.id))
  edges = edges.filter(edge => ids.has(edge.source) && ids.has(edge.target))
  return { ...graph, nodes, edges }
}
