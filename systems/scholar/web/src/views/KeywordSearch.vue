<template>
  <div class="keyword-search">
    <h2 class="page-title">关键词检索</h2>
    <p class="page-desc">
      按研究主题/关键词浏览论文：左侧选择关键词，右侧列出该关键词下的全部论文。
      也可以在「科研分析 → 关键词共现」中点击节点直达。
    </p>

    <div class="ks-main">
      <aside class="ks-side">
        <el-input v-model="search" placeholder="搜索关键词" clearable :prefix-icon="Search" />
        <div v-loading="kwLoading" class="kw-list">
          <div
            v-for="item in keywords"
            :key="item.id"
            class="kw-item"
            :class="{ active: item.id === selectedId }"
            @click="selectKeyword(item)"
          >
            <span class="kw-name">{{ item.name }}</span>
            <span class="kw-count">{{ item.paperCount }}</span>
          </div>
          <el-empty
            v-if="!kwLoading && keywords.length === 0"
            description="没有匹配的关键词"
            :image-size="80"
          />
        </div>
      </aside>

      <section class="ks-content">
        <div class="ks-head">
          <span class="ks-current">
            {{ selectedName ? `「${selectedName}」下的论文` : '请在左侧选择关键词' }}
          </span>
        </div>

        <div class="table-card" v-loading="loading">
          <el-table :data="rows" style="width: 100%">
            <el-table-column label="论文标题" min-width="320" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="paper-title">{{ row.title }}</span>
              </template>
            </el-table-column>
            <el-table-column label="作者" min-width="200" show-overflow-tooltip>
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
            description="在左侧选择一个关键词查看论文"
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
import { fetchKeywords, fetchKeywordPapers } from '../api/keyword'

const route = useRoute()

const search = ref('')
const keywords = ref([])
const kwLoading = ref(false)

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
  searchTimer = setTimeout(loadKeywords, 300)
})

async function loadKeywords() {
  kwLoading.value = true
  try {
    keywords.value = await fetchKeywords(search.value || undefined)
  } catch (e) {
    ElMessage.error('关键词加载失败：' + (e.message || '未知错误'))
  } finally {
    kwLoading.value = false
  }
}

function selectKeyword(item) {
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
    const res = await fetchKeywordPapers(selectedId.value, page.value, size.value)
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

// 支持从「关键词共现」图点击节点跳转过来（query 带 topicId / name）
onMounted(async () => {
  await loadKeywords()
  const tid = route.query.topicId
  if (!tid) return
  const hit = keywords.value.find((k) => k.id === tid)
  if (hit) {
    selectKeyword(hit)
  } else {
    // 列表里找不到（比如被搜索过滤）时直接按 id 查询
    selectedId.value = String(tid)
    selectedName.value = String(route.query.name || tid)
    loadPapers()
  }
})
</script>

<style scoped>
.keyword-search {
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

.ks-main {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 16px;
  align-items: stretch;
}

/* 左侧关键词面板 */
.ks-side {
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

.kw-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.kw-item {
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

.kw-item:hover {
  background-color: var(--raised);
}

.kw-item.active {
  background-color: var(--paper-deep);
  border-color: var(--accent);
  color: var(--accent-bright);
}

.kw-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kw-count {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--muted);
  background-color: var(--paper-deep);
  border-radius: 10px;
  padding: 1px 8px;
}

.kw-item.active .kw-count {
  color: var(--accent-bright);
}

/* 右侧论文列表 */
.ks-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ks-head {
  display: flex;
  align-items: center;
}

.ks-current {
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
