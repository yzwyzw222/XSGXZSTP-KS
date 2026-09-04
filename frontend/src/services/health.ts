export type HealthStatus = 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN'

export interface HealthResponse {
  status: HealthStatus
}

const validStatuses: HealthStatus[] = ['UP', 'DOWN', 'OUT_OF_SERVICE', 'UNKNOWN']

async function fetchHealth(group: 'liveness' | 'readiness' | 'graph', signal?: AbortSignal): Promise<HealthResponse> {
  const response = await fetch(`/actuator/health/${group}`, {
    headers: { Accept: 'application/json' },
    signal,
  })

  let payload: unknown
  try {
    payload = await response.json()
  } catch {
    throw new Error(`健康检查请求失败：HTTP ${response.status}`)
  }
  if (typeof payload !== 'object' || payload === null || !('status' in payload)) {
    throw new Error('健康检查响应格式无效')
  }
  const status = payload.status
  if (typeof status !== 'string' || !validStatuses.includes(status as HealthStatus)) {
    throw new Error('健康检查响应格式无效')
  }

  return { status: status as HealthStatus }
}

export function fetchLiveness(signal?: AbortSignal): Promise<HealthResponse> {
  return fetchHealth('liveness', signal)
}

export function fetchReadiness(signal?: AbortSignal): Promise<HealthResponse> {
  return fetchHealth('readiness', signal)
}

export function fetchGraphHealth(signal?: AbortSignal): Promise<HealthResponse> {
  return fetchHealth('graph', signal)
}
