<template>
  <div class="author-search">
    <h2 class="page-title">学者检索</h2>
    <div class="author-search__controls">
      <SearchBar v-model="keyword" placeholder="输入学者姓名..." @search="doSearch" />
      <el-checkbox v-model="fetchRemote">从 Semantic Scholar 远程搜索</el-checkbox>
    </div>
    <AuthorList :authors="items" :loading="loading" />
    <Pagination
      :page="page" :size="size"
      :total-elements="totalElements" :total-pages="totalPages"
      @update:page="onPageChange"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { searchAuthors } from '../api/author.js'
import { usePagination } from '../composables/usePagination.js'
import SearchBar from '../components/common/SearchBar.vue'
import AuthorList from '../components/author/AuthorList.vue'
import Pagination from '../components/common/Pagination.vue'

const keyword = ref('')
const fetchRemote = ref(false)
const { items, page, size, totalElements, totalPages, loading, applyPageResponse } = usePagination()

async function doSearch() {
  loading.value = true
  try {
    const res = await searchAuthors({
      name: keyword.value || '',
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
.author-search {
  &__controls {
    display: flex;
    align-items: center;
    gap: var(--spacing-md);
    margin-bottom: var(--spacing-md);
    flex-wrap: wrap;
  }
}

.page-title {
  color: var(--text-primary);
  font-size: 1.25rem;
  margin: 0 0 var(--spacing-md);
}
</style>
