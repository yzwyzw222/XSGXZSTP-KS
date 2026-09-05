import { catalogApi } from '@/services/business'
import type { CatalogCollection, GraphNodeType } from '@/types/api'

export interface GraphEntityOption {
  id: number
  label: string
  description: string
}

const collections: Record<Exclude<GraphNodeType, 'ACHIEVEMENT'>, CatalogCollection> = {
  AUTHOR: 'authors',
  INSTITUTION: 'organizations',
  VENUE: 'venues',
  TOPIC: 'topics',
}

/** 使用受权限保护的目录检索，每次只返回十个可明确选择的规范实体。 */
export async function findGraphEntities(type: GraphNodeType, name: string): Promise<GraphEntityOption[]> {
  const query = name.trim()
  if (!query || query.length > 200) return []
  if (type === 'ACHIEVEMENT') {
    const result = await catalogApi.achievements({ title: query, page: 0, size: 10 })
    return result.items.map((item) => ({
      id: item.id,
      label: item.title,
      description: [item.publicationDate, item.doi, item.primaryVenue].filter(Boolean).join(' · '),
    }))
  }
  const result = await catalogApi.entities(collections[type], query, 0, 10)
  return result.items.map((item) => ({
    id: item.id,
    label: item.displayName,
    description: `${item.externalId || '暂无外部标识'} · ${item.achievementCount} 条成果`,
  }))
}
