<template>
  <div class="inst-search">
    <h2 class="page-title">机构检索</h2>
    <p class="page-desc">
      按机构浏览论文：左侧选择机构，右侧列出该机构参与的论文（按署名机构解析）。
      也可以在「科研分析 → 机构合作」中点击节点直达。
    </p>

    <div class="is-main">
      <aside class="is-side">
        <el-input v-model="search" placeholder="搜索机构名称" clearable :prefix-icon="Search" />
        <div v-loading="instLoading" class="inst-list">
          <div
            v-for="item in institutions"
            :key="item.id"
            class="inst-item"
            :class="{ active: item.id === selectedId }"
            @click="selectInstitution(item)"
          >
            <span class="inst-name">{{ item.name }}</span>
            <span class="inst-count">{{ item.paperCount }}</span>
          </div>
          <el-empty
            v-if="!instLoading && institutions.length === 0"
            description="没有匹配的机构"
            :image-size="80"
          />
        </div>
      </aside>

      <section class="is-content">
        <div class="is-head">
          <span class="is-current">
            {{ selectedName ? `「${selectedName}」参与的论文` : '请在左侧选择机构' }}
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
            description="在左侧选择一个机构查看论文"
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
import { fetchInstitutions, fetchInstitutionPapers } from '../api/institution'

const route = useRoute()

const search = ref('')
const institutions = ref([])
const instLoading = ref(false)

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
  searchTimer = setTimeout(loadInstitutions, 300)
})

async function loadInstitutions() {
  instLoading.value = true
  try {
    institutions.value = await fetchInstitutions(search.value || undefined)
  } catch (e) {
    ElMessage.error('机构加载失败：' + (e.message || '未知错误'))
  } finally {
    instLoading.value = false
  }
}

function selectInstitution(item) {
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
    const res = await fetchInstitutionPapers(selectedId.value, page.value, size.value)
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

// 支持从「机构合作」图点击节点跳转过来：图节点 id 即机构名（query.name）
onMounted(async () => {
  const name = route.query.name ? String(route.query.name) : ''
  if (name) {
    search.value = name
  }
  await loadInstitutions()
  if (!name) return
  const hit = institutions.value.find((i) => i.name === name)
  if (hit) {
    selectInstitution(hit)
  } else if (institutions.value.length === 1) {
    selectInstitution(institutions.value[0])
  }
})
</script>

<style scoped>
.inst-search {
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

.is-main {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 16px;
  align-items: stretch;
}

/* 左侧机构面板 */
.is-side {
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

.inst-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.inst-item {
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

.inst-item:hover {
  background-color: var(--raised);
}

.inst-item.active {
  background-color: var(--paper-deep);
  border-color: var(--accent);
  color: var(--accent-bright);
}

.inst-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.inst-count {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--muted);
  background-color: var(--paper-deep);
  border-radius: 10px;
  padding: 1px 8px;
}

.inst-item.active .inst-count {
  color: var(--accent-bright);
}

/* 右侧论文列表 */
.is-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.is-head {
  display: flex;
  align-items: center;
}

.is-current {
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
