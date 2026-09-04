import { afterEach, describe, expect, it, vi } from 'vitest'

import { analyticsApi, exportApi, operationsApi } from '@/services/business'
import { clearCsrfToken } from '@/services/api'
import type { AlertEvent, ExportTask } from '@/types/api'

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('统计接口', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('四类请求使用一致的受控筛选条件和有界合作排行', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockImplementation(async () => jsonResponse({}))
    vi.stubGlobal('fetch', fetchMock)
    const filters = {
      publicationYearFrom: 2024,
      publicationYearTo: 2026,
      achievementType: 'article',
      sourceType: 'OPENALEX' as const,
      organizationId: 9,
      topicId: 7,
    }

    await Promise.all([
      analyticsApi.overview(filters),
      analyticsApi.trends(filters),
      analyticsApi.distributions(filters),
      analyticsApi.collaboration(filters),
    ])

    expect(fetchMock).toHaveBeenCalledTimes(4)
    const urls = fetchMock.mock.calls.map(([url]) => String(url))
    expect(urls.every((url) => url.includes('publicationYearFrom=2024'))).toBe(true)
    expect(urls.every((url) => url.includes('sourceType=OPENALEX'))).toBe(true)
    expect(urls[3]).toContain('/api/v1/analytics/collaboration')
    expect(urls[3]).toContain('limit=20')
  })
})

describe('导出接口', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('使用冻结契约创建、查询并下载导出任务', async () => {
    const task: ExportTask = {
      id: '18e0f81b-e07a-4f19-9265-a2fe48e35b41',
      format: 'CSV',
      status: 'SUCCEEDED',
      requestedBy: 8,
      requestedCount: 1,
      exportedCount: 1,
      downloadAvailable: true,
      downloadToken: 'abcdefghijklmnopqrstuvwxyz1234567890ABCDEFG',
      createdAt: '2026-09-03T00:00:00Z',
      startedAt: '2026-09-03T00:00:01Z',
      completedAt: '2026-09-03T00:00:02Z',
      expiresAt: '2026-09-04T00:00:02Z',
      errorCode: null,
      errorMessage: null,
    }
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'token-value',
      }))
      .mockResolvedValueOnce(jsonResponse(task))
      .mockResolvedValueOnce(jsonResponse(task))
      .mockResolvedValueOnce(new Response('title\r\n可信计算', {
        status: 200,
        headers: { 'Content-Type': 'text/csv' },
      }))
    vi.stubGlobal('fetch', fetchMock)

    await exportApi.create('CSV', { title: '可信计算' })
    await exportApi.get(task.id)
    const blob = await exportApi.download(task)

    const createRequest = fetchMock.mock.calls[1]
    expect(createRequest?.[0]).toBe('/api/v1/exports')
    expect(createRequest?.[1]?.body).toBe(JSON.stringify({
      format: 'CSV',
      filters: { title: '可信计算' },
    }))
    expect(fetchMock.mock.calls[2]?.[0]).toBe(`/api/v1/exports/${task.id}`)
    expect(String(fetchMock.mock.calls[3]?.[0])).toContain(`token=${task.downloadToken}`)
    expect(await blob.text()).toContain('可信计算')
  })
})

describe('运行监控接口', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('使用冻结路径分页读取并提交受CSRF保护的运维操作', async () => {
    clearCsrfToken()
    const alert = {
      id: 7,
      version: 2,
    } as AlertEvent
    const fetchMock = vi.fn<typeof fetch>().mockImplementation(async (input) =>
      String(input) === '/api/v1/auth/csrf'
        ? jsonResponse({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'token-value' })
        : jsonResponse({ items: [] }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await operationsApi.alerts('OPEN', 'GRAPH_SYNC_BACKLOG', 2)
    await operationsApi.graphEvents('DEAD', 1)
    await operationsApi.maintenanceRuns(3)
    await operationsApi.audits(4)
    await operationsApi.acknowledgeAlert(alert, '已完成处置')
    await operationsApi.replayGraphEvent('18e0f81b-e07a-4f19-9265-a2fe48e35b41')
    await operationsApi.startBackfill()
    await operationsApi.startReconciliation()
    await operationsApi.startRebuild()

    const urls = fetchMock.mock.calls.map(([url]) => String(url))
    expect(urls[0]).toContain('/api/v1/operations/alerts?status=OPEN&type=GRAPH_SYNC_BACKLOG&page=2&size=20')
    expect(urls[1]).toContain('/api/v1/operations/graph-events?status=DEAD&page=1&size=20')
    expect(urls[2]).toContain('/api/v1/operations/graph-maintenance/runs?page=3&size=20')
    expect(urls[3]).toContain('/api/v1/operations/audits?page=4&size=20')
    expect(fetchMock.mock.calls[5]?.[0]).toBe('/api/v1/operations/alerts/7/acknowledge')
    expect(fetchMock.mock.calls[5]?.[1]?.body).toBe(JSON.stringify({ reason: '已完成处置', version: 2 }))
    expect(fetchMock.mock.calls[6]?.[0]).toContain('/graph-events/18e0f81b-e07a-4f19-9265-a2fe48e35b41/replay')
    expect(fetchMock.mock.calls[9]?.[1]?.body).toBe(JSON.stringify({ confirmation: 'REBUILD_AACV_MANAGED_GRAPH' }))
  })
})
