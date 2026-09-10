export interface PlatformLog {
  id: string
  createdAt: string
  system: string
  username: string
  method: string
  resource: string
  status: number
  durationMs: number
}
export interface PlatformLogPage {
  items: PlatformLog[]
  page: number
  totalPages: number
  totalElements: number
  retentionLimit: number
}

/** 平台日志由统一网关查询，保持请求取消、超时及认证错误可见。 */
export async function getPlatformLogs(filters: Record<string, string>, page: number, signal: AbortSignal): Promise<PlatformLogPage> {
  const query = new URLSearchParams({ ...filters, page: String(page), size: '20' })
  const response = await fetch(`/__integration/platform/logs?${query}`, {
    credentials: 'same-origin', signal: AbortSignal.any([signal, AbortSignal.timeout(8000)]),
  })
  const data = await response.json()
  if (response.status === 401) window.location.replace('/login')
  if (!response.ok) throw new Error(data?.detail || '平台日志加载失败。')
  if (!Array.isArray(data.items)) throw new Error('平台日志返回格式无效。')
  return data
}
