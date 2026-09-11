import { defineStore } from 'pinia'
import { computed, onScopeDispose, reactive, ref, watch } from 'vue'

import { toErrorMessage } from '@/services/api'
import { graphOverviewApi, type GraphCenter } from '@/services/graph-overview'
import { useSessionStore } from '@/stores/session'
import type { GraphNode, GraphResponse } from '@/types/api'
import { filterOverview } from '@/utils/graph-overview'
import { cooperationEvidence, cooperationGraph } from '@/utils/graph-cooperation'
import { GraphDataError, parseGraphResponse } from '@/utils/graph-presentation'

/** 概览业务数据的唯一来源；请求句柄只保存在闭包中。 */
export const useGraphOverviewStore = defineStore('graph-overview', () => {
  const graph = ref<GraphResponse | null>(null)
  const loading = ref(false)
  const errorMessage = ref('')
  const filters = reactive({ keyword: '', nodeType: '', relationship: '' })
  const history = ref<GraphCenter[]>([])
  const selection = ref<{ kind: 'node' | 'edge'; id: string } | null>(null)
  const focusedCooperationId = ref('')
  const cooperations = computed(() => graph.value ? cooperationEvidence(graph.value) : [])
  const focusedCooperation = computed(() => cooperations.value.find(item => item.edge.id === focusedCooperationId.value))
  const visible = computed(() => {
    if (!graph.value) return null
    if (focusedCooperation.value) return cooperationGraph(graph.value, [focusedCooperation.value])
    const { keyword, nodeType, relationship } = filters
    return filterOverview(graph.value, keyword, nodeType, relationship, true)
  })
  let sequence = 0
  let controller: AbortController | undefined
  let targetHistory: GraphCenter[] = []

  function invalidate(): void {
    sequence++
    controller?.abort()
    controller = undefined
  }

  /** 提交成功响应时同时切换数据和导航，失败保留上次可核对的结果。 */
  async function refresh(path = targetHistory): Promise<void> {
    invalidate()
    const current = sequence
    targetHistory = [...path]
    const requestedHistory = [...path]
    controller = new AbortController()
    loading.value = true
    errorMessage.value = ''
    try {
      const response = await graphOverviewApi.load(requestedHistory.at(-1), controller.signal)
      if (current !== sequence) return
      const next = parseGraphResponse(response)
      graph.value = next
      history.value = requestedHistory
      if (selection.value && !(selection.value.kind === 'node' ? visible.value?.nodes : visible.value?.edges)?.some(item => item.id === selection.value?.id)) selection.value = null
      if (!cooperations.value.some(item => item.edge.id === focusedCooperationId.value)) focusedCooperationId.value = ''
    } catch (error) {
      if (current !== sequence) return
      targetHistory = [...history.value]
      errorMessage.value = error instanceof GraphDataError ? error.message : toErrorMessage(error)
    } finally {
      if (current === sequence) { loading.value = false; controller = undefined }
    }
  }

  /** 历史重复节点直接回退并截断后续记录，不把当前局部图用于计算两跳。 */
  async function enterNode(node: GraphNode): Promise<void> {
    const index = history.value.findIndex(item => item.id === node.id)
    const path = index >= 0 ? history.value.slice(0, index + 1) : [...history.value, {
      id: node.id, name: node.label, type: node.type, businessId: node.businessId,
    }]
    clearSelection()
    await refresh(path)
  }

  /** -1 表示全部；失败时不提前改变当前导航。 */
  async function goTo(index: number): Promise<void> {
    if (!Number.isInteger(index) || index < -1 || index >= history.value.length) return
    clearSelection()
    await refresh(history.value.slice(0, index + 1))
  }

  function clearSelection(): void { selection.value = null; focusedCooperationId.value = '' }
  function select(kind: 'node' | 'edge', id: string): void {
    selection.value = { kind, id }
  }

  // 筛选只作用于已读取的图，不取消加载，也不重复请求相同数据。
  watch(filters, clearSelection, { flush: 'sync' })

  function reset(): void {
    Object.assign(filters, { keyword: '', nodeType: '', relationship: '' })
    invalidate()
    graph.value = null
    history.value = []
    targetHistory = []
    loading.value = false
    errorMessage.value = ''
    clearSelection()
  }
  const session = useSessionStore()
  watch(() => session.currentUserId, reset, { flush: 'sync' })
  onScopeDispose(invalidate)
  return { graph, visible, cooperations, focusedCooperation, focusedCooperationId, filters,
    history, loading, errorMessage, selection, refresh, enterNode, goTo, select, clearSelection, reset }
})
