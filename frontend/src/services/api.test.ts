import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  ApiError,
  api,
  clearCsrfToken,
  resetUnauthorizedLatch,
  setUnauthorizedHandler,
  toErrorMessage,
} from '@/services/api'
import {
  blob,
  empty,
  installHttpStub,
  json,
  networkError,
  problem,
  type HttpStub,
  type StubResponse,
} from '@/test/http-stub'

const csrfBody = { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'token-value' }

describe('apiRequest', () => {
  let stub: HttpStub | undefined

  beforeEach(() => {
    clearCsrfToken()
    resetUnauthorizedLatch()
    setUnauthorizedHandler(() => undefined)
  })

  afterEach(() => {
    vi.useRealTimers()
    stub?.restore()
    stub = undefined
  })

  it('为写请求获取并携带服务端指定的 CSRF 头，并保持 Cookie 会话', async () => {
    stub = installHttpStub((config) =>
      config.url === '/api/v1/auth/csrf' ? json(csrfBody) : json({ id: 1 }))

    await api.post('/api/v1/example', { value: 'new' })

    expect(stub.requests).toHaveLength(2)
    expect(stub.requests[0]?.url).toBe('/api/v1/auth/csrf')
    const write = stub.requests[1]
    expect(write?.url).toBe('/api/v1/example')
    expect(write?.method).toBe('POST')
    expect(write?.headers['X-CSRF-TOKEN']).toBe('token-value')
    expect(write?.headers['Content-Type']).toContain('application/json')
    expect(write?.withCredentials).toBe(true)
    expect(JSON.parse(String(write?.data))).toEqual({ value: 'new' })
  })

  it('不重复拼接 /api/v1 前缀，安全方法不请求 CSRF', async () => {
    stub = installHttpStub(() => json({ items: [] }))

    await api.get('/api/v1/catalog/achievements?page=0&size=20')

    expect(stub.requests).toHaveLength(1)
    expect(stub.requests[0]?.url).toBe('/api/v1/catalog/achievements?page=0&size=20')
  })

  it('并发写请求只获取一次 CSRF，且复用缓存令牌', async () => {
    stub = installHttpStub((config) =>
      config.url === '/api/v1/auth/csrf' ? json(csrfBody) : json({ ok: true }))

    await Promise.all([
      api.post('/api/v1/a'),
      api.post('/api/v1/b'),
      api.put('/api/v1/c'),
    ])

    expect(stub.requests.filter((request) => request.url === '/api/v1/auth/csrf')).toHaveLength(1)
    expect(stub.requests).toHaveLength(4)
  })

  it('保留 403 与 409 的明确提示与 problem+json 字段', async () => {
    stub = installHttpStub(() =>
      problem({ detail: 'version conflict', code: 'VERSION_CONFLICT', traceId: 'trace-1' }, 409))

    await expect(api.get('/api/v1/example')).rejects.toMatchObject({
      status: 409,
      code: 'VERSION_CONFLICT',
      traceId: 'trace-1',
    })
    expect(toErrorMessage(new ApiError('forbidden', 403))).toContain('权限')
    expect(toErrorMessage(new ApiError('conflict', 409))).toContain('刷新')
    expect(toErrorMessage(new ApiError('expired', 401))).toContain('重新登录')
  })

  it('解析 fieldErrors 与 errorCode 两种字段命名', async () => {
    stub = installHttpStub(() => problem({
      errorCode: 'VALIDATION_FAILED',
      fieldErrors: { realName: '请输入姓名' },
    }, 400))

    const error = await api.post('/api/v1/users', {}).catch((value: unknown) => value as ApiError)
    expect(error).toBeInstanceOf(ApiError)
    if (!(error instanceof ApiError)) throw new Error('预期接口错误')
    expect(error.code).toBe('VALIDATION_FAILED')
    expect(error.fieldErrors).toEqual({ realName: '请输入姓名' })
  })

  it('收到 401 时通知会话层并清理令牌缓存', async () => {
    const unauthorized = vi.fn()
    setUnauthorizedHandler(unauthorized)
    stub = installHttpStub(() => json({}, 401))

    await expect(api.get('/api/v1/private')).rejects.toMatchObject({ status: 401 })
    expect(unauthorized).toHaveBeenCalledOnce()
  })

  it('并发 401 只通知会话层一次，避免重复退出与提示轰炸', async () => {
    const unauthorized = vi.fn()
    setUnauthorizedHandler(unauthorized)
    stub = installHttpStub(() => json({}, 401))

    await Promise.allSettled([
      api.get('/api/v1/a'),
      api.get('/api/v1/b'),
      api.get('/api/v1/c'),
    ])

    expect(unauthorized).toHaveBeenCalledOnce()
  })

  it('重新建立会话后，下一次会话失效仍能通知', async () => {
    const unauthorized = vi.fn()
    setUnauthorizedHandler(unauthorized)
    stub = installHttpStub(() => json({}, 401))

    await expect(api.get('/api/v1/a')).rejects.toMatchObject({ status: 401 })
    resetUnauthorizedLatch()
    await expect(api.get('/api/v1/b')).rejects.toMatchObject({ status: 401 })

    expect(unauthorized).toHaveBeenCalledTimes(2)
  })

  it('写请求被 403 拒绝后清理令牌，下一次写请求重新获取', async () => {
    stub = installHttpStub((config) => {
      if (config.url === '/api/v1/auth/csrf') return json(csrfBody)
      return json({}, 403)
    })

    await expect(api.post('/api/v1/example')).rejects.toMatchObject({ status: 403 })
    await expect(api.post('/api/v1/example')).rejects.toMatchObject({ status: 403 })

    expect(stub.requests.filter((request) => request.url === '/api/v1/auth/csrf')).toHaveLength(2)
  })

  it('将超时转换为 REQUEST_TIMEOUT', async () => {
    vi.useFakeTimers()
    stub = installHttpStub(() => new Promise(() => undefined))

    const request = expect(api.get('/api/v1/slow', { timeoutMs: 20 })).rejects.toMatchObject({
      status: 0,
      code: 'REQUEST_TIMEOUT',
    })
    await vi.advanceTimersByTimeAsync(20)

    await request
  })

  it('12 秒预算覆盖 CSRF 与业务请求的总时限', async () => {
    stub = installHttpStub((config) => {
      if (config.url === '/api/v1/auth/csrf') {
        // CSRF 段消耗 60ms，业务请求只能拿到剩下的预算。
        return new Promise<StubResponse>((resolve) => {
          setTimeout(() => resolve(json(csrfBody)), 60)
        })
      }
      return json({ ok: true })
    })

    await api.post('/api/v1/example', {}, { timeoutMs: 12_000 })

    const csrfTimeout = stub.requests[0]?.timeout ?? 0
    const writeTimeout = stub.requests[1]?.timeout ?? 0
    expect(csrfTimeout).toBeGreaterThan(0)
    expect(csrfTimeout).toBeLessThanOrEqual(12_000)
    // 业务请求拿到的是共享截止时间下的剩余预算，而不是另外一份 12 秒。
    expect(writeTimeout).toBeLessThanOrEqual(12_000 - 50)
  })

  it('区分调用方取消与超时，取消不呈现为操作失败', async () => {
    stub = installHttpStub(() => new Promise(() => undefined))
    const controller = new AbortController()

    const request = api.get('/api/v1/cancellable', { signal: controller.signal })
    controller.abort()

    await expect(request).rejects.toMatchObject({ code: 'REQUEST_CANCELLED' })
    expect(toErrorMessage(await request.catch((value: unknown) => value))).toBe('')
  })

  it('已取消的信号立即拒绝，不发出请求', async () => {
    stub = installHttpStub(() => json({ ok: true }))
    const controller = new AbortController()
    controller.abort()

    await expect(api.get('/api/v1/never', { signal: controller.signal }))
      .rejects.toMatchObject({ code: 'REQUEST_CANCELLED' })
    expect(stub.requests).toHaveLength(0)
  })

  it('将网络故障转换为 NETWORK_ERROR', async () => {
    stub = installHttpStub(() => {
      throw networkError()
    })

    await expect(api.get('/api/v1/example')).rejects.toMatchObject({
      status: 0,
      code: 'NETWORK_ERROR',
    })
  })

  it('接受 204 空响应', async () => {
    stub = installHttpStub(() => empty(204))

    await expect(api.get<void>('/api/v1/empty')).resolves.toBeUndefined()
  })

  it('以浏览器原生 Blob 读取下载响应', async () => {
    stub = installHttpStub(() => blob('title\r\n可信计算'))

    const downloaded = await api.get<Blob>('/api/v1/exports/task/download?token=safe', {
      responseType: 'blob',
    })

    expect(downloaded.type).toBe('text/csv')
    expect(await downloaded.text()).toContain('可信计算')
    expect(stub.requests[0]?.headers['Accept']).toBe('*/*')
  })

  it('下载失败时解析 problem+json 而不是把错误体当作文件', async () => {
    stub = installHttpStub(() => ({
      status: 410,
      statusText: 'HTTP 410',
      data: new Blob([JSON.stringify({ detail: '导出文件已过期', code: 'EXPORT_EXPIRED' })], {
        type: 'application/problem+json',
      }),
      headers: { 'content-type': 'application/problem+json' },
    }))

    await expect(
      api.get<Blob>('/api/v1/exports/task/download?token=safe', { responseType: 'blob' }),
    ).rejects.toMatchObject({ status: 410, code: 'EXPORT_EXPIRED', message: '导出文件已过期' })
  })

  it('非 JSON 响应体退化为状态文本', async () => {
    stub = installHttpStub(() => ({
      status: 502,
      statusText: 'Bad Gateway',
      data: '<html>gateway error</html>',
      headers: { 'content-type': 'text/html' },
    }))

    await expect(api.get('/api/v1/example')).rejects.toMatchObject({
      status: 502,
      message: 'Bad Gateway',
    })
  })

  it('不虚构通用响应包装，直接返回后端负载', async () => {
    stub = installHttpStub(() => json({ items: [{ id: 1 }], page: 0, size: 20, totalElements: 1, totalPages: 1 }))

    await expect(api.get('/api/v1/catalog/achievements')).resolves.toEqual({
      items: [{ id: 1 }], page: 0, size: 20, totalElements: 1, totalPages: 1,
    })
  })

  it('写操作失败不自动重试', async () => {
    stub = installHttpStub((config) =>
      config.url === '/api/v1/auth/csrf' ? json(csrfBody) : json({}, 500))

    await expect(api.post('/api/v1/example', { value: 1 })).rejects.toMatchObject({ status: 500 })
    expect(stub.requests.filter((request) => request.url === '/api/v1/example')).toHaveLength(1)
  })

  it('共享 CSRF 的短预算调用按自身期限结束，其他调用仍能成功', async () => {
    vi.useFakeTimers()
    stub = installHttpStub((config) => config.url === '/api/v1/auth/csrf'
      ? new Promise<StubResponse>((resolve) => setTimeout(() => resolve(json(csrfBody)), 100))
      : json({ ok: true }))
    const long = api.post('/api/v1/long', {}, { timeoutMs: 200 })
    const short = expect(api.post('/api/v1/short', {}, { timeoutMs: 20 }))
      .rejects.toMatchObject({ code: 'REQUEST_TIMEOUT' })
    await vi.advanceTimersByTimeAsync(20)
    await short
    expect(stub.requests).toHaveLength(1)
    await vi.advanceTimersByTimeAsync(80)
    await expect(long).resolves.toEqual({ ok: true })
    expect(stub.requests.map((request) => request.url)).toEqual(['/api/v1/auth/csrf', '/api/v1/long'])
  })

  it('已取消的写请求不获取 CSRF', async () => {
    stub = installHttpStub(() => json(csrfBody))
    await expect(api.post('/api/v1/never', {}, { signal: AbortSignal.abort() }))
      .rejects.toMatchObject({ code: 'REQUEST_CANCELLED' })
    expect(stub.requests).toHaveLength(0)
  })

  it('清理 CSRF 时旧等待者取消，新写请求只使用新令牌', async () => {
    let release!: (value: StubResponse) => void
    let calls = 0
    stub = installHttpStub((config) => {
      if (config.url !== '/api/v1/auth/csrf') return json({ ok: true })
      return ++calls === 1 ? new Promise<StubResponse>((resolve) => { release = resolve }) : json(csrfBody)
    })
    const old = expect(api.post('/api/v1/old')).rejects.toMatchObject({ code: 'REQUEST_CANCELLED' })
    await vi.waitFor(() => expect(calls).toBe(1))
    clearCsrfToken()
    await api.post('/api/v1/new')
    release(json({ ...csrfBody, token: 'stale' }))
    await old
    expect(stub.requests.some((request) => request.url === '/api/v1/old')).toBe(false)
  })
})
