<template>
  <div class="paper-search">
    <h2 class="page-title">论文搜索</h2>
    <div class="paper-search__controls">
      <SearchBar v-model="keyword" placeholder="搜索论文标题或关键词..." @search="doSearch" />
      <el-checkbox v-model="fetchRemote" class="paper-search__remote">
        从 Semantic Scholar 远程搜索
      </el-checkbox>
    </div>
    <PaperList :papers="items" :loading="loading" />
    <Pagination
      :page="page"
      :size="size"
      :total-elements="totalElements"
      :total-pages="totalPages"
      @update:page="onPageChange"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { searchPapers } from '../api/paper.js'
import { usePagination } from '../composables/usePagination.js'
import SearchBar from '../components/common/SearchBar.vue'
import PaperList from '../components/paper/PaperList.vue'
import Pagination from '../components/common/Pagination.vue'

const keyword = ref('')
const fetchRemote = ref(false)
const { items, page, size, totalElements, totalPages, loading, applyPageResponse } = usePagination()

async function doSearch() {
  loading.value = true
  try {
    const res = await searchPapers({
      keyword: keyword.value,
      page: page.value,
      size: size.value,
      fetchRemote: fetchRemote.value || undefined
    })
    applyPageResponse(res)
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

function onPageChange(newPage) {
  page.value = newPage
  doSearch()
}

onMounted(() => {
  doSearch()
})
</script>

<style lang="scss" scoped>
.paper-search {
  &__controls {
    display: flex;
    align-items: center;
    gap: var(--spacing-md);
    margin-bottom: var(--spacing-md);
    flex-wrap: wrap;
  }

  &__remote {
    color: var(--text-secondary);
    white-space: nowrap;
  }
}

.page-title {
  color: var(--text-primary);
  font-size: 1.25rem;
  margin: 0 0 var(--spacing-md);
}
</style>
