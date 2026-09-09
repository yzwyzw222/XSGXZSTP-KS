<template>
  <div class="ai-selection">
    <h2 class="page-title">AI 选文分析</h2>
    <p class="page-desc">
      选择数据来源，输入作者或关键词检索论文，勾选需要的论文；后续可对选中论文进行 AI 关键点分析。
    </p>

    <div class="toolbar">
      <div class="field">
        <label class="field-label" for="ai-source">数据来源</label>
        <el-select
          id="ai-source"
          v-model="source"
          class="source-select"
          placeholder="全部来源"
          clearable
        >
          <el-option
            v-for="s in sources"
            :key="s.name"
            :label="`${s.name}（${s.paperCount}）`"
            :value="s.name"
          />
        </el-select>
      </div>
      <div class="field grow">
        <label class="field-label" for="ai-search">检索内容</label>
        <el-input
          id="ai-search"
          v-model="search"
          placeholder="输入作者或关键词"
          clearable
          :prefix-icon="Search"
          @keyup.enter="onSearch"
        />
      </div>
      <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
    </div>

    <div class="as-main">
      <section class="as-content">
        <div class="table-card" v-loading="loading">
          <el-table
            ref="tableRef"
            :data="rows"
            row-key="id"
            style="width: 100%"
            @select="onSelect"
            @select-all="onSelectAll"
          >
            <el-table-column type="selection" width="42" />
            <el-table-column label="论文标题" min-width="320" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="paper-title">{{ row.title }}</span>
              </template>
            </el-table-column>
            <el-table-column label="作者" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ (row.authors || []).join('、') || '—' }}</template>
            </el-table-column>
            <el-table-column label="来源" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.venue || '—' }}</template>
            </el-table-column>
            <el-table-column label="年份" width="80">
              <template #default="{ row }">{{ row.year ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="数据来源" width="100" show-overflow-tooltip>
              <template #default="{ row }">{{ row.paperType || '—' }}</template>
            </el-table-column>
          </el-table>

          <el-empty
            v-if="!loading && rows.length === 0"
            description="没有匹配的论文，请调整数据来源或检索内容"
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

      <aside class="as-side">
        <div class="side-head">
          <span class="side-title">已选论文</span>
          <span class="side-count">{{ selectedList.length }}</span>
        </div>
        <div class="selected-list">
          <div v-for="item in selectedList" :key="item.id" class="selected-item">
            <span class="selected-name" :title="item.title">{{ item.title }}</span>
            <el-button
              class="remove-btn"
              :icon="Close"
              circle
              size="small"
              text
              aria-label="移除已选论文"
              @click="removeSelected(item)"
            />
          </div>
          <el-empty
            v-if="selectedList.length === 0"
            description="在左侧勾选论文"
            :image-size="60"
          />
        </div>
        <div class="side-footer">
          <el-button
            type="primary"
            class="ai-btn"
            :disabled="selectedList.length === 0"
            @click="onAnalyze"
          >
            AI 分析（{{ selectedList.length }}）
          </el-button>
          <el-button :disabled="selectedList.length === 0" @click="clearSelected">清空</el-button>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted } from 'vue'
import { Search, Close } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchAiSources, fetchAiPapers } from '../api/aiSelection'

const sources = ref([])
const source = ref('')
const search = ref('')
const appliedSearch = ref('')

const tableRef = ref(null)
const rows = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const totalElements = ref(0)

// 已选论文：id -> 行数据（跨页/跨搜索保留）
const selectedMap = ref(new Map())
const selectedList = computed(() => Array.from(selectedMap.value.values()))

async function loadSources() {
  try {
    sources.value = await fetchAiSources()
  } catch (e) {
    ElMessage.error('数据来源加载失败：' + (e.message || '未知错误'))
  }
}

function syncSelection() {
  nextTick(() => {
    const table = tableRef.value
    if (!table) return
    rows.value.forEach((r) => {
      if (selectedMap.value.has(r.id)) {
        table.toggleRowSelection(r, true)
      }
    })
  })
}

async function loadPapers() {
  loading.value = true
  try {
    const res = await fetchAiPapers({
      source: source.value || undefined,
      search: appliedSearch.value || undefined,
      page: page.value,
      size: size.value,
    })
    rows.value = res.items || []
    totalElements.value = res.totalElements || 0
    syncSelection()
  } catch (e) {
    ElMessage.error('论文加载失败：' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

function onSearch() {
  appliedSearch.value = search.value.trim()
  page.value = 1
  loadPapers()
}

function onSelect(selection, row) {
  const checked = selection.some((s) => s.id === row.id)
  if (checked) {
    selectedMap.value.set(row.id, row)
  } else {
    selectedMap.value.delete(row.id)
  }
}

function onSelectAll(selection) {
  rows.value.forEach((r) => {
    if (selection.some((s) => s.id === r.id)) {
      selectedMap.value.set(r.id, r)
    } else {
      selectedMap.value.delete(r.id)
    }
  })
}

function removeSelected(item) {
  selectedMap.value.delete(item.id)
  const row = rows.value.find((r) => r.id === item.id)
  if (row && tableRef.value) {
    tableRef.value.toggleRowSelection(row, false)
  }
}

function clearSelected() {
  selectedMap.value = new Map()
  if (tableRef.value) {
    tableRef.value.clearSelection()
  }
}

function onAnalyze() {
  ElMessage.info(`已选中 ${selectedList.value.length} 篇论文，AI 关键点分析能力即将上线`)
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

onMounted(async () => {
  await loadSources()
  await loadPapers()
})
</script>

<style scoped>
.ai-selection {
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

/* 筛选工具栏 */
.toolbar {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 16px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field.grow {
  flex: 1;
  min-width: 0;
}

.field-label {
  font-size: 12px;
  color: var(--muted);
}

.source-select {
  width: 220px;
}

/* 主区域：左表格 + 右已选面板 */
.as-main {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 16px;
  align-items: stretch;
}

.as-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
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

/* 右侧已选面板 */
.as-side {
  width: 300px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background-color: var(--paper);
  border: 1px solid #1e3a5e;
  border-radius: 8px;
  padding: 12px;
}

.side-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.side-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
}

.side-count {
  font-size: 12px;
  color: var(--accent-bright);
  background-color: var(--paper-deep);
  border-radius: 10px;
  padding: 1px 8px;
}

.selected-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.selected-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  background-color: var(--paper-deep);
}

.selected-item:hover {
  background-color: var(--raised);
}

.selected-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--ink);
}

.remove-btn {
  flex-shrink: 0;
  color: var(--muted);
}

.remove-btn:hover {
  color: var(--danger);
}

.side-footer {
  display: flex;
  gap: 8px;
}

.side-footer .el-button {
  flex: 1;
}

.ai-btn {
  flex: 2;
}

@media (max-width: 1180px) {
  .as-side {
    width: 260px;
  }
}

@media (max-width: 900px) {
  .as-main {
    flex-direction: column;
  }

  .as-side {
    width: 100%;
    max-height: 320px;
  }

  .toolbar {
    flex-wrap: wrap;
  }

  .source-select {
    width: 180px;
  }
}
</style>
