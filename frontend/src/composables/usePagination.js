import { ref, computed } from 'vue'

export function usePagination(initialSize = 20) {
  const page = ref(0)
  const size = ref(initialSize)
  const totalElements = ref(0)
  const totalPages = ref(0)
  const items = ref([])
  const loading = ref(false)

  const paginationInfo = computed(() => ({
    page: page.value,
    size: size.value,
    totalElements: totalElements.value,
    totalPages: totalPages.value
  }))

  function applyPageResponse(res) {
    items.value = res.items || []
    totalElements.value = res.totalElements || 0
    totalPages.value = res.totalPages || 0
    page.value = res.page || 0
  }

  function nextPage() {
    if (page.value < totalPages.value - 1) {
      page.value++
      return true
    }
    return false
  }

  function prevPage() {
    if (page.value > 0) {
      page.value--
      return true
    }
    return false
  }

  return { page, size, totalElements, totalPages, items, loading, paginationInfo, applyPageResponse, nextPage, prevPage }
}
