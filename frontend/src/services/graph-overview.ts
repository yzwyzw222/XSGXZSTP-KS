import { api } from '@/services/api'
import { withQuery } from '@/services/business'
import type { GraphNodeType } from '@/types/api'

export interface GraphCenter { id: string; name: string; type: GraphNodeType; businessId: string }

/** 只调用已存在的查询契约；两跳由后端同时遍历入边与出边，维持300节点上限。 */
export const graphOverviewApi = {
  load: (center: GraphCenter | undefined, signal: AbortSignal): Promise<unknown> => center
    ? api.get(withQuery('/api/v1/graph/subgraph', {
      centerType: center.type, centerId: center.businessId, depth: 2, nodeLimit: 300, includeCoauthors: true,
    }), { signal })
    : api.get('/api/v1/graph/overview', { signal }),
}
