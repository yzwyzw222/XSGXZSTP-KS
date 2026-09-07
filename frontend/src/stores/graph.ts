import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

import { toErrorMessage } from '@/services/api'
import { useSessionStore } from '@/stores/session'
import type { GraphResponse } from '@/types/api'
import { mergeGraph } from '@/utils/graph'
import { graphFilterSchema, type GraphFilters } from '@/utils/graph-query'

/** 图浏览与路径分析共用的结果状态；画布实例与请求句柄仍由生命周期层管理。 */
export const useGraphStore = defineStore('graph', () => {
  const graph = ref<GraphResponse | null>(null)
  const loading = ref(false)
  const errorMessage = ref('')
  const addedNodeIds = ref<string[]>([])
  const focus = ref<{ nodeId: string; seq: number } | null>(null)
  const pendingQuery = ref<GraphFilters | null>(null)
  let sequence = 0

  function reset(): void {
    sequence += 1
    graph.value = null
    loading.value = false
    errorMessage.value = ''
    addedNodeIds.value = []
    focus.value = null
  }

  /** 新查询、页面离开或账号切换使旧响应失效，合并统一遵守 300 节点约束。 */
  async function load(request: () => Promise<GraphResponse>, merge: boolean): Promise<void> {
    const current = ++sequence
    loading.value = true
    errorMessage.value = ''
    try {
      const response = await request()
      if (current !== sequence) return
      const oldIds = new Set(graph.value?.nodes.map((node) => node.id) ?? [])
      const next = mergeGraph(merge ? graph.value : null, response)
      addedNodeIds.value = merge ? next.nodes.filter((node) => !oldIds.has(node.id)).map((node) => node.id) : []
      graph.value = next
      focus.value = { nodeId: response.rootNodeId, seq: current }
    } catch (error) {
      if (current === sequence) errorMessage.value = toErrorMessage(error)
    } finally {
      if (current === sequence) loading.value = false
    }
  }

  const session = useSessionStore()
  watch(() => session.currentUserId, () => { reset(); pendingQuery.value = null }, { flush: 'sync' })

  /** 跨页面交接只保留在当前账号内存中，读取即消费，不向存储写入临时查询。 */
  function stageQuery(filters: GraphFilters): void {
    pendingQuery.value = graphFilterSchema.parse(filters)
  }
  function takeQuery(): GraphFilters | null {
    const value = pendingQuery.value
    pendingQuery.value = null
    return value
  }
  return { graph, loading, errorMessage, addedNodeIds, focus, pendingQuery, load, reset, stageQuery, takeQuery }
})
