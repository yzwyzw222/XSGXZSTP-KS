import { onScopeDispose, ref, watch } from 'vue'
import { academicGraphApi, type AuthorGraphQuery, type AuthorGraphResponse } from '@/services/academic-graph'
import { toErrorMessage } from '@/services/api'
import { parseAuthorGraph } from '@/utils/academic-graph'
import { GraphDataError } from '@/utils/graph-presentation'

export function useAuthorGraph(query: () => AuthorGraphQuery | null) {
  const result = ref<AuthorGraphResponse | null>(null)
  const loading = ref(false)
  const error = ref('')
  let sequence = 0
  let controller: AbortController | undefined

  async function load(): Promise<void> {
    const current = ++sequence
    controller?.abort()
    result.value = null
    error.value = ''
    loading.value = false
    const requested = query()
    if (!requested) return
    const activeController = new AbortController()
    controller = activeController
    loading.value = true
    try {
      const response = await academicGraphApi.load(requested, activeController.signal)
      if (sequence === current) result.value = parseAuthorGraph(response, requested)
    } catch (cause) {
      if (sequence === current && !activeController.signal.aborted) error.value = cause instanceof GraphDataError ? cause.message : toErrorMessage(cause)
    } finally {
      if (sequence === current) loading.value = false
    }
  }

  // 切换作者、分类和页面时立即作废旧响应，避免把旧作者的数据展示给新作者。
  watch(query, load, { immediate: true, deep: true })
  onScopeDispose(() => { sequence++; controller?.abort() })
  return { result, loading, error, load }
}
