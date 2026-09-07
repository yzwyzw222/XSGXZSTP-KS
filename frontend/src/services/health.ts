import { AxiosError } from 'axios'

import { httpClient, remainingBudget, requestLifetime } from '@/services/http'

export type HealthStatus = 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN'

export interface HealthResponse {
  status: HealthStatus
}

const validStatuses: HealthStatus[] = ['UP', 'DOWN', 'OUT_OF_SERVICE', 'UNKNOWN']

/**
 * 读取 Actuator 健康分组。
 *
 * 与业务请求的差异（有意保留）：健康检查在 HTTP 503 时仍会返回合法的
 * `{"status":"DOWN"}`，因此这里不按状态码判错，只校验响应体本身。
 * `httpClient` 的 `validateStatus` 恒为 true，业务请求的错误判定仍集中在
 * `services/api.ts`，不受此函数影响。
 */
async function fetchHealth(
  group: 'liveness' | 'readiness' | 'graph',
  signal?: AbortSignal,
  timeoutMs?: number,
): Promise<HealthResponse> {
  const lifetime = requestLifetime(timeoutMs, signal)
  let response
  try {
    response = await httpClient.request<unknown>({
      url: `/actuator/health/${group}`,
      method: 'GET',
      headers: { Accept: 'application/json' },
      timeout: remainingBudget(lifetime.deadline),
      signal: lifetime.signal,
    })
  } catch (error) {
    if (lifetime.timedOut) throw new Error('健康检查请求超时')
    if (error instanceof AxiosError && error.code === 'ERR_CANCELED') {
      throw new Error('健康检查已取消')
    }
    if (error instanceof AxiosError && (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT')) {
      throw new Error('健康检查请求超时')
    }
    throw new Error('健康检查请求失败：网络不可达')
  } finally {
    lifetime.dispose()
  }

  const payload = typeof response.data === 'string' && response.data.trim() === ''
    ? null
    : response.data
  if (typeof payload !== 'object' || payload === null || !('status' in payload)) {
    throw new Error(`健康检查请求失败：HTTP ${response.status}`)
  }
  const status = (payload as { status: unknown }).status
  if (typeof status !== 'string' || !validStatuses.includes(status as HealthStatus)) {
    throw new Error('健康检查响应格式无效')
  }

  return { status: status as HealthStatus }
}

export function fetchLiveness(signal?: AbortSignal, timeoutMs?: number): Promise<HealthResponse> {
  return fetchHealth('liveness', signal, timeoutMs)
}

export function fetchReadiness(signal?: AbortSignal, timeoutMs?: number): Promise<HealthResponse> {
  return fetchHealth('readiness', signal, timeoutMs)
}

export function fetchGraphHealth(signal?: AbortSignal, timeoutMs?: number): Promise<HealthResponse> {
  return fetchHealth('graph', signal, timeoutMs)
}
