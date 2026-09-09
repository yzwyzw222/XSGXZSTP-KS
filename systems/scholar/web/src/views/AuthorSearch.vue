<template>
  <div class="author-search">
    <h2 class="page-title">作者检索</h2>
    <p class="page-desc">
      按作者浏览论文：左侧选择作者，右侧列出该作者名下的全部论文。
      也可以在「科研分析 → 合作作者」中点击节点直达。
    </p>

    <div class="as-main">
      <aside class="as-side">
        <el-input v-model="search" placeholder="搜索作者姓名" clearable :prefix-icon="Search" />
        <div v-loading="authorLoading" class="author-list">
          <div
            v-for="item in authors"
            :key="item.id"
            class="author-item"
            :class="{ active: item.id === selectedId }"
            @click="selectAuthor(item)"
          >
            <span class="author-name">{{ item.name }}</span>
            <span class="author-count">{{ item.paperCount }}</span>
          </div>
          <el-empty
            v-if="!authorLoading && authors.length === 0"
            description="没有匹配的作者"
            :image-size="80"
          />
        </div>
      </aside>

      <section class="as-content">
        <div class="as-head">
          <span class="as-current">
            {{ selectedName ? `「${selectedName}」名下的论文` : '请在左侧选择作者' }}
          </span>
        </div>

        <div class="table-card" v-loading="loading">
          <el-table :data="rows" style="width: 100%">
            <el-table-column label="论文标题" min-width="320" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="paper-title">{{ row.title }}</span>
              </template>
            </el-table-column>
            <el-table-column label="全部作者" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ (row.authors || []).join('、') || '—' }}</template>
            </el-table-column>
            <el-table-column label="来源" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.venue || '—' }}</template>
            </el-table-column>
            <el-table-column label="年份" width="80">
              <template #default="{ row }">{{ row.year ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="类型" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.paperType || '—' }}</template>
            </el-table-column>
          </el-table>

          <el-empty
            v-if="!loading && !selectedId"
            description="在左侧选择一个作者查看论文"
          />

          <div v-if="totalElements > 0" class="pager">
            <el-pagination
              background
              layout="total, prev, pager, next, sizes"
              :total="totalElements"
              :current-page="page"
              :page-size="size"
              :page-sizes="[10, 20, 50]"
              @current-change="onPageChange"
              @size-change="onSizeChange"
            />
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchAuthors, fetchAuthorPapers } from '../api/author'

const route = useRoute()

const search = ref('')
const authors = ref([])
const authorLoading = ref(false)

const selectedId = ref(null)
const selectedName = ref('')
const rows = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const totalElements = ref(0)

let searchTimer = null
watch(search, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(loadAuthors, 300)
})

async function loadAuthors() {
  authorLoading.value = true
  try {
    authors.value = await fetchAuthors(search.value || undefined)
  } catch (e) {
    ElMessage.error('作者加载失败：' + (e.message || '未知错误'))
  } finally {
    authorLoading.value = false
  }
}

function selectAuthor(item) {
  if (selectedId.value === item.id) return
  selectedId.value = item.id
  selectedName.value = item.name
  page.value = 1
  loadPapers()
}

async function loadPapers() {
  if (!selectedId.value) return
  loading.value = true
  try {
    const res = await fetchAuthorPapers(selectedId.value, page.value, size.value)
    rows.value = res.items || []
    totalElements.value = res.totalElements || 0
  } catch (e) {
    ElMessage.error('论文加载失败：' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

function onPageChange(p) {
  page.value = p
  loadPapers()
}

function onSizeChange(s) {
  size.value = s
  page.value = 1
  loadPapers()
}

// 支持从「合作作者」图点击节点跳转过来（query 带 authorId / name）
onMounted(async () => {
  await loadAuthors()
  const aid = route.query.authorId
  if (!aid) return
  const hit = authors.value.find((a) => a.id === aid)
  if (hit) {
    selectAuthor(hit)
  } else {
    // 列表里找不到时直接按 id 查询
    selectedId.value = String(aid)
    selectedName.value = String(route.query.name || aid)
    loadPapers()
  }
})
</script>

<style scoped>
.author-search {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--ink);
  margin-bottom: 6px;
}

.page-desc {
  color: var(--muted);
  font-size: 13px;
  margin-bottom: 16px;
}

.as-main {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 16px;
  align-items: stretch;
}

/* 左侧作者面板 */
.as-side {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background-color: var(--paper);
  border: 1px solid #1e3a5e;
  border-radius: 8px;
  padding: 12px;
}

.author-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.author-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  color: var(--ink);
  font-size: 13px;
  border: 1px solid transparent;
}

.author-item:hover {
  background-color: var(--raised);
}

.author-item.active {
  background-color: var(--paper-deep);
  border-color: var(--accent);
  color: var(--accent-bright);
}

.author-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.author-count {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--muted);
  background-color: var(--paper-deep);
  border-radius: 10px;
  padding: 1px 8px;
}

.author-item.active .author-count {
  color: var(--accent-bright);
}

/* 右侧论文列表 */
.as-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.as-head {
  display: flex;
  align-items: center;
}

.as-current {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
}

.table-card {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  background-color: var(--paper);
  border: 1px solid #1e3a5e;
  border-radius: 8px;
  padding: 12px;
}

.paper-title {
  color: var(--ink);
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}
</style>
