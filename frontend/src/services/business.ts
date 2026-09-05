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
  CandidateComparison,
  CrawlFailure,
  CrawlRun,
  CrawlSchedule,
  CrawlTask,
  CrawlTaskParameters,
  DataSource,
  DuplicateCandidate,
  ExportFilter,
  ExportFormat,
  ExportTask,
  GraphEvent,
  GraphMaintenanceRun,
  GraphOutboxStatus,
  FieldOverride,
  GraphNodeType,
  GraphRelationshipType,
  GraphResponse,
  GraphSyncStatus,
  MergeDecision,
  OperationsOverview,
  PageResponse,
  QualityMetric,
  QualityMetricDetail,
  SourceConfigurationInput,
  SourceProbe,
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

export const sourceApi = {
  page: (page = 0, size = 20) =>
    api.get<PageResponse<DataSource>>(withQuery('/api/v1/sources', { page, size })),
  create: (input: SourceConfigurationInput) => api.post<DataSource>('/api/v1/sources', input),
  update: (id: number, input: SourceConfigurationInput) =>
    api.put<DataSource>(`/api/v1/sources/${id}`, input),
  setEnabled: (source: DataSource, enabled: boolean) =>
    api.post<DataSource>(`/api/v1/sources/${source.id}/${enabled ? 'enable' : 'disable'}`, {
      version: source.version,
    }),
  probe: (id: number) => api.post<SourceProbe>(`/api/v1/sources/${id}/probe`),
}

export const crawlApi = {
  tasks: (page = 0, size = 20) =>
    api.get<PageResponse<CrawlTask>>(withQuery('/api/v1/crawl/tasks', { page, size })),
  createTask: (input: { sourceId: number; name: string; parameters: CrawlTaskParameters }) =>
    api.post<CrawlTask>('/api/v1/crawl/tasks', input),
  updateTask: (task: CrawlTask, name: string, parameters: CrawlTaskParameters) =>
    api.put<CrawlTask>(`/api/v1/crawl/tasks/${task.id}`, { name, parameters, version: task.version }),
  trigger: (taskId: number) => api.post<CrawlRun>(`/api/v1/crawl/tasks/${taskId}/trigger`),
  schedule: (taskId: number, localTime: string, timeZone: string, version?: number) =>
    api.put<CrawlSchedule>(`/api/v1/crawl/tasks/${taskId}/schedule`, {
      localTime,
      timeZone,
      version,
    }),
  run: (runId: number) => api.get<CrawlRun>(`/api/v1/crawl/runs/${runId}`),
  failures: (runId: number, page = 0, size = 20) =>
    api.get<PageResponse<CrawlFailure>>(
      withQuery(`/api/v1/crawl/runs/${runId}/failures`, { page, size }),
    ),
  control: (runId: number, action: 'pause' | 'resume' | 'cancel' | 'retry-failures') =>
    api.post<CrawlRun>(`/api/v1/crawl/runs/${runId}/${action}`),
}

export const governanceApi = {
  candidates: (query: {
    entityType?: string
    status?: string
    sourceId?: number
    ruleVersion?: number
    page: number
    size: number
  }) =>
    api.get<PageResponse<DuplicateCandidate>>(
      withQuery('/api/v1/duplicate-candidates', { ...query }),
    ),
  candidate: (id: number) => api.get<DuplicateCandidate>(`/api/v1/duplicate-candidates/${id}`),
  comparison: (id: number) => api.get<CandidateComparison>(`/api/v1/duplicate-candidates/${id}/comparison`),
  accept: (candidate: DuplicateCandidate, canonicalEntityId: number, reason: string) =>
    api.post<MergeDecision>(`/api/v1/duplicate-candidates/${candidate.id}/accept`, {
      canonicalEntityId,
      reason,
      version: candidate.version,
    }),
  reject: (candidate: DuplicateCandidate, reason: string) =>
    api.post<MergeDecision>(`/api/v1/duplicate-candidates/${candidate.id}/reject`, {
      reason,
      version: candidate.version,
    }),
  revertDecision: (decision: MergeDecision, reason: string) =>
    api.post<MergeDecision>(`/api/v1/merge-decisions/${decision.id}/revert`, {
      reason,
      version: decision.version,
    }),
  overrideField: (
    achievementId: number,
    fieldName: string,
    value: unknown,
    reason: string,
    version: number,
  ) =>
    api.post<FieldOverride>(`/api/v1/catalog/achievements/${achievementId}/field-overrides`, {
      fieldName,
      value,
      reason,
      version,
    }),
  revertOverride: (override: FieldOverride, reason: string) =>
    api.post<FieldOverride>(
      `/api/v1/catalog/achievements/${override.achievementId}/field-overrides/${override.revisionId}/revert`,
      { reason, version: override.version },
    ),
}

export const qualityApi = {
  page: (query: {
    sourceId?: number
    runId?: number
    metricCode?: string
    page: number
    size: number
  }) =>
    api.get<PageResponse<QualityMetric>>(withQuery('/api/v1/quality-metrics', { ...query })),
  detail: (id: number, sampleLimit = 20) =>
    api.get<QualityMetricDetail>(
      withQuery(`/api/v1/quality-metrics/${id}`, { sampleLimit }),
    ),
}

export { userApi } from '@/services/users'

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
}

export const graphApi = {
  subgraph: (query: GraphSubgraphQuery) =>
    api.get<GraphResponse>(withQuery('/api/v1/graph/subgraph', {
      centerType: query.centerType,
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
