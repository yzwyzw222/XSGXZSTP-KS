export type RoleCode = 'ADMIN' | 'DATA_OPERATOR' | 'RESEARCHER'

export type Permission =
  | 'ACCOUNT_SELF_READ'
  | 'USER_LIST'
  | 'USER_CREATE'
  | 'USER_UPDATE'
  | 'USER_ENABLE'
  | 'USER_DISABLE'
  | 'USER_PASSWORD_RESET'
  | 'USER_ROLE_CHANGE'
  | 'AUDIT_READ'
  | 'SOURCE_READ'
  | 'SOURCE_MANAGE'
  | 'SOURCE_PROBE'
  | 'CRAWL_TASK_READ'
  | 'CRAWL_TASK_CREATE'
  | 'CRAWL_TASK_UPDATE'
  | 'CRAWL_TASK_CONTROL'
  | 'CRAWL_SCHEDULE_MANAGE'
  | 'CRAWL_RUN_READ'
  | 'GOVERNANCE_READ'
  | 'GOVERNANCE_MANAGE'
  | 'CATALOG_READ'
  | 'GRAPH_READ'
  | 'GRAPH_SYNC_READ'
  | 'GRAPH_SYNC_MANAGE'
  | 'ANALYTICS_READ'
  | 'EXPORT_CREATE'
  | 'EXPORT_READ'
  | 'OPERATIONS_READ'
  | 'ALERT_MANAGE'

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface CurrentUser {
  id: number
  username: string
  roles: RoleCode[]
  permissions: Permission[]
}

export interface CsrfToken {
  headerName: string
  parameterName: string
  token: string
}

export interface ProblemDetails {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  code?: string
  errorCode?: string
  traceId?: string
  fieldErrors?: Record<string, string> | Array<{ field: string; message: string }>
}

export interface UserProfile {
  realName?: string | null
  email?: string | null
  phone?: string | null
  organization?: string | null
  department?: string | null
  remark?: string | null
}

export interface UserStatistics {
  totalUsers: number
  admin: number
  dataOperator: number
  researcher: number
}

export interface UserAccount extends UserProfile {
  id: number
  username: string
  status: 'ACTIVE' | 'DISABLED' | 'PASSWORD_RESET_REQUIRED'
  version: number
  credentialsChangedAt: string
  createdAt: string
  updatedAt: string
  roles: RoleCode[]
}

export interface SourceConfigurationInput {
  sourceType?: 'OPENALEX' | 'CROSSREF'
  requestsPerSecond: number
  maxConcurrency: number
  connectTimeoutSeconds: number
  responseTimeoutSeconds: number
  maxRetries: number
  maxResponseBytes: number
  complianceNote: string
  version?: number
}

export interface DataSource extends Required<Omit<SourceConfigurationInput, 'version'>> {
  id: number
  sourceCode: string
  baseUri: string
  enabled: boolean
  lastSuccessAt: string | null
  lastFailureAt: string | null
  consecutiveFailures: number
  version: number
}

export interface SourceProbe {
  reachable: boolean
  statusCode: number | null
  errorCategory: string | null
  rateLimitSummary: Record<string, string>
  checkedAt: string
}

export interface AchievementSummary {
  id: number
  title: string
  doi: string | null
  achievementType: string | null
  publicationDate: string | null
  primaryVenue: string | null
  authors: string[]
  topics: string[]
}

export interface AchievementDetail {
  summary: AchievementSummary
  language: string | null
  abstractText: string | null
  authorshipsMayBeIncomplete: boolean
  authorships: Array<{
    authorId: number
    openAlexId: string | null
    orcid: string | null
    displayName: string
    position: number
    organizations: Array<{ id: number; openAlexId: string | null; displayName: string }>
  }>
  referencedWorkIds: string[]
  sources: Array<{
    sourceRecordId: number
    rawRecordId: number
    sourceCode: string
    externalRecordId: string
    sourceUrl: string | null
    firstSeenAt: string
    lastSeenAt: string
    parserVersion: string
    scholarlyMetadata?: ScholarlyMetadata | null
  }>
  fields: Array<{
    fieldName: string
    sourceCode: string | null
    rawRecordId: number | null
    manualOverride: boolean
  }>
}

export interface ScholarlyMetadata {
  observedAt: string
  citedByCount: number | null
  retracted: boolean | null
  openAccess: boolean | null
  openAccessStatus: string | null
  versionRelations: Array<{ relationType: string; targetDoi: string }>
}

export type CatalogCollection = 'authors' | 'organizations' | 'venues' | 'topics'

export interface CatalogEntity {
  id: number
  externalId: string | null
  displayName: string
  entityType: string
  achievementCount: number
}

export interface CrawlTaskParameters {
  publicationDateFrom: string | null
  publicationDateTo: string | null
  keyword: string | null
  authorIds: string[]
  institutionIds: string[]
  dois: string[]
  orcids: string[]
  rorIds: string[]
  updatedFrom: string | null
  updatedUntil: string | null
  maxPages: number
  maxRecords: number
}

