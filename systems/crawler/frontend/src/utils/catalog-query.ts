import type { LocationQuery, LocationQueryRaw } from 'vue-router'
import type { AchievementQuery } from '@/services/business'

const textKeys = ['title', 'author', 'organization', 'venue', 'topic'] as const
const idKeys = ['authorId', 'organizationId', 'venueId', 'topicId'] as const

/** URL 只接受目录已知条件，避免详情返回时携带无关参数。 */
export function readCatalogQuery(query: LocationQuery): AchievementQuery {
  const result: AchievementQuery = { page: integer(query.page, 0, 1000000) ?? 0, size: integer(query.size, 1, 100) ?? 20 }
  for (const key of textKeys) result[key] = typeof query[key] === 'string' ? query[key].trim().slice(0, 200) : ''
  for (const key of idKeys) result[key] = integer(query[key], 1, Number.MAX_SAFE_INTEGER)
  result.publicationYear = integer(query.publicationYear, 1000, 9999)
  return result
}

export function catalogRouteQuery(query: AchievementQuery): LocationQueryRaw {
  return Object.fromEntries(Object.entries(query)
    .filter(([key]) => [...textKeys, ...idKeys, 'publicationYear', 'page', 'size'].includes(key))
    .filter(([key, value]) => value !== undefined && value !== '' && !(key === 'page' && value === 0) && !(key === 'size' && value === 20))
    .map(([key, value]) => [key, String(value)]))
}

function integer(value: unknown, min: number, max: number): number | undefined {
  if (typeof value !== 'string' || !/^\d+$/.test(value)) return undefined
  const number = Number(value)
  return Number.isSafeInteger(number) && number >= min && number <= max ? number : undefined
}
