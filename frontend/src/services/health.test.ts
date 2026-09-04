import { afterEach, describe, expect, it, vi } from 'vitest'

import { fetchGraphHealth, fetchLiveness, fetchReadiness } from './health'

describe('fetchLiveness', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('请求三个独立健康分组并返回经过校验的状态', async () => {
    const fetchMock = vi.fn().mockImplementation(async () =>
      new Response(JSON.stringify({ status: 'UP' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(fetchLiveness()).resolves.toEqual({ status: 'UP' })
    await expect(fetchReadiness()).resolves.toEqual({ status: 'UP' })
    await expect(fetchGraphHealth()).resolves.toEqual({ status: 'UP' })
    expect(fetchMock).toHaveBeenCalledWith('/actuator/health/liveness', expect.any(Object))
    expect(fetchMock).toHaveBeenCalledWith('/actuator/health/readiness', expect.any(Object))
    expect(fetchMock).toHaveBeenCalledWith('/actuator/health/graph', expect.any(Object))
  })

  it('保留503响应中的明确DOWN状态用于降级展示', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 'DOWN' }), {
      status: 503,
      headers: { 'Content-Type': 'application/json' },
    })))

    await expect(fetchGraphHealth()).resolves.toEqual({ status: 'DOWN' })
  })

  it('明确报告没有合法状态体的非成功响应', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 503 })))

    await expect(fetchLiveness()).rejects.toThrow('HTTP 503')
  })
})