export interface CrawlTask {
  id: number
  sourceId: number
  name: string
  parameterVersion: number
  parameters: CrawlTaskParameters
  enabled: boolean
  version: number
  createdAt: string
  updatedAt: string
}

export interface CrawlRun {
  id: number
  taskId: number
  runNumber: string
  triggerType: string
  parentRunId: number | null
  status: string
  batchJobExecutionId: number | null
  readCount: number
  parsedCount: number
  createdCount: number
  updatedCount: number
  duplicateCount: number
  failureCount: number
  requestCount: number
  checkpoint: string | null
  startedAt: string | null
  finishedAt: string | null
  completionReason?: string | null
  deferredUntil?: string | null
  quotaDeferrals?: number
}

export interface CrawlSchedule {
  taskId: number
  localTime: string
  timeZone: string
  nextFireAt: string
  version: number
  incrementalMode: string
}

export interface CrawlFailure {
  id: number
  runId: number
  rawRecordId: number | null
  externalRecordId: string | null
  failureStage: string
  errorCategory: string
  safeMessage: string
  retryable: boolean
  attemptCount: number
  resolved: boolean
  evidenceHash: string
  createdAt: string
  updatedAt: string
}

export interface DuplicateCandidate {
  id: number
  entityType: string
  leftEntityId: number
  rightEntityId: number
  matchBasis: string
  evidence: Record<string, unknown>
  status: string
  sourceId: number | null
  ruleVersion: number
  version: number
  createdAt: string
  updatedAt: string
}

export interface CatalogEntityEvidence {
  entityId: number
  entityType: 'AUTHOR' | 'ORGANIZATION'
  names: { displayName: string; sourceCode: string; firstObservedAt: string; lastObservedAt: string }[]
  affiliations: {
    organizationId: number; displayName: string | null; firstPublicationYear: number | null
    lastPublicationYear: number | null; achievementCount: number; datedAchievementCount: number
  }[]
  namesTruncated: boolean
  affiliationsTruncated: boolean
}

export interface CandidateComparison {
  candidateId: number
  candidateVersion: number
  entityType: string
  leftEntityId: number
  rightEntityId: number
  left: Record<string, unknown>
  right: Record<string, unknown>
  explicitVersionRelation: boolean
}

export interface MergeDecision {
  id: number
  candidateId: number
  decision: string
  canonicalEntityId: number | null
  revisionId: number
  actorUserId: number
  reason: string
  version: number
  decidedAt: string
}

export interface FieldOverride {
  id: number
  achievementId: number
  fieldName: string
  value: unknown
  revisionId: number
  actorUserId: number
  reason: string
  active: boolean
  version: number
  createdAt: string
  updatedAt: string
}

export interface QualityMetric {
  id: number
  sourceId: number
  taskId: number
  runId: number
  metricCode: string
  numerator: number
  denominator: number
  metricValue: number
  measuredAt: string
  version: number
}

export interface QualityMetricDetail {
  metric: QualityMetric
  samples: Array<{
    id: number
    rawRecordId: number
    externalRecordId: string
    evidence: Record<string, unknown>
    createdAt: string
  }>
}

export type GraphNodeType = 'ACHIEVEMENT' | 'AUTHOR' | 'INSTITUTION' | 'VENUE' | 'TOPIC'

export type GraphRelationshipType =
  | 'AUTHORED'
  | 'AFFILIATED_WITH'
  | 'PUBLISHED_IN'
  | 'HAS_TOPIC'
  | 'CITES'

export interface GraphNode {
  id: string
  businessId: string
  type: GraphNodeType
  label: string
  properties: Record<string, unknown>
}

export interface GraphEdge {
  id: string
  type: GraphRelationshipType
  source: string
  target: string
  properties: Record<string, unknown>
}

export interface GraphResponse {
  nodes: GraphNode[]
  edges: GraphEdge[]
  rootNodeId: string
  truncated: boolean
  narrowingSuggestion: string | null
  appliedLimits: {
    depth: number
    nodeLimit: number
    maxHops: number
  }
  syncedAt: string | null
  projectionLagSeconds: number | null
  traceId: string
}

export interface GraphSyncStatus {
  neo4jAvailable: boolean
  schemaVersion: number | null
  pendingCount: number
  processingCount: number
  deadCount: number
  oldestPendingAgeSeconds: number | null
  lastSucceededAt: string | null
  lagThresholdExceeded: boolean
  rebuildInProgress: boolean
}

export interface AnalyticsFilter {
  publicationYearFrom?: number
  publicationYearTo?: number
  achievementType?: string
  sourceType?: 'OPENALEX' | 'CROSSREF'
  organizationId?: number
  topicId?: number
}

export interface AnalyticsScope {
  source: 'MYSQL'
  filters: AnalyticsFilter
}

export interface AnalyticsOverview {
  achievementCount: number
  authorCount: number
  organizationCount: number
  sourceCount: number
  scope: AnalyticsScope
  updatedAt: string
  coverage?: AnalyticsCoverage | null
}

