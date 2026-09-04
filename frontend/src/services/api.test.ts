import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  ApiError,
  api,
  clearCsrfToken,
  setUnauthorizedHandler,
  toErrorMessage,
} from '@/services/api'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('apiRequest', () => {
  beforeEach(() => {
    clearCsrfToken()
    setUnauthorizedHandler(() => undefined)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('为写请求获取并携带 CSRF Token', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'token-value',
      }))
      .mockResolvedValueOnce(jsonResponse({ id: 1 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.post('/api/v1/example', { value: 'new' })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/auth/csrf')
    const request = fetchMock.mock.calls[1]?.[1]
    expect(new Headers(request?.headers).get('X-CSRF-TOKEN')).toBe('token-value')
    expect(request?.credentials).toBe('same-origin')
    expect(request?.body).toBe(JSON.stringify({ value: 'new' }))
  })

  it('保留 403 与 409 的明确提示', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>().mockResolvedValue(
        jsonResponse({ detail: 'version conflict', code: 'VERSION_CONFLICT' }, 409),
      ),
    )

    await expect(api.get('/api/v1/example')).rejects.toMatchObject({
      status: 409,
      code: 'VERSION_CONFLICT',
    })
    expect(toErrorMessage(new ApiError('forbidden', 403))).toContain('权限')
    expect(toErrorMessage(new ApiError('conflict', 409))).toContain('刷新')
  })

  it('收到 401 时通知会话层并清理会话', async () => {
    const unauthorized = vi.fn()
    setUnauthorizedHandler(unauthorized)
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(jsonResponse({}, 401)))

    await expect(api.get('/api/v1/private')).rejects.toMatchObject({ status: 401 })
    expect(unauthorized).toHaveBeenCalledOnce()
  })

  it('将超时转换为 REQUEST_TIMEOUT', async () => {
    vi.useFakeTimers()
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>().mockImplementation((_input, init) =>
        new Promise((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
        }),
      ),
    )

    const request = expect(api.get('/api/v1/slow', { timeoutMs: 20 })).rejects.toMatchObject({
      status: 0,
      code: 'REQUEST_TIMEOUT',
    })
    await vi.advanceTimersByTimeAsync(20)

    await request
  })

  it('接受 204 空响应', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 204 })))

    await expect(api.get<void>('/api/v1/empty')).resolves.toBeUndefined()
  })

  it('以浏览器原生 Blob 读取下载响应', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>().mockResolvedValue(new Response('title\r\n可信计算', {
        status: 200,
        headers: { 'Content-Type': 'text/csv' },
      })),
    )

    const blob = await api.get<Blob>('/api/v1/exports/task/download?token=safe', {
      responseType: 'blob',
    })

    expect(blob.type).toBe('text/csv')
    expect(await blob.text()).toContain('可信计算')
  })
})
