import { z } from 'zod'
import type { ElementDefinition } from 'cytoscape'
import type { AuthorGraphQuery, AuthorGraphResponse, WorkCategory } from '@/services/academic-graph'
import type { GraphNode, GraphResponse } from '@/types/api'
import { toCytoscapeElements } from '@/utils/graph'
import { GraphDataError, parseGraphResponse } from '@/utils/graph-presentation'

export const workCategories: { value: WorkCategory; label: string; color: string }[] = [
  { value: 'PATENT', label: '专利', color: '#bd7b32' },
  { value: 'PAPER', label: '论文', color: '#16856d' },
  { value: 'MASTER_THESIS', label: '指导硕论', color: '#287f9a' },
  { value: 'DOCTORAL_THESIS', label: '指导博论', color: '#9067b5' },
]

export function workCategory(node: GraphNode): WorkCategory | undefined {
  const type = node.properties.achievementType
  if (type === 'patent') return 'PATENT'
  if (type === 'master-thesis') return 'MASTER_THESIS'
  if (type === 'doctoral-thesis') return 'DOCTORAL_THESIS'
  if (['article', 'review', 'preprint', 'proceedings-article'].includes(String(type))) return 'PAPER'
  return undefined
}

export function workDefinition(node: GraphNode) {
  return workCategories.find(item => item.value === workCategory(node))
}

/** 无日期记录始终排在末尾；不把导入时间推断成发表时间。 */
export function workDate(node: GraphNode): string {
  const date = node.properties.publicationDate
  return typeof date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(date) ? date : ''
}

const institutionProperties = z.object({
  institutions: z.array(z.object({ id: z.string().regex(/^[1-9]\d*$/), name: z.string() })).max(100).optional(),
  institutionsTruncated: z.boolean().optional(),
})

/** 只使用本篇成果的机构，兼容旧后端未返回字段的情况。 */
export function workInstitutionNames(node: GraphNode): string {
  const result = institutionProperties.safeParse(node.properties)
  if (!result.success) throw new GraphDataError('成果机构信息无效')
  const institutions = result.data.institutions
  if (institutions === undefined) return '暂未返回'
  if (new Set(institutions.map(item => item.id)).size !== institutions.length) throw new GraphDataError('成果机构标识重复')
  return institutions.map(item => item.name.trim() || `机构 #${item.id}`).join('、') || '未收录'
}

export function timelineGroups(works: GraphNode[]): { year: string; works: GraphNode[] }[] {
  const sorted = [...works].sort((left, right) => {
    const a = workDate(left), b = workDate(right)
    return (a || '99999').localeCompare(b || '99999') || Number(left.businessId) - Number(right.businessId)
  })
  const groups = new Map<string, GraphNode[]>()
  for (const work of sorted) {
    const year = workDate(work).slice(0, 4) || '日期未知'
    if (!groups.has(year)) groups.set(year, [])
    groups.get(year)!.push(work)
  }
  return [...groups].map(([year, items]) => ({ year, works: items }))
}

/** 成果存储编码保持不变，画布使用四种业务实体样式。 */
export function academicElements(graph: GraphResponse): ElementDefinition[] {
  const nodes = new Map(graph.nodes.map(node => [node.id, node]))
  return toCytoscapeElements(graph).map(element => {
    const node = nodes.get(String(element.data.id))
    if (!node) {
      if (element.data.relationshipType === 'COAUTHORED') {
        return { ...element, data: { ...element.data, label: '共同创作', displayColor: '#67949b', displaySize: 2.2 } }
      }
      // 常规关系按需显示标签，为成果题名留出空间，关系数据和点击详情保持完整。
      return ['AUTHORED', 'SUPERVISED'].includes(String(element.data.relationshipType))
        ? { ...element, data: { ...element.data, labelMode: 'interaction' } } : element
    }
    const definition = workDefinition(node)
    return { ...element, data: { ...element.data,
      displayColor: node.type === 'AUTHOR' ? '#1677ef' : definition?.color,
      displaySize: node.id === graph.rootNodeId ? 62 : node.type === 'AUTHOR' ? 42 : 34,
      typeName: node.type === 'AUTHOR' ? '作者' : definition?.label,
    } }
  })
}

/** 小型合作网络分列作者与作品，给长题名及合作连线留下阅读空间。 */
export function cooperationPositions(graph: GraphResponse): Record<string, { x: number; y: number }> | undefined {
  if (graph.nodes.length > 12) return undefined
  const works = graph.nodes.filter(node => node.type === 'ACHIEVEMENT')
  const partners = graph.nodes.filter(node => node.type === 'AUTHOR' && node.id !== graph.rootNodeId)
  if (!works.length || !partners.length) return undefined
  return Object.fromEntries([
    [graph.rootNodeId, { x: -300, y: 0 }],
    ...works.map((node, i) => [node.id, { x: 0, y: works.length === 1 ? -170 : (i - (works.length - 1) / 2) * 220 }]),
    ...partners.map((node, i) => [node.id, { x: 300, y: (i - (partners.length - 1) / 2) * 180 }]),
  ])
}

const responseSchema = z.object({
  graph: z.unknown(), page: z.number().int().min(0).max(1000000),
  size: z.number().int().min(1).max(50), totalWorks: z.number().int().nonnegative().safe(),
})

export function parseAuthorGraph(input: unknown, query: AuthorGraphQuery): AuthorGraphResponse {
  const result = responseSchema.safeParse(input)
  if (!result.success) throw new GraphDataError('作者图谱分页信息无效')
  const graph = parseGraphResponse(result.data.graph)
  const root = graph.nodes.find(node => node.id === graph.rootNodeId)
  if (root?.type !== 'AUTHOR' || root.businessId !== String(query.authorId)
    || result.data.page !== query.page || result.data.size !== query.size) {
    throw new GraphDataError('作者或分页与本次查询不一致')
  }
  if (graph.nodes.some(node => node.type !== 'AUTHOR' && (node.type !== 'ACHIEVEMENT' || !workCategory(node)))) {
    throw new GraphDataError('返回了不支持的学术实体类型')
  }
  graph.nodes.filter(node => node.type === 'ACHIEVEMENT').forEach(workInstitutionNames)
  return { ...result.data, graph }
}
