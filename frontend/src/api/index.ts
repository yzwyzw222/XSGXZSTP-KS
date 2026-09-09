/**
 * 类型化 API 层：把后端全部接口（见 docs/openapi.yaml）包装成带类型的函数。
 * 页面组件只 import 这里的函数，不直接拼 URL/请求体，好处：
 *  - 类型即契约：与后端 DTO 字段一一对应，改字段时 TS 编译器立刻报错；
 *  - 一处维护：路径、参数序列化集中在 qs() 里，避免每个页面重复拼接。
 * 底层统一走 api/http.ts 的 request()（原生 fetch + 12s 超时 + CSRF 头）。
 */
import { del, get, post, put, upload } from './http'
import type { SessionUser } from '../session'

// ------------------------------------------------------------------
// 通用分页契约（与后端 PageResponse 对应）：page 从 0 起
// ------------------------------------------------------------------
export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** 查询参数序列化：跳过 undefined/null/空串，值用 encodeURIComponent 转义 */
function qs(params: Record<string, string | number | undefined | null>): string {
  const parts = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
  return parts.length ? '?' + parts.join('&') : ''
}

// ------------------------------------------------------------------
// 认证（auth 分组）
// ------------------------------------------------------------------
export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  displayName?: string
}

export const authApi = {
  /** 建立会话并取 CSRF Token（Token 会经 http.ts 自动写入内存） */
  csrf: () => get<{ token: string }>('/api/v1/auth/csrf'),
  register: (body: RegisterRequest) => post<SessionUser>('/api/v1/auth/register', body),
  login: (body: LoginRequest) => post<SessionUser>('/api/v1/auth/login', body),
  me: () => get<SessionUser>('/api/v1/auth/me'),
  logout: () => post<void>('/api/v1/auth/logout')
}

// ------------------------------------------------------------------
// 论文（papers 分组）
// ------------------------------------------------------------------
export interface AuthorItem {
  authorId: number
  position: number
  institutionId?: number | null
}

export interface ReferenceItem {
  citedPaperId?: number | null
  externalCitedDoi?: string | null
}

export interface PaperUpsert {
  title: string
  doi?: string
  paperType?: string
  language?: string
  publicationDate?: string
  abstractText?: string
  citationCount?: number
  venueId?: number | null
  // 知网导入扩展字段（可空字符串：期号含字母如 Z3、页码是区间、分类号多值分号分隔）
  volume?: string
  period?: string
  pageCount?: string
  clcNumber?: string
  url?: string
  authors?: AuthorItem[]
  keywordIds?: number[]
  references?: ReferenceItem[]
  version?: number | null
}

export interface VenueRef {
  id: number
  displayName: string
}

export interface AuthorRef {
  authorId: number
  displayName: string
  position: number
  institutionId: number | null
  institutionName: string | null
}

export interface KeywordRef {
  id: number
  name: string
}

export interface ReferenceRef {
  id: number
  citedPaperId: number | null
  citedTitle: string | null
  externalCitedDoi: string | null
}

export interface Paper {
  id: number
  title: string
  doi: string | null
  paperType: string
  language: string | null
  publicationDate: string | null
  publicationYear: number | null
  abstractText: string | null
  citationCount: number
  /** LLM 抽取状态（PENDING/IN_PROGRESS/COMPLETED/FAILED），实体抽取面板据此筛选 */
  extractionStatus: string
  venue: VenueRef | null
  volume: string | null
  period: string | null
  pageCount: string | null
  clcNumber: string | null
  url: string | null
  authors: AuthorRef[]
  keywords: KeywordRef[]
  references: ReferenceRef[]
  version: number
  createdAt: string
  updatedAt: string
}

/** 论文类型白名单（与后端 ck_paper_type 约束一致，前端下拉只给合法值） */
export const PAPER_TYPES = ['JOURNAL_ARTICLE', 'CONFERENCE_PAPER', 'PATENT', 'OTHER'] as const

export const papersApi = {
  list: (
    params: { keyword?: string; paperType?: string; year?: number; page?: number; size?: number },
    signal?: AbortSignal
  ) => get<Page<Paper>>(`/api/v1/papers${qs(params)}`, signal),
  get: (id: number) => get<Paper>(`/api/v1/papers/${id}`),
  create: (body: PaperUpsert) => post<Paper>('/api/v1/papers', body),
  update: (id: number, body: PaperUpsert) => put<Paper>(`/api/v1/papers/${id}`, body),
  remove: (id: number) => del<void>(`/api/v1/papers/${id}`)
}