export interface AnalyticsCoverage {
  withDoiCount: number
  withPublicationYearCount: number
  withAbstractCount: number
  withCitationCount: number
  withOpenAccessStatusCount: number
  withRetractionStatusCount: number
  authorshipsMayBeIncompleteCount: number
}

export interface AnalyticsTrendItem {
  publicationYear: number
  achievementCount: number
}

export interface AnalyticsTrendResponse {
  items: AnalyticsTrendItem[]
  scope: AnalyticsScope
  updatedAt: string
}

export interface AnalyticsDistributionItem {
  key: string
  label: string
  achievementCount: number
}

export interface AnalyticsDistributionResponse {
  achievementTypes: AnalyticsDistributionItem[]
  sources: AnalyticsDistributionItem[]
  organizations: AnalyticsDistributionItem[]
  topics: AnalyticsDistributionItem[]
  scope: AnalyticsScope
  updatedAt: string
}

export interface AnalyticsCollaborationItem {
  leftId: number
  leftLabel: string
  rightId: number
  rightLabel: string
  sharedAchievementCount: number
}

export interface AnalyticsCollaborationResponse {
  authors: AnalyticsCollaborationItem[]
  organizations: AnalyticsCollaborationItem[]
  scope: AnalyticsScope
  updatedAt: string
}

export type ExportFormat = 'CSV' | 'JSON'

export type ExportStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'EXPIRED'

export interface ExportFilter {
  title?: string
  authorId?: number
  organizationId?: number
  publicationYearFrom?: number
  publicationYearTo?: number
  achievementType?: string
  sourceType?: 'OPENALEX' | 'CROSSREF'
  venueId?: number
  topicId?: number
}

export interface ExportTask {
  id: string
  format: ExportFormat
  status: ExportStatus
  requestedBy: number
  requestedCount: number
  exportedCount: number
  downloadAvailable: boolean
  downloadToken: string | null
  createdAt: string
  startedAt: string | null
  completedAt: string | null
  expiresAt: string | null
  errorCode: string | null
  errorMessage: string | null
}

export type OperationsHealthStatus = 'UP' | 'DOWN' | 'DEGRADED' | 'UNKNOWN'

export interface OperationsOverview {
  generatedAt: string
  applicationStatus: OperationsHealthStatus
  mysqlStatus: OperationsHealthStatus
  neo4jStatus: OperationsHealthStatus
  activeCrawlRunCount: number
  recentCrawlFailureCount: number
  graphPendingCount: number
  graphProcessingCount: number
  graphDeadCount: number
  openAlertCount: number
}

export type AlertType =
  | 'CRAWL_CONSECUTIVE_FAILURES'
  | 'PARSE_SUCCESS_RATE_DROP'
  | 'GRAPH_SYNC_BACKLOG'

export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED'

export interface AlertEvent {
  id: number
  type: AlertType
  severity: 'WARNING' | 'CRITICAL'
  status: AlertStatus
  subjectType: 'SOURCE' | 'CRAWL_TASK' | 'GRAPH_SYNC'
  subjectId: string | null
  summary: string
  evidence: Record<string, unknown>
  firstDetectedAt: string
  lastDetectedAt: string
  occurrenceCount: number
  acknowledgedBy: number | null
  acknowledgedAt: string | null
  acknowledgementReason: string | null
  version: number
}

export type GraphOutboxStatus = 'PENDING' | 'PROCESSING' | 'SUCCEEDED' | 'DEAD'

export interface GraphEvent {
  eventId: string
  achievementId: number
  desiredVersion: number
  eventType: 'REFRESH'
  status: GraphOutboxStatus
  attempts: number
  nextAttemptAt: string
  errorCode: string | null
  errorSummary: string | null
  replayOfEventId: string | null
  createdAt: string
  updatedAt: string
  completedAt: string | null
}

export type GraphMaintenanceType = 'INITIAL_BACKFILL' | 'RECONCILE' | 'FULL_REBUILD'
export type GraphMaintenanceStatus = 'PENDING' | 'RUNNING' | 'PAUSED' | 'SUCCEEDED' | 'FAILED'

export interface GraphMaintenanceRun {
  id: number
  runType: GraphMaintenanceType
  status: GraphMaintenanceStatus
  cursorAchievementId: number
  scannedCount: number
  repairedCount: number
  differenceCount: number
  requestedBy: number
  errorCode: string | null
  createdAt: string
  updatedAt: string
  completedAt: string | null
}

export type AuditCategory = 'LOGIN' | 'OPERATION'

export interface AuditFilter {
  category?: AuditCategory
  username?: string
  from?: string
  to?: string
  result?: 'SUCCESS' | 'FAILURE'
  action?: string
}

export interface AuditLog {
  category?: AuditCategory
  username?: string | null
  clientIp?: string | null
  userAgent?: string | null
  id: number
  actorUserId: number | null
  action: string
  targetType: string
  targetId: string | null
  result: 'SUCCESS' | 'FAILURE'
  traceId: string
  summary: Record<string, string | null>
  createdAt: string
}
