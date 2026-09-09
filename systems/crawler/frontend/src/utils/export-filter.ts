import type { CatalogCollection, CatalogEntity, ExportFilter, PageResponse } from '@/types/api'

export interface CatalogExportFilters {
  title: string
  author: string
  organization: string
  publicationYear?: number
  achievementType: string
  sourceCode: string
  venue: string
  topic: string
}

export type CatalogEntityResolver = (
  collection: CatalogCollection,
  name: string,
) => Promise<PageResponse<CatalogEntity>>

export class ExportFilterResolutionError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'ExportFilterResolutionError'
  }
}

const entityLabels: Record<CatalogCollection, string> = {
  authors: '作者',
  organizations: '机构',
  venues: '期刊',
  topics: '主题',
}

export async function resolveExportFilter(
  filters: CatalogExportFilters,
  resolveEntity: CatalogEntityResolver,
): Promise<ExportFilter> {
  const sourceType = resolveSourceType(filters.sourceCode)
  const [authorId, organizationId, venueId, topicId] = await Promise.all([
    resolveEntityId('authors', filters.author, resolveEntity),
    resolveEntityId('organizations', filters.organization, resolveEntity),
    resolveEntityId('venues', filters.venue, resolveEntity),
    resolveEntityId('topics', filters.topic, resolveEntity),
  ])

  return compact({
    title: normalized(filters.title),
    authorId,
    organizationId,
    publicationYearFrom: filters.publicationYear,
    publicationYearTo: filters.publicationYear,
    achievementType: normalized(filters.achievementType),
    sourceType,
    venueId,
    topicId,
  })
}

async function resolveEntityId(
  collection: CatalogCollection,
  value: string,
  resolveEntity: CatalogEntityResolver,
): Promise<number | undefined> {
  const name = normalized(value)
  if (!name) return undefined

  const page = await resolveEntity(collection, name)
  if (page.totalElements !== 1 || page.items.length !== 1) {
    const label = entityLabels[collection]
    throw new ExportFilterResolutionError(
      `${label}“${name}”匹配到 ${page.totalElements} 条记录，请调整为唯一名称后再导出`,
    )
  }
  return page.items[0]?.id
}

function resolveSourceType(value: string): ExportFilter['sourceType'] {
  const sourceType = normalized(value)?.toUpperCase()
  if (!sourceType) return undefined
  if (sourceType !== 'OPENALEX' && sourceType !== 'CROSSREF') {
    throw new ExportFilterResolutionError('来源代码仅支持 OPENALEX 或 CROSSREF')
  }
  return sourceType
}

function normalized(value: string): string | undefined {
  return value.trim() || undefined
}

function compact(filter: ExportFilter): ExportFilter {
  return Object.fromEntries(
    Object.entries(filter).filter(([, value]) => value !== undefined),
  ) as ExportFilter
}
