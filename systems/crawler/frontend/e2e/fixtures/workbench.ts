import type { Page } from '@playwright/test'

export const time = '2026-09-06T08:00:00Z'
const scope = { source: 'MYSQL', filters: {} }
const permissions = ['ACCOUNT_SELF_READ', 'CATALOG_READ', 'AUTHOR_IMPORT', 'GRAPH_READ', 'ANALYTICS_READ', 'SOURCE_READ', 'SOURCE_MANAGE', 'SOURCE_PROBE', 'CRAWL_TASK_READ', 'CRAWL_TASK_CREATE', 'CRAWL_TASK_UPDATE', 'CRAWL_TASK_CONTROL', 'CRAWL_SCHEDULE_MANAGE', 'GOVERNANCE_READ', 'GOVERNANCE_MANAGE', 'OPERATIONS_READ', 'GRAPH_SYNC_READ', 'GRAPH_SYNC_MANAGE', 'ALERT_MANAGE', 'USER_LIST', 'USER_CREATE', 'USER_UPDATE', 'USER_ROLE_CHANGE', 'USER_ENABLE', 'USER_DISABLE', 'USER_PASSWORD_RESET', 'AUDIT_READ', 'EXPORT_CREATE', 'EXPORT_READ']
export const achievement = { id: 42, title: '面向开放学术数据的可信知识图谱与研究证据溯源方法', doi: '10.1000/aacv-demo', publicationYear: 2026, publicationDate: '2026-05-20', achievementType: 'article', primaryVenue: '计算机学报', authors: [], organizations: [], topics: [], sources: ['OPENALEX'], version: 1 }

/** 全路由视觉夹具仅提供本机合成数据，未定义的请求明确失败。 */
export async function fixture(page: Page, authenticated = true) {
  const requests: string[] = []
  await page.addInitScript(() => {
    const runtime = window as typeof window & { aacvTestErrors: string[] }
    runtime.aacvTestErrors = []
    window.addEventListener('error', event => runtime.aacvTestErrors.push(event.message))
  })
  await page.route('**/actuator/**', (route) => route.fulfill({ json: { status: 'UP' } }))
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname.replace('/api/v1', '')
    requests.push(path + url.search)
    const pageOf = (items: unknown[]) => ({ items, page: Number(url.searchParams.get('page') ?? 0), size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0 })
    let data: unknown
    if (path === '/auth/me' && !authenticated) return route.fulfill({ status: 401, json: { detail: '请登录', code: 'UNAUTHORIZED' } })
    if (path === '/auth/me') data = { id: 1, username: 'academic-admin', roles: ['ADMIN'], permissions }
    else if (path === '/auth/csrf') data = { headerName: 'X-AACV-CSRF', token: 'synthetic-visual-token' }
    else if (path === '/analytics/overview') data = { achievementCount: 1286, authorCount: 428, organizationCount: 36, sourceCount: 2, scope, updatedAt: time, coverage: { withDoiCount: 1028, withPublicationYearCount: 1200, withAbstractCount: 928, withCitationCount: 780, withOpenAccessStatusCount: 620, withRetractionStatusCount: 550, authorshipsMayBeIncompleteCount: 32 } }
    else if (path === '/analytics/trends') data = { items: [2022, 2023, 2024, 2025, 2026].map((publicationYear, i) => ({ publicationYear, achievementCount: [120, 184, 268, 340, 374][i] })), scope, updatedAt: time }
    else if (path === '/analytics/distributions') data = { achievementTypes: [{ key: 'article', label: '期刊论文', achievementCount: 1028 }], sources: [{ key: 'OPENALEX', label: 'OpenAlex', achievementCount: 800 }, { key: 'CROSSREF', label: 'Crossref', achievementCount: 620 }], organizations: [{ key: '3', label: '开放科学研究院', achievementCount: 268 }], topics: [{ key: '5', label: '可信计算与软件工程', achievementCount: 328 }], scope, updatedAt: time }
    else if (path === '/analytics/collaboration') data = { authors: [{ leftId: 1, leftLabel: '林研究员', rightId: 2, rightLabel: '张研究员', sharedAchievementCount: 18 }], organizations: [{ leftId: 3, leftLabel: '开放科学研究院', rightId: 4, rightLabel: '软件工程实验室', sharedAchievementCount: 12 }], scope, updatedAt: time }
    else if (path === '/catalog/achievements') data = pageOf([achievement])
    else if (path === '/catalog/achievements/42') data = { summary: achievement, language: 'zh', abstractText: '本研究围绕学术元数据的可验证性，保留多来源证据并分析研究合作关系。', authorshipsMayBeIncomplete: false, authorships: [], referencedWorkIds: [], sources: [], fields: [] }
    else if (/^\/catalog\/(authors|organizations|venues|topics)$/.test(path)) data = pageOf([{ id: 7, name: '开放科学研究院', displayName: '开放科学研究院', achievementCount: 42 }])
    else if (path === '/graph/subgraph' || path === '/graph/path') data = { nodes: [{ id: 'ACHIEVEMENT:42', businessId: '42', type: 'ACHIEVEMENT', label: achievement.title, properties: { publicationYear: 2026 } }, { id: 'AUTHOR:7', businessId: '7', type: 'AUTHOR', label: '林研究员', properties: {} }], edges: [{ id: 'AUTHORED:7:42', type: 'AUTHORED', source: 'AUTHOR:7', target: 'ACHIEVEMENT:42', properties: {} }], rootNodeId: 'ACHIEVEMENT:42', truncated: false, appliedLimits: { depth: 1, nodeLimit: 100, maxHops: 6 }, syncedAt: time, projectionLagSeconds: 2, traceId: 'visual-graph' }
    else if (path === '/graph/sync-status') data = { neo4jAvailable: true, rebuildInProgress: false, lagThresholdExceeded: false, oldestPendingAgeSeconds: 2, pendingCount: 3, deadCount: 0, generatedAt: time }
    else if (path === '/operations/overview') data = { applicationStatus: 'UP', mysqlStatus: 'UP', neo4jStatus: 'UP', activeCrawlRunCount: 2, recentCrawlFailureCount: 1, graphPendingCount: 3, graphProcessingCount: 1, graphDeadCount: 0, openAlertCount: 1, generatedAt: time }
    else if (path === '/author-import' || /^\/author-import\/achievements\/\d+\/evidence$/.test(path)) data = []
    else if (path === '/users/statistics') data = { totalUsers: 12, admin: 2, dataOperator: 3, researcher: 7 }
    else if (path === '/users') data = pageOf([{ id: 2, username: 'research-demo', realName: '林研究员', organization: '开放科学研究院', roles: ['RESEARCHER'], status: 'ACTIVE', version: 1, createdAt: time, updatedAt: time, credentialsChangedAt: time }])
    else if (['/operations/alerts', '/operations/audits', '/operations/graph-events', '/operations/graph-maintenance/runs'].includes(path)) data = pageOf([])
    else return route.fulfill({ status: 404, json: { detail: '视觉夹具未定义接口', errorCode: 'NOT_FOUND' } })
    await route.fulfill({ json: data })
  })
  return requests
}