// ------------------------------------------------------------------
// 作者 / 机构 / 关键词 / 渠道（标准 CRUD，结构同构）
// ------------------------------------------------------------------
export interface Author {
  id: number
  displayName: string
  orcid: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface AuthorUpsert {
  displayName: string
  orcid?: string
  version?: number | null
}

export interface Institution {
  id: number
  displayName: string
  countryCode: string | null
  institutionType: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface InstitutionUpsert {
  displayName: string
  countryCode?: string
  institutionType?: string
  version?: number | null
}

export interface Keyword {
  id: number
  name: string
  fieldName: string | null
  createdAt: string
  updatedAt: string
}

export interface KeywordUpsert {
  name: string
  fieldName?: string
}

export interface Venue {
  id: number
  displayName: string
  issn: string | null
  venueType: string | null
  createdAt: string
  updatedAt: string
}

export interface VenueUpsert {
  displayName: string
  issn?: string
  venueType?: string
}

export const authorsApi = {
  list: (params: { keyword?: string; page?: number; size?: number }) =>
    get<Page<Author>>(`/api/v1/authors${qs(params)}`),
  get: (id: number) => get<Author>(`/api/v1/authors/${id}`),
  create: (body: AuthorUpsert) => post<Author>('/api/v1/authors', body),
  update: (id: number, body: AuthorUpsert) => put<Author>(`/api/v1/authors/${id}`, body),
  remove: (id: number) => del<void>(`/api/v1/authors/${id}`)
}

export const institutionsApi = {
  list: (params: { keyword?: string; page?: number; size?: number }) =>
    get<Page<Institution>>(`/api/v1/institutions${qs(params)}`),
  get: (id: number) => get<Institution>(`/api/v1/institutions/${id}`),
  create: (body: InstitutionUpsert) => post<Institution>('/api/v1/institutions', body),
  update: (id: number, body: InstitutionUpsert) => put<Institution>(`/api/v1/institutions/${id}`, body),
  remove: (id: number) => del<void>(`/api/v1/institutions/${id}`)
}

export const keywordsApi = {
  list: (params: { keyword?: string; page?: number; size?: number }) =>
    get<Page<Keyword>>(`/api/v1/keywords${qs(params)}`),
  get: (id: number) => get<Keyword>(`/api/v1/keywords/${id}`),
  create: (body: KeywordUpsert) => post<Keyword>('/api/v1/keywords', body),
  update: (id: number, body: KeywordUpsert) => put<Keyword>(`/api/v1/keywords/${id}`, body),
  remove: (id: number) => del<void>(`/api/v1/keywords/${id}`)
}

export const venuesApi = {
  list: (params: { keyword?: string; page?: number; size?: number }) =>
    get<Page<Venue>>(`/api/v1/venues${qs(params)}`),
  get: (id: number) => get<Venue>(`/api/v1/venues/${id}`),
  create: (body: VenueUpsert) => post<Venue>('/api/v1/venues', body),
  update: (id: number, body: VenueUpsert) => put<Venue>(`/api/v1/venues/${id}`, body),
  remove: (id: number) => del<void>(`/api/v1/venues/${id}`)
}

// ------------------------------------------------------------------
// 多维分析（analytics 分组）：四类只读查询，limit 控制 TOP-N
// ------------------------------------------------------------------
export interface CollaborationItem {
  author1Id: number
  author1: string
  author2Id: number
  author2: string
  paperCount: number
}

export interface CitationItem {
  paperId: number
  title: string
  citesOut: number
  citesIn: number
}

export interface TopicEvolutionItem {
  year: number
  keyword: string
  paperCount: number
}

export interface InstitutionImpactItem {
  institution: string
  paperCount: number
  totalCitations: number
}

export const analyticsApi = {
  collaborations: (limit?: number) =>
    get<CollaborationItem[]>(`/api/v1/analytics/collaborations${qs({ limit })}`),
  /**
   * 合作关系边（Neo4j 物化的 COAUTHOR_WITH）：图谱页"显示合作关系"开关的数据源。
   * 结构与 collaborations 相同，paperCount 就是边上的 weight（共同署名论文数）
   */
  coauthorEdges: (limit?: number) =>
    get<CollaborationItem[]>(`/api/v1/analytics/coauthor-edges${qs({ limit })}`),
  citations: (limit?: number) => get<CitationItem[]>(`/api/v1/analytics/citations${qs({ limit })}`),
  topicEvolution: (limit?: number) =>
    get<TopicEvolutionItem[]>(`/api/v1/analytics/topic-evolution${qs({ limit })}`),
  institutionImpact: (limit?: number) =>
    get<InstitutionImpactItem[]>(`/api/v1/analytics/institution-impact${qs({ limit })}`)
}

// ------------------------------------------------------------------
// 关系分析（relations 分组）：五个板块的关系钻取查询
// ------------------------------------------------------------------
export interface CoauthorItem {
  authorId: number
  authorName: string
  paperCount: number
  paperTitles: string[]
  institutions: string[]
  sameInstitution: boolean
}

export interface FieldPartitionItem {
  field: string
  authorId: number
  authorName: string
  paperCount: number
}

export interface AuthorTopicItem {
  keywordId: number
  keyword: string
  fieldName: string | null
  paperCount: number
  years: number[]
}

export interface InstitutionAuthorItem {
  authorId: number
  authorName: string
  paperCount: number
  totalCitations: number
}

export interface InstitutionCollabItem {
  inst1Id: number
  inst1: string
  inst2Id: number
  inst2: string
  paperCount: number
}

export const relationsApi = {
  /** 合作者网络：某作者的合作者明细（合著篇数/共同论文标题/同机构标签） */
  coauthors: (authorId: number, limit?: number) =>
    get<CoauthorItem[]>(`/api/v1/relations/coauthors${qs({ authorId, limit })}`),
  /** 研究领域分区：全体作者的领域 × 作者扁平行（前端按 field 分组） */
  fieldPartition: (limit?: number) =>
    get<FieldPartitionItem[]>(`/api/v1/relations/field-partition${qs({ limit })}`),
  /** 研究领域分区：某作者的主题画像（关键词 × 篇数 × 出现年份） */
  authorTopics: (authorId: number, limit?: number) =>
    get<AuthorTopicItem[]>(`/api/v1/relations/author-topics${qs({ authorId, limit })}`),
  /** 科研机构：某机构下的作者明细 */
  institutionAuthors: (institutionId: number, limit?: number) =>
    get<InstitutionAuthorItem[]>(`/api/v1/relations/institution-authors${qs({ institutionId, limit })}`),
  /** 科研机构：机构间合作边表（两两机构共同署名论文数） */
  institutionCollaborations: (limit?: number) =>
    get<InstitutionCollabItem[]>(`/api/v1/relations/institution-collaborations${qs({ limit })}`)
}

// ------------------------------------------------------------------
// 后台管理（admin 分组）：仅 ADMIN 可调用，后端强制校验
// ------------------------------------------------------------------
export interface UserAdmin {
  id: number
  username: string
  displayName: string
  status: string
  roles: string[]
  createdAt: string
}

export interface CreateUserRequest {
  username: string
  password: string
  displayName?: string
  roles?: string[]
}

// ------------------------------------------------------------------
// 数据导入（管理员）：「论文为中心」的嵌套 JSON 文件上传
// ------------------------------------------------------------------
/** 导入文件里的一篇论文（嵌套作者/机构/关键词/渠道，与后端 ImportPaperItem 对齐） */
export interface ImportPaperItem {
  title: string
  doi?: string
  paperType?: string
  language?: string
  publicationDate?: string | null
  abstractText?: string
  citationCount?: number
  venue?: { name: string; venueType?: string; issn?: string } | null
  authors: { name: string; orcid?: string; institution?: { name: string; countryCode?: string } | null }[]
  keywords?: { name: string; fieldName?: string }[]
  volume?: string
  period?: string
  pageCount?: string
  clcNumber?: string
  url?: string
}

/** 单篇导入结果行：IMPORTED=成功 / SKIPPED=已存在跳过 / FAILED=失败（message 带原因） */
export interface ImportResultRow {
  index: number
  status: 'IMPORTED' | 'SKIPPED' | 'FAILED'
  message: string | null
  paperId: number | null
  title: string | null
}

/** 导入汇总：计数 + 新建关联对象计数 + 逐条明细 */
export interface ImportSummary {
  total: number
  imported: number
  skipped: number
  failed: number
  createdAuthors: number
  createdInstitutions: number
  createdKeywords: number
  createdVenues: number
  rows: ImportResultRow[]
}

export const adminApi = {
  users: (params: { keyword?: string; page?: number; size?: number }) =>
    get<Page<UserAdmin>>(`/api/v1/admin/users${qs(params)}`),
  createUser: (body: CreateUserRequest) => post<UserAdmin>('/api/v1/admin/users', body),
  updateRoles: (id: number, roles: string[]) => put<UserAdmin>(`/api/v1/admin/users/${id}/roles`, { roles }),
  updateStatus: (id: number, status: string) => put<UserAdmin>(`/api/v1/admin/users/${id}/status`, { status }),
  remove: (id: number) => del<void>(`/api/v1/admin/users/${id}`),
  syncGraph: () => post<{ processed: number }>('/api/v1/admin/sync-graph'),
  /** 上传嵌套 JSON 文件批量导入论文（multipart 表单，字段名 file） */
  importPapers: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return upload<ImportSummary>('/api/v1/admin/import/papers', formData)
  },
  /** 在线爬取 OpenAlex（关键词搜索单页）：返回与文件导入同构的汇总 */
  crawlOpenAlex: (body: { keyword: string; maxRecords?: number }) =>
    post<ImportSummary>('/api/v1/admin/crawl/openalex', body)
}

// ------------------------------------------------------------------
// 实体抽取（extraction 分组）：触发是异步的（202 立即返回），前端轮询 status
// ------------------------------------------------------------------
export type ExtractionStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED'

export interface ExtractionStatusDto {
  paperId: number
  paperTitle: string
  status: ExtractionStatus
  extractedEntityCount: number
  extractedRelationshipCount: number
}

/** 台账实体行：type 是 LLM 输出的六类之一，resolved* 是归并后的业务实体定位 */
export interface ExtractionEntityDto {
  id: number
  name: string
  type: string
  resolvedEntityType: string | null
  resolvedEntityId: number | null
}

/** 关系行：端点已由后端回填成名称，evidence 是 LLM 给出的原文依据 */
export interface ExtractionRelationshipDto {
  id: number
  sourceName: string
  targetName: string
  type: string
  evidence: string
  confidence: number
}

export interface ExtractionResultDto {
  paperId: number
  paperTitle: string
  status: ExtractionStatus
  entities: ExtractionEntityDto[]
  relationships: ExtractionRelationshipDto[]
}

export interface ExtractionTriggerResponse {
  message: string
  queuedCount: number
  paperIds: number[]
}

/** 抽取图谱数据（与主图谱同构的节点/边） */
export interface ExtractionGraphNode {
  id: string
  label: string
  type: string
  entityType: string | null
}

export interface ExtractionGraphEdge {
  id: string
  source: string
  target: string
  kind: string
  label: string
  evidence: string
  confidence: number
}

export interface ExtractionGraphData {
  nodes: ExtractionGraphNode[]
  edges: ExtractionGraphEdge[]
}

export const extractionApi = {
  /** 触发抽取：paperIds 缺省时抽最早的 10 篇 PENDING；统一数组契约 */
  trigger: (paperIds?: number[]) =>
    post<ExtractionTriggerResponse>('/api/v1/admin/extraction/trigger', paperIds ? { paperIds } : {}),
  status: (paperId: number) => get<ExtractionStatusDto>(`/api/v1/admin/extraction/status/${paperId}`),
  result: (paperId: number) => get<ExtractionResultDto>(`/api/v1/admin/extraction/result/${paperId}`),
  /**
   * 抽取图谱数据（登录即可）。后端接收重复参数 ?paperIds=1&paperIds=2，
   * qs() 只支持单值，这里手工拼接。
   */
  graphData: (paperIds?: number[]) => {
    const query = paperIds?.length ? '?' + paperIds.map((id) => `paperIds=${id}`).join('&') : ''
    return get<ExtractionGraphData>(`/api/v1/extraction/graph-data${query}`)
  }
}
