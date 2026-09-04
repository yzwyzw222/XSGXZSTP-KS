import { expect, test, type Route } from '@playwright/test'

const administrator = {
  id: 1,
  username: 'administrator',
  roles: ['ADMIN'],
  permissions: [
    'ACCOUNT_SELF_READ',
    'CRAWL_TASK_READ',
    'GRAPH_SYNC_READ',
    'GRAPH_SYNC_MANAGE',
    'OPERATIONS_READ',
    'ALERT_MANAGE',
  ],
}

async function fulfill(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

test('管理员在Neo4j降级时查看运行信息并执行受控处置', async ({ page }) => {
  await page.route('**/api/v1/auth/me', (route) => fulfill(route, administrator))
  await page.route('**/api/v1/auth/csrf', (route) => fulfill(route, {
    headerName: 'X-CSRF-TOKEN',
    parameterName: '_csrf',
    token: 'stage7-operations-token',
  }))
  await page.route('**/actuator/health/**', (route) => {
    const graph = new URL(route.request().url()).pathname.endsWith('/graph')
    return fulfill(route, { status: graph ? 'DOWN' : 'UP' }, graph ? 503 : 200)
  })
  await page.route('**/api/v1/operations/overview', (route) => fulfill(route, {
    generatedAt: '2026-09-03T01:00:00Z',
    applicationStatus: 'UP',
    mysqlStatus: 'UP',
    neo4jStatus: 'DOWN',
    activeCrawlRunCount: 2,
    recentCrawlFailureCount: 4,
    graphPendingCount: 6,
    graphProcessingCount: 1,
    graphDeadCount: 1,
    openAlertCount: 1,
  }))
  await page.route('**/api/v1/graph/sync-status', (route) => fulfill(route, {
    neo4jAvailable: false,
    schemaVersion: null,
    pendingCount: 6,
    processingCount: 1,
    deadCount: 1,
    oldestPendingAgeSeconds: 540,
    lastSucceededAt: '2026-09-03T00:40:00Z',
    lagThresholdExceeded: true,
    rebuildInProgress: false,
  }))
  await page.route('**/api/v1/operations/alerts**', (route) => {
    if (route.request().method() === 'POST') {
      return fulfill(route, {
        id: 9,
        type: 'GRAPH_SYNC_BACKLOG',
        severity: 'CRITICAL',
        status: 'ACKNOWLEDGED',
        subjectType: 'GRAPH_SYNC',
        subjectId: null,
        summary: '图同步存在死信事件',
        evidence: { deadCount: 1 },
        firstDetectedAt: '2026-09-03T00:50:00Z',
        lastDetectedAt: '2026-09-03T01:00:00Z',
        occurrenceCount: 2,
        acknowledgedBy: 1,
        acknowledgedAt: '2026-09-03T01:01:00Z',
        acknowledgementReason: '已确认并准备重放',
        version: 1,
      })
    }
    return fulfill(route, {
      items: [{
        id: 9,
        type: 'GRAPH_SYNC_BACKLOG',
        severity: 'CRITICAL',
        status: 'OPEN',
        subjectType: 'GRAPH_SYNC',
        subjectId: null,
        summary: '图同步存在死信事件',
        evidence: { deadCount: 1, oldestPendingAgeSeconds: 540 },
        firstDetectedAt: '2026-09-03T00:50:00Z',
        lastDetectedAt: '2026-09-03T01:00:00Z',
        occurrenceCount: 2,
        acknowledgedBy: null,
        acknowledgedAt: null,
        acknowledgementReason: null,
        version: 0,
      }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
  })
  await page.route('**/api/v1/operations/graph-events**', (route) => {
    if (route.request().method() === 'POST') {
      return fulfill(route, {
        eventId: '22222222-2222-2222-2222-222222222222',
        achievementId: 88,
        desiredVersion: 4,
        eventType: 'REFRESH',
        status: 'PENDING',
        attempts: 0,
        nextAttemptAt: '2026-09-03T01:02:00Z',
        errorCode: null,
        errorSummary: null,
        replayOfEventId: '11111111-1111-1111-1111-111111111111',
        createdAt: '2026-09-03T01:02:00Z',
        updatedAt: '2026-09-03T01:02:00Z',
        completedAt: null,
      }, 202)
    }
    return fulfill(route, {
      items: [{
        eventId: '11111111-1111-1111-1111-111111111111',
        achievementId: 88,
        desiredVersion: 4,
        eventType: 'REFRESH',
        status: 'DEAD',
        attempts: 5,
        nextAttemptAt: '2026-09-03T00:55:00Z',
        errorCode: 'GRAPH_UNAVAILABLE',
        errorSummary: 'Neo4j 暂不可用',
        replayOfEventId: null,
        createdAt: '2026-09-03T00:40:00Z',
        updatedAt: '2026-09-03T00:55:00Z',
        completedAt: null,
      }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
  })
  await page.route('**/api/v1/operations/graph-maintenance/**', (route) => {
    const run = {
      id: 5,
      runType: route.request().method() === 'POST' ? 'RECONCILE' : 'INITIAL_BACKFILL',
      status: 'PENDING',
      cursorAchievementId: 0,
      scannedCount: 0,
      repairedCount: 0,
      differenceCount: 0,
      requestedBy: 1,
      errorCode: null,
      createdAt: '2026-09-03T01:00:00Z',
      updatedAt: '2026-09-03T01:00:00Z',
      completedAt: null,
    }
    return route.request().method() === 'POST'
      ? fulfill(route, run, 202)
      : fulfill(route, { items: [run], page: 0, size: 20, totalElements: 1, totalPages: 1 })
  })
  await page.route('**/api/v1/operations/audits**', (route) => fulfill(route, {
    items: [{
      id: 12,
      actorUserId: 1,
      action: 'ALERT_ACKNOWLEDGED',
      targetType: 'ALERT_EVENT',
      targetId: '9',
      result: 'SUCCESS',
      traceId: 'trace-stage7-operations',
      summary: { alertType: 'GRAPH_SYNC_BACKLOG' },
      createdAt: '2026-09-03T01:01:00Z',
    }],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  }))

  await page.goto('/operations')
  await expect(page.getByRole('heading', { name: '运行监控' })).toBeVisible()
  await expect(page.getByText('应用存活 · 正常')).toBeVisible()
  await expect(page.getByText('MySQL 就绪 · 正常')).toBeVisible()
  await expect(page.getByText('Neo4j 独立状态 · 异常')).toBeVisible()
  await expect(page.getByText('近 24 小时未解决')).toBeVisible()
  await expect(page.getByText('图同步存在死信事件')).toBeVisible()

  await page.getByRole('button', { name: '确认告警' }).click()
  await page.getByLabel('告警确认原因').fill('已确认并准备重放')
  const acknowledgeRequest = page.waitForRequest((request) =>
    request.method() === 'POST' && request.url().endsWith('/api/v1/operations/alerts/9/acknowledge'),
  )
  await page.getByRole('button', { name: '提交确认' }).click()
  expect((await acknowledgeRequest).postDataJSON()).toEqual({ reason: '已确认并准备重放', version: 0 })

  await page.getByRole('tab', { name: '图同步事件 1' }).click()
  const replayRequest = page.waitForRequest((request) =>
    request.method() === 'POST' && request.url().includes('/graph-events/11111111-1111-1111-1111-111111111111/replay'),
  )
  await page.getByRole('button', { name: '重放', exact: true }).click()
  await replayRequest

  await page.getByRole('tab', { name: '维护运行 1' }).click()
  await expect(page.getByRole('button', { name: '全量重建' })).toBeVisible()
  const reconciliationRequest = page.waitForRequest((request) =>
    request.method() === 'POST' && request.url().endsWith('/api/v1/operations/graph-maintenance/reconcile'),
  )
  await page.getByRole('button', { name: '启动对账' }).click()
  await reconciliationRequest

  await page.getByRole('tab', { name: '审计记录 1' }).click()
  await expect(page.getByRole('cell', { name: 'ALERT_ACKNOWLEDGED' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'trace-stage7-operations' })).toBeVisible()
})
