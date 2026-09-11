import { z } from 'zod'

import type { GraphResponse, GraphTypeDefinition } from '@/types/api'
import { relationshipLabel } from '@/utils/graph'

const nodeTypes = ['AUTHOR', 'ACHIEVEMENT', 'INSTITUTION', 'VENUE', 'TOPIC'] as const
const edgeTypes = ['AUTHORED', 'SUPERVISED', 'PRODUCED_AT', 'COAUTHORED', 'AFFILIATED_WITH', 'PUBLISHED_IN', 'HAS_TOPIC', 'CITES'] as const
const identifier = z.string().min(1).refine(value => value.trim() === value, '标识不能包含首尾空白')
const properties = z.record(z.unknown()).default({})
const definitionSchema = z.object({
  kind: z.enum(['NODE', 'RELATIONSHIP']), code: z.enum([...nodeTypes, ...edgeTypes]),
  displayName: z.string().min(1), color: z.string().regex(/^#[\da-f]{6}$/i).nullish(),
  size: z.number().finite().nonnegative().nullish(), reviewStatus: z.enum(['PENDING', 'APPROVED', 'REJECTED']),
  version: z.number().int().nonnegative(),
})
const responseSchema = z.object({
  nodes: z.array(z.object({
    id: identifier, businessId: z.string().regex(/^[1-9]\d*$/), type: z.enum(nodeTypes),
    label: z.string().refine(value => value.trim().length > 0, '名称不能为空'), properties, extend_data: z.unknown().optional(),
  })).max(300),
  edges: z.array(z.object({
    id: identifier, type: z.enum(edgeTypes), source: identifier, target: identifier, properties,
  })),
  typeDefinitions: z.array(definitionSchema).nullish().transform(value => value ?? []),
  rootNodeId: z.string(), truncated: z.boolean(),
  narrowingSuggestion: z.string().nullable().default(null),
  appliedLimits: z.object({ depth: z.number().int().min(0).max(2), nodeLimit: z.number().int().min(1).max(300), maxHops: z.number().int().nonnegative() }),
  syncedAt: z.string().nullable().default(null), projectionLagSeconds: z.number().finite().nonnegative().nullable().default(null),
  traceId: z.string().default(''),
})

export class GraphDataError extends Error {
  constructor(message: string) { super(`图谱数据异常：${message}`); this.name = 'GraphDataError' }
}

/** 扩展字段必须是对象；只报告字段位置，不把业务原文写入错误消息。 */
export function parseGraphExtend(value: unknown, location: string): Record<string, unknown> {
  if (value === undefined || value === null) return {}
  let parsed: unknown = value
  if (typeof value === 'string') {
    try { parsed = JSON.parse(value) }
    catch { throw new GraphDataError(`${location} 的 extend_data 不是有效 JSON`) }
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new GraphDataError(`${location} 的 extend_data 必须是对象`)
  }
  return parsed as Record<string, unknown>
}

/** 对整个响应先校验后接收，禁止用去重或过滤掩盖损坏的数据。 */
export function parseGraphResponse(input: unknown): GraphResponse {
  const result = responseSchema.safeParse(input)
  if (!result.success) {
    throw new GraphDataError(`字段 ${result.error.issues.map(issue => issue.path.join('.')).join('、')} 格式不合法`)
  }
  const graph = result.data
  const nodeIds = new Set<string>()
  for (const [index, node] of graph.nodes.entries()) {
    if (nodeIds.has(node.id)) throw new GraphDataError(`nodes.${index} 的 ID 重复`)
    nodeIds.add(node.id)
    if (!Number.isSafeInteger(Number(node.businessId))) throw new GraphDataError(`nodes.${index} 的业务 ID 超出安全整数范围`)
    const raw = node.extend_data ?? node.properties.extend_data
    if (raw !== undefined) node.properties = { ...node.properties, extend_data: parseGraphExtend(raw, `nodes.${index}`) }
  }
  const edgeIds = new Set<string>()
  for (const [index, edge] of graph.edges.entries()) {
    if (edgeIds.has(edge.id)) throw new GraphDataError(`edges.${index} 的 ID 重复`)
    edgeIds.add(edge.id)
    if (!nodeIds.has(edge.source) || !nodeIds.has(edge.target)) throw new GraphDataError(`edges.${index} 缺少端点`)
  }
  const definitionIds = new Set<string>()
  for (const definition of graph.typeDefinitions) {
    const key = `${definition.kind}:${definition.code}`
    if (definitionIds.has(key)) throw new GraphDataError('类型配置重复')
    if ((definition.kind === 'NODE') !== nodeTypes.some(type => type === definition.code)) throw new GraphDataError('类型配置与种类不匹配')
    definitionIds.add(key)
  }
  if (graph.rootNodeId && graph.nodes.length && !nodeIds.has(graph.rootNodeId)) throw new GraphDataError('中心节点未返回')
  return { ...graph, typeDefinitions: graph.typeDefinitions.map(definition => ({ ...definition,
    color: definition.color ?? (definition.kind === 'NODE' ? nodeDefaults[definition.code as keyof typeof nodeDefaults].color : '#7690a8'),
    size: definition.size ?? (definition.kind === 'NODE' ? 50 : 1.5),
  })) }
}

const nodeDefaults = {
  AUTHOR: { name: '作者', color: '#258ca3', size: 50 },
  ACHIEVEMENT: { name: '作品', color: '#2363b8', size: 50 },
  INSTITUTION: { name: '机构', color: '#ba7b35', size: 50 },
  VENUE: { name: '期刊', color: '#8365b6', size: 50 },
  TOPIC: { name: '主题', color: '#278566', size: 50 },
} as const

/** 补齐旧响应未提供的类型样式；配置存在时保留合法的零值。 */
export function graphDefinitions(graph: GraphResponse | null): GraphTypeDefinition[] {
  const defaults: GraphTypeDefinition[] = [
    ...nodeTypes.map(code => ({ kind: 'NODE' as const, code, displayName: nodeDefaults[code].name,
      color: nodeDefaults[code].color, size: nodeDefaults[code].size, reviewStatus: 'PENDING' as const, version: 0 })),
    ...edgeTypes.map(code => ({ kind: 'RELATIONSHIP' as const, code, displayName: relationshipLabel(code),
      color: '#7690a8', size: 1.5, reviewStatus: 'PENDING' as const, version: 0 })),
  ]
  return defaults.map(value => graph?.typeDefinitions?.find(item => item.kind === value.kind && item.code === value.code) ?? value)
}

