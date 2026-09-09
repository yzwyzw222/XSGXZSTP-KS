import { afterEach, describe, expect, it, vi } from 'vitest'

import { fetchGraphHealth, fetchLiveness, fetchReadiness } from './health'
import { installHttpStub, json, type HttpStub } from '@/test/http-stub'

describe('fetchLiveness', () => {
  let stub: HttpStub | undefined

  afterEach(() => {
    vi.useRealTimers()
    stub?.restore()
    stub = undefined
  })

  it('请求三个独立健康分组并返回经过校验的状态', async () => {
    stub = installHttpStub(() => json({ status: 'UP' }))

    await expect(fetchLiveness()).resolves.toEqual({ status: 'UP' })
    await expect(fetchReadiness()).resolves.toEqual({ status: 'UP' })
    await expect(fetchGraphHealth()).resolves.toEqual({ status: 'UP' })
    expect(stub.requests.map((request) => request.url)).toEqual([
      '/actuator/health/liveness',
      '/actuator/health/readiness',
      '/actuator/health/graph',
    ])
  })

  it('保留503响应中的明确DOWN状态用于降级展示', async () => {
    stub = installHttpStub(() => json({ status: 'DOWN' }, 503))

    await expect(fetchGraphHealth()).resolves.toEqual({ status: 'DOWN' })
  })

  it('明确报告没有合法状态体的非成功响应', async () => {
    stub = installHttpStub(() => ({ status: 503, statusText: 'HTTP 503', data: '', headers: {} }))

    await expect(fetchLiveness()).rejects.toThrow('HTTP 503')
  })

  it('拒绝无法识别的状态值', async () => {
    stub = installHttpStub(() => json({ status: 'DEGRADED_PARTIALLY' }, 503))

    await expect(fetchGraphHealth()).rejects.toThrow('健康检查响应格式无效')
  })

  it('调用方取消时不报告为服务故障', async () => {
    stub = installHttpStub(() => new Promise(() => undefined))
    const controller = new AbortController()

    const request = fetchLiveness(controller.signal)
    controller.abort()

    await expect(request).rejects.toThrow('健康检查已取消')
  })

  it('超时按明确文案报告，不与 DOWN 状态混淆', async () => {
    vi.useFakeTimers()
    stub = installHttpStub(() => new Promise(() => undefined))

    const request = expect(fetchReadiness(undefined, 20)).rejects.toThrow('健康检查请求超时')
    await vi.advanceTimersByTimeAsync(20)

    await request
  })
})
