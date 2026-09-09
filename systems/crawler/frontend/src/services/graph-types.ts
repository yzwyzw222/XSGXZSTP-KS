import { api } from '@/services/api'
import type { GraphTypeDefinition } from '@/types/api'

/** 类型配置只经业务接口读取和保存，沿用统一会话、CSRF 与错误转换。 */
export const graphTypesApi = {
  list: () => api.get<GraphTypeDefinition[]>('/api/v1/graph/types'),
  update: (value: GraphTypeDefinition) => api.put<GraphTypeDefinition>(
    `/api/v1/graph/types/${encodeURIComponent(value.kind)}/${encodeURIComponent(value.code)}`,
    { displayName: value.displayName, color: value.color, size: value.size,
      reviewStatus: value.reviewStatus, version: value.version },
  ),
}
