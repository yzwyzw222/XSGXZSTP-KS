import { api } from '@/services/api'
import type {
  AchievementDetail,
  AchievementSummary,
  AlertEvent,
  AlertStatus,
  AlertType,
  AnalyticsCollaborationResponse,
  AnalyticsDistributionResponse,
  AnalyticsFilter,
  AnalyticsOverview,
  AnalyticsTrendResponse,
  CatalogCollection,
  CatalogEntity,
  CatalogEntityEvidence,
  ExportFilter,
  ExportFormat,
  ExportTask,
  GraphEvent,
  GraphMaintenanceRun,
  GraphOutboxStatus,
  GraphNodeType,
  GraphRelationshipType,
  GraphResponse,
  GraphSyncStatus,
  OperationsOverview,
  PageResponse,
  AuditLog,
} from '@/types/api'

export type QueryValue = string | number | boolean | null | undefined

export function withQuery(path: string, query: Record<string, QueryValue>): string {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, String(value))
    }
  })
  const encoded = params.toString()
  return encoded ? `${path}?${encoded}` : path
}

export interface AchievementQuery {
  authorId?: number
  organizationId?: number
  venueId?: number
  topicId?: number
  title?: string
  author?: string
  organization?: string
  publicationYear?: number
  achievementType?: string
  sourceCode?: string
  venue?: string
  topic?: string
  page: number
  size: number
}

export const catalogApi = {
  achievements: (query: AchievementQuery) =>
    api.get<PageResponse<AchievementSummary>>(
      withQuery('/api/v1/catalog/achievements', { ...query }),
    ),
  achievement: (id: number) =>
    api.get<AchievementDetail>(`/api/v1/catalog/achievements/${id}`),
  entityEvidence: (collection: 'authors' | 'organizations', id: number) =>
    api.get<CatalogEntityEvidence>(`/api/v1/catalog/${collection}/${id}/evidence`),
  entities: (collection: CatalogCollection, name: string, page: number, size: number) =>
    api.get<PageResponse<CatalogEntity>>(
      withQuery(`/api/v1/catalog/${collection}`, { name, page, size }),
    ),
  relatedAchievements: (collection: CatalogCollection, id: number, page = 0, size = 20) =>
    api.get<PageResponse<AchievementSummary>>(
      withQuery(`/api/v1/catalog/${collection}/${id}/achievements`, { page, size }),
    ),
}

export interface GraphSubgraphQuery {
  centerType: GraphNodeType
  centerId: number
  depth: number
  nodeLimit: number
  relationshipTypes?: GraphRelationshipType[]
  nodeTypes?: GraphNodeType[]
  publicationYearFrom?: number
  publicationYearTo?: number
  achievementTypes?: string[]
  includeCoauthors?: boolean
}

export const graphApi = {
  /** 自动读取受限作者与作品网络，合作依据由后端随结果返回。 */
  overview: () => api.get<GraphResponse>('/api/v1/graph/overview'),
  subgraph: (query: GraphSubgraphQuery) =>
    api.get<GraphResponse>(withQuery('/api/v1/graph/subgraph', {
      centerType: query.centerType,
      includeCoauthors: query.includeCoauthors,
      centerId: query.centerId,
      depth: query.depth,
      nodeLimit: query.nodeLimit,
      relationshipTypes: query.relationshipTypes?.join(','),
      nodeTypes: query.nodeTypes?.join(','),
      publicationYearFrom: query.publicationYearFrom,
      publicationYearTo: query.publicationYearTo,
      achievementTypes: query.achievementTypes?.join(','),
    })),
  path: (query: {
    sourceType: GraphNodeType
    sourceId: number
    targetType: GraphNodeType
    targetId: number
    maxHops: number
  }) => api.get<GraphResponse>(withQuery('/api/v1/graph/path', {
    sourceType: query.sourceType,
    sourceId: query.sourceId,
    targetType: query.targetType,
    targetId: query.targetId,
    maxHops: query.maxHops,
  })),
  syncStatus: () => api.get<GraphSyncStatus>('/api/v1/graph/sync-status'),
}

export const analyticsApi = {
  overview: (filters: AnalyticsFilter) =>
    api.get<AnalyticsOverview>(withQuery('/api/v1/analytics/overview', { ...filters })),
  trends: (filters: AnalyticsFilter) =>
    api.get<AnalyticsTrendResponse>(withQuery('/api/v1/analytics/trends', { ...filters })),
  distributions: (filters: AnalyticsFilter) =>
    api.get<AnalyticsDistributionResponse>(
      withQuery('/api/v1/analytics/distributions', { ...filters }),
    ),
  collaboration: (filters: AnalyticsFilter, limit = 20) =>
    api.get<AnalyticsCollaborationResponse>(
      withQuery('/api/v1/analytics/collaboration', { ...filters, limit }),
    ),
}

export const exportApi = {
  create: (format: ExportFormat, filters: ExportFilter) =>
    api.post<ExportTask>('/api/v1/exports', { format, filters }),
  get: (exportId: string) => api.get<ExportTask>(`/api/v1/exports/${exportId}`),
  download: (task: ExportTask) =>
    api.get<Blob>(
      withQuery(`/api/v1/exports/${task.id}/download`, { token: task.downloadToken }),
      { responseType: 'blob' },
    ),
}

export const operationsApi = {
  overview: () => api.get<OperationsOverview>('/api/v1/operations/overview'),
  alerts: (status: AlertStatus | undefined, type: AlertType | undefined, page = 0, size = 20) =>
    api.get<PageResponse<AlertEvent>>(
      withQuery('/api/v1/operations/alerts', { status, type, page, size }),
    ),
  acknowledgeAlert: (alert: AlertEvent, reason: string) =>
    api.post<AlertEvent>(`/api/v1/operations/alerts/${alert.id}/acknowledge`, {
      reason,
      version: alert.version,
    }),
  graphEvents: (status: GraphOutboxStatus | undefined, page = 0, size = 20) =>
    api.get<PageResponse<GraphEvent>>(
      withQuery('/api/v1/operations/graph-events', { status, page, size }),
    ),
  replayGraphEvent: (eventId: string) =>
    api.post<GraphEvent>(`/api/v1/operations/graph-events/${eventId}/replay`),
  maintenanceRuns: (page = 0, size = 20) =>
    api.get<PageResponse<GraphMaintenanceRun>>(
      withQuery('/api/v1/operations/graph-maintenance/runs', { page, size }),
    ),
  startBackfill: () =>
    api.post<GraphMaintenanceRun>('/api/v1/operations/graph-maintenance/backfill'),
  startReconciliation: () =>
    api.post<GraphMaintenanceRun>('/api/v1/operations/graph-maintenance/reconcile'),
  startRebuild: () =>
    api.post<GraphMaintenanceRun>('/api/v1/operations/graph-maintenance/rebuild', {
      confirmation: 'REBUILD_AACV_MANAGED_GRAPH',
    }),
  audits: (page = 0, size = 20) =>
    api.get<PageResponse<AuditLog>>(
      withQuery('/api/v1/operations/audits', { page, size }),
    ),
}
