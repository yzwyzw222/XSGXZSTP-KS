import type { GraphEdge, GraphNode, GraphResponse } from '@/types/api'

export interface CooperationEvidence {
  edge: GraphEdge
  source: GraphNode
  target: GraphNode
  works: GraphNode[]
  authoredEdges: GraphEdge[]
  incomplete: boolean
}

/** 按作品建立创作索引，只解析后端已有合作边，并交叉核对双方创作证据。 */
export function cooperationEvidence(graph: GraphResponse): CooperationEvidence[] {
  const nodes = new Map(graph.nodes.map(node => [node.id, node]))
  const authorsByWork = new Map<string, Map<string, GraphEdge[]>>()
  for (const edge of graph.edges) {
    if (edge.type !== 'AUTHORED' || nodes.get(edge.source)?.type !== 'AUTHOR'
      || nodes.get(edge.target)?.type !== 'ACHIEVEMENT') continue
    const authors = authorsByWork.get(edge.target) ?? new Map<string, GraphEdge[]>()
    authors.set(edge.source, [...(authors.get(edge.source) ?? []), edge])
    authorsByWork.set(edge.target, authors)
  }
  return graph.edges.filter(edge => edge.type === 'COAUTHORED').flatMap(edge => {
    const source = nodes.get(edge.source)
    const target = nodes.get(edge.target)
    if (source?.type !== 'AUTHOR' || target?.type !== 'AUTHOR' || source.id === target.id) return []
    const raw = edge.properties.sharedWorkIds
    const ids = [...new Set(Array.isArray(raw) ? raw.filter((id): id is string => typeof id === 'string') : [])]
    const works: GraphNode[] = []
    const authoredEdges: GraphEdge[] = []
    for (const id of ids) {
      const work = nodes.get(id)
      const authors = authorsByWork.get(id)
      if (work?.type !== 'ACHIEVEMENT' || !authors?.has(source.id) || !authors.has(target.id)) continue
      works.push(work)
      authoredEdges.push(...authors.get(source.id)!, ...authors.get(target.id)!)
    }
    return [{ edge, source, target, works, authoredEdges, incomplete: works.length === 0 || works.length !== ids.length }]
  })
}

/** 合作视图保留共同作品及双方实际创作边；不生成或推断任何新关系。 */
export function cooperationGraph(graph: GraphResponse, evidence: CooperationEvidence[]): GraphResponse {
  const nodes = new Set<string>()
  const edges = new Set<string>()
  for (const item of evidence) {
    nodes.add(item.source.id); nodes.add(item.target.id)
    item.works.forEach(work => nodes.add(work.id))
    edges.add(item.edge.id)
    item.authoredEdges.forEach(edge => edges.add(edge.id))
  }
  return { ...graph, nodes: graph.nodes.filter(node => nodes.has(node.id)),
    edges: graph.edges.filter(edge => edges.has(edge.id)), rootNodeId: '' }
}

/** 摘要限制画布占用，完整标题由合作作品列表展示。 */
export function cooperationLabel(name: string, evidence?: CooperationEvidence): string {
  const work = evidence?.works[0]
  if (!work) return `${name} · 依据未返回`
  const title = Array.from(work.label)
  const excerpt = title.slice(0, 18).join('') + (title.length > 18 ? '…' : '')
  return `${name} · 共同${evidence.works.length}部${evidence.incomplete ? '（部分）' : ''}\n《${excerpt}》${evidence.works.length > 1 ? '等' : ''}`
}
