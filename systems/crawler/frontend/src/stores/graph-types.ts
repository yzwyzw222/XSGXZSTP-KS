import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

import { toErrorMessage } from '@/services/api'
import { graphTypesApi } from '@/services/graph-types'
import { useSessionStore } from '@/stores/session'
import type { GraphTypeDefinition } from '@/types/api'

export const useGraphTypesStore = defineStore('graph-types', () => {
  const definitions = ref<GraphTypeDefinition[]>([])
  const loading = ref(false)
  const saving = ref(false)
  const error = ref('')
  let sequence = 0

  function reset(): void {
    sequence++
    definitions.value = []
    loading.value = false
    saving.value = false
    error.value = ''
  }

  /** 请求完成前切换页面或账号时，忽略已经失效的响应。 */
  async function load(): Promise<void> {
    if (saving.value) return
    const current = ++sequence
    loading.value = true
    error.value = ''
    try {
      const result = await graphTypesApi.list()
      if (current === sequence) definitions.value = result
    } catch (cause) {
      if (current === sequence) error.value = toErrorMessage(cause)
    } finally {
      if (current === sequence) loading.value = false
    }
  }

  /** 保存原版本；冲突保留编辑内容，让用户明确刷新后再操作。 */
  async function save(value: GraphTypeDefinition): Promise<boolean> {
    if (saving.value || loading.value) return false
    const current = ++sequence
    saving.value = true
    error.value = ''
    try {
      const result = await graphTypesApi.update(value)
      if (current !== sequence) return false
      definitions.value = definitions.value.map(item => item.kind === result.kind && item.code === result.code ? result : item)
      return true
    } catch (cause) {
      if (current === sequence) error.value = toErrorMessage(cause)
      return false
    } finally {
      if (current === sequence) saving.value = false
    }
  }

  const session = useSessionStore()
  watch(() => session.currentUserId, reset, { flush: 'sync' })
  return { definitions, loading, saving, error, load, save, reset }
})
