import { api } from '@/services/api'
import type { AuditFilter, AuditLog, PageResponse } from '@/types/api'

/** 时间范围使用起点包含、终点不包含的 ISO 时间，筛选由后端执行。 */
export function getAudits(filter: AuditFilter = {}, page = 0, size = 20, signal?: AbortSignal) {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  Object.entries(filter).forEach(([key, value]) => { if (value) query.set(key, value) })
  return api.get<PageResponse<AuditLog>>(`/api/v1/operations/audits?${query}`, { signal })
}
