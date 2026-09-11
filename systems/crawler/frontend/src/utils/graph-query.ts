import { z } from 'zod'

const nodeType = z.enum(['ACHIEVEMENT', 'AUTHOR', 'INSTITUTION', 'VENUE', 'TOPIC'])
const integerText = (minimum: number, maximum: number) => z.string().regex(/^\d+$/)
  .refine((value) => Number.isSafeInteger(Number(value)) && Number(value) >= minimum && Number(value) <= maximum)
const year = z.union([z.literal(''), integerText(1000, 9999)])

export const graphFilterSchema = z.object({
  centerType: nodeType,
  centerId: integerText(1, Number.MAX_SAFE_INTEGER),
  depth: integerText(1, 2),
  nodeLimit: integerText(1, 300),
  publicationYearFrom: year,
  publicationYearTo: year,
  nodeTypes: z.array(nodeType).max(5),
  relationshipTypes: z.array(z.enum(['AUTHORED', 'SUPERVISED', 'PRODUCED_AT', 'AFFILIATED_WITH', 'PUBLISHED_IN', 'HAS_TOPIC', 'CITES'])).max(7),
  achievementTypes: z.string().max(200),
}).refine((value) => !value.publicationYearFrom || !value.publicationYearTo
  || Number(value.publicationYearFrom) <= Number(value.publicationYearTo))

export type GraphFilters = z.infer<typeof graphFilterSchema>
export const savedGraphQuerySchema = z.array(z.object({
  name: z.string().trim().min(1).max(40),
  filters: graphFilterSchema,
})).max(10)
export type SavedGraphQuery = z.infer<typeof savedGraphQuerySchema>[number]

/**
 * 常用查询仅保存在本机当前账号下，避免跨账号泄露检索习惯。
 * 账号标识由调用方从会话 store 传入，本模块不直接依赖状态层。
 */
export function savedQueriesStorageKey(userId: number | null): string | null {
  return userId === null ? null : `aacv-graph-queries-v1:${userId}`
}

export function readSavedQueries(userId: number | null): { queries: SavedGraphQuery[]; corrupted: boolean } {
  const key = savedQueriesStorageKey(userId)
  if (!key) return { queries: [], corrupted: false }
  try {
    const value = localStorage.getItem(key)
    return { queries: value ? savedGraphQuerySchema.parse(JSON.parse(value)) : [], corrupted: false }
  } catch {
    return { queries: [], corrupted: true }
  }
}

export function writeSavedQueries(userId: number | null, queries: SavedGraphQuery[]): boolean {
  const key = savedQueriesStorageKey(userId)
  if (!key) return false
  try {
    localStorage.setItem(key, JSON.stringify(queries))
    return true
  } catch {
    return false
  }
}
