import { onBeforeUnmount, ref } from 'vue'
import { sourceApi } from '@/services/business'
import { toErrorMessage } from '@/services/api'
import type { DataSource } from '@/types/api'

/** 名称用于展示与选择，关联键仍使用接口返回的标识，兼容历史记录。 */
export function useDataSources() {
  const sources = ref<DataSource[]>([])
  const sourceLoading = ref(false)
  const sourceError = ref('')
  let generation = 0
  async function loadSources(): Promise<void> {
    const request = ++generation
    sourceLoading.value = true
    sourceError.value = ''
    try {
      const result = await sourceApi.page(0, 100)
      if (request === generation) sources.value = result.items
    } catch (error) {
      if (request === generation) sourceError.value = toErrorMessage(error)
    } finally {
      if (request === generation) sourceLoading.value = false
    }
  }
  function sourceName(id: number): string {
    const source = sources.value.find((item) => item.id === id)
    return source?.sourceType === 'OPENALEX' ? 'OpenAlex'
      : source?.sourceType === 'CROSSREF' ? 'Crossref' : '来源不可用'
  }
  onBeforeUnmount(() => { generation++ })
  return { sources, sourceLoading, sourceError, loadSources, sourceName }
}
