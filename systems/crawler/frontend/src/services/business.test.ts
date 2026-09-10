import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { analyticsApi, crawlApi, exportApi, operationsApi, sourceApi } from '@/services/business'
import { clearCsrfToken, resetUnauthorizedLatch, setUnauthorizedHandler } from '@/services/api'
import { blob, installHttpStub, json, type HttpStub } from '@/test/http-stub'
import type { AlertEvent, ExportTask } from '@/types/api'

const csrfBody = { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'token-value' }

describe('采集计划与运行接口', () => {
  let stub: HttpStub | undefined
  beforeEach(() => { clearCsrfToken(); resetUnauthorizedLatch() })
  afterEach(() => { stub?.restore() })
  it('来源名称搜索编码查询文本，旧标识批量回显使用只读接口', async () => {
    stub = installHttpStub(() => json([]))
    await sourceApi.entities(31, 'authors', '张三 & Smith+')
    await sourceApi.resolveEntities(31, 'institutions', ['I1', 'I2'])
    expect(stub.requests[0]).toMatchObject({ method: 'GET' })
    const query = new URL(stub.requests[0]!.url, 'http://localhost')
    expect(query.pathname).toBe('/api/v1/sources/31/entities/authors')
    expect(query.searchParams.get('query')).toBe('张三 & Smith+')
    expect(stub.requests[1]).toMatchObject({ method: 'GET', url: '/api/v1/sources/31/entities/institutions/resolve?ids=I1%2CI2' })
    expect(stub.requests).toHaveLength(2)
  })
  it('读取已有计划并携带版本更新和删除，提供历史与窗口及续跑入口', async () => {
    stub = installHttpStub((config) => json(config.url === '/api/v1/auth/csrf' ? csrfBody : {}))
    await crawlApi.getSchedule(7)
    await crawlApi.schedule(7, '08:30', 'Asia/Shanghai', 3, 'CLOSED_INDEX_DATE_WINDOW', false)
    await crawlApi.deleteSchedule(7, 4)
    await crawlApi.runs(7, 1, 20)
    await crawlApi.window(8)
    await crawlApi.control(8, 'retry-run')
    const requests = stub.requests.filter((item) => !item.url.includes('/auth/csrf'))
    expect(requests[0]?.url).toBe('/api/v1/crawl/tasks/7/schedule')
    expect(JSON.parse(requests[1]!.data as string)).toEqual({ localTime: '08:30', timeZone: 'Asia/Shanghai',
      version: 3, incrementalMode: 'CLOSED_INDEX_DATE_WINDOW', enabled: false })
    expect(requests[2]).toMatchObject({ url: '/api/v1/crawl/tasks/7/schedule?version=4', method: 'DELETE' })
    expect(requests[2]?.headers['X-CSRF-TOKEN']).toBe(csrfBody.token)
    expect(requests[3]?.url).toBe('/api/v1/crawl/tasks/7/runs?page=1&size=20')
    expect(requests[4]?.url).toBe('/api/v1/crawl/runs/8/window')
    expect(requests[5]).toMatchObject({ url: '/api/v1/crawl/runs/8/retry-run', method: 'POST' })
  })
})

describe('统计接口', () => {
  let stub: HttpStub | undefined

  afterEach(() => {
    stub?.restore()
    stub = undefined
  })

  it('四类请求使用一致的受控筛选条件和有界合作排行', async () => {
    stub = installHttpStub(() => json({}))
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

    expect(stub.requests).toHaveLength(4)
    const urls = stub.requests.map((request) => request.url)
    expect(urls.every((url) => url.includes('publicationYearFrom=2024'))).toBe(true)
    expect(urls.every((url) => url.includes('sourceType=OPENALEX'))).toBe(true)
    expect(urls[3]).toContain('/api/v1/analytics/collaboration')
    expect(urls[3]).toContain('limit=20')
  })
})

describe('导出接口', () => {
  let stub: HttpStub | undefined

  beforeEach(() => {
    clearCsrfToken()
    resetUnauthorizedLatch()
    setUnauthorizedHandler(() => undefined)
  })

  afterEach(() => {
    stub?.restore()
    stub = undefined
  })

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
    stub = installHttpStub((config) => {
      const url = String(config.url)
      if (url === '/api/v1/auth/csrf') return json(csrfBody)
      if (url.includes('/download')) return blob('title\r\n可信计算')
      return json(task)
    })

    await exportApi.create('CSV', { title: '可信计算' })
    await exportApi.get(task.id)
    const downloaded = await exportApi.download(task)

    const createRequest = stub.requests[1]
    expect(createRequest?.url).toBe('/api/v1/exports')
    expect(createRequest?.method).toBe('POST')
    expect(createRequest?.headers['X-CSRF-TOKEN']).toBe('token-value')
    expect(createRequest?.data).toBe(JSON.stringify({
      format: 'CSV',
      filters: { title: '可信计算' },
    }))
    expect(stub.requests[2]?.url).toBe(`/api/v1/exports/${task.id}`)
    expect(stub.requests[3]?.url).toContain(`token=${task.downloadToken}`)
    expect(await downloaded.text()).toContain('可信计算')
  })
})

describe('运行监控接口', () => {
  let stub: HttpStub | undefined

  beforeEach(() => {
    clearCsrfToken()
    resetUnauthorizedLatch()
    setUnauthorizedHandler(() => undefined)
  })

  afterEach(() => {
    stub?.restore()
    stub = undefined
  })

  it('使用冻结路径分页读取并提交受CSRF保护的运维操作', async () => {
    const alert = {
      id: 7,
      version: 2,
    } as AlertEvent
    stub = installHttpStub((config) =>
      config.url === '/api/v1/auth/csrf' ? json(csrfBody) : json({ items: [] }))

    await operationsApi.alerts('OPEN', 'GRAPH_SYNC_BACKLOG', 2)
    await operationsApi.graphEvents('DEAD', 1)
    await operationsApi.maintenanceRuns(3)
    await operationsApi.audits(4)
    await operationsApi.acknowledgeAlert(alert, '已完成处置')
    await operationsApi.replayGraphEvent('18e0f81b-e07a-4f19-9265-a2fe48e35b41')
    await operationsApi.startBackfill()
    await operationsApi.startReconciliation()
    await operationsApi.startRebuild()

    const urls = stub.requests.map((request) => request.url)
    expect(urls[0]).toContain('/api/v1/operations/alerts?status=OPEN&type=GRAPH_SYNC_BACKLOG&page=2&size=20')
    expect(urls[1]).toContain('/api/v1/operations/graph-events?status=DEAD&page=1&size=20')
    expect(urls[2]).toContain('/api/v1/operations/graph-maintenance/runs?page=3&size=20')
    expect(urls[3]).toContain('/api/v1/operations/audits?page=4&size=20')

    const writes = stub.requests.filter((request) => request.method === 'POST')
    expect(writes[0]?.url).toBe('/api/v1/operations/alerts/7/acknowledge')
    expect(writes[0]?.data).toBe(JSON.stringify({ reason: '已完成处置', version: 2 }))
    expect(writes[1]?.url).toContain('/graph-events/18e0f81b-e07a-4f19-9265-a2fe48e35b41/replay')
    expect(writes.at(-1)?.data).toBe(JSON.stringify({ confirmation: 'REBUILD_AACV_MANAGED_GRAPH' }))
    // CSRF 只获取一次并在后续写操作中复用。
    expect(urls.filter((url) => url === '/api/v1/auth/csrf')).toHaveLength(1)
    expect(writes.every((request) => request.headers['X-CSRF-TOKEN'] === 'token-value')).toBe(true)
  })
})
