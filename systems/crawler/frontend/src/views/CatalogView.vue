<script setup lang="ts">
import { ElAlert, ElButton, ElPopover, ElTag } from 'element-plus'
import { Download } from 'lucide-vue-next'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import {
  DataTable, StatusPill,
} from '@/components/business'
import SplitWorkspace from '@/components/business/SplitWorkspace.vue'
import AchievementPreview from '@/components/business/AchievementPreview.vue'
import CompactFieldSearch from '@/components/business/CompactFieldSearch.vue'
import YearPicker from '@/components/business/YearPicker.vue'
import type { DataTableColumn } from '@/components/business/types'
import { useSessionCleanup } from '@/composables/useSessionCleanup'
import { toErrorMessage } from '@/services/api'
import { catalogApi, exportApi, type AchievementQuery } from '@/services/business'
import { useSessionStore } from '@/stores/session'
import type { AchievementSummary, CatalogCollection, ExportFormat, ExportTask, PageResponse } from '@/types/api'
import { resolveExportFilter } from '@/utils/export-filter'
import { catalogRouteQuery, readCatalogQuery } from '@/utils/catalog-query'
import { formatDateTime } from '@/utils/format'

const sessionStore = useSessionStore()
const { hasPermission } = sessionStore

const previewId = ref<number | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const exportCreating = ref<ExportFormat | null>(null)
const exportDownloading = ref(false)
const exportErrorMessage = ref('')
const exportTask = ref<ExportTask | null>(null)
let exportPollTimer: number | undefined
let disposed = false
let querySequence = 0
onBeforeUnmount(() => { querySequence++ })
const result = ref<PageResponse<AchievementSummary>>({
  items: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
})

const route = useRoute()
const router = useRouter()
const filters = reactive(readCatalogQuery(route.query))
const resultQuery = ref(readCatalogQuery(route.query))
const filtersChanged = computed(() => JSON.stringify(resolveExportFilter(filters)) !== JSON.stringify(resolveExportFilter(resultQuery.value)))
const detailQuery = computed(() => catalogRouteQuery(resultQuery.value))

const searchFields = [
  { value: 'title', label: '题名' }, { value: 'author', label: '作者', collection: 'authors' },
  { value: 'organization', label: '机构', collection: 'organizations' },
  { value: 'venue', label: '期刊', collection: 'venues' }, { value: 'topic', label: '主题', collection: 'topics' },
] as const satisfies readonly { value: string; label: string; collection?: CatalogCollection }[]
type SearchField = typeof searchFields[number]['value']
const searchField = ref<SearchField>('title')
const searchIds = { author: 'authorId', organization: 'organizationId', venue: 'venueId', topic: 'topicId' } as const
const searchText = computed({ get: () => filters[searchField.value] ?? '', set: (value: string) => { filters[searchField.value] = value } })
const searchEntityId = computed({
  get: () => searchField.value === 'title' ? undefined : filters[searchIds[searchField.value]],
  set: (value: number | undefined) => { if (searchField.value !== 'title') filters[searchIds[searchField.value]] = value },
})
const appliedConditions = computed(() => searchFields.flatMap(field => {
  const value = resultQuery.value[field.value]
  const id = field.value === 'title' ? undefined : resultQuery.value[searchIds[field.value]]
  return value || id ? [`${field.label}：${value || `#${id}`}${value && id ? ` (#${id})` : ''}`] : []
}))
function changeSearchField(value: string): void {
  for (const field of searchFields) filters[field.value] = ''
  for (const key of Object.values(searchIds)) filters[key] = undefined
  searchField.value = value as SearchField
}
function changeYear(value: number | undefined): void { filters.publicationYear = value; void load() }

const columns: DataTableColumn<AchievementSummary>[] = [
  { accessorKey: 'title', header: '题名', enableSorting: false, meta: { minWidth: 320 } },
  { id: 'authors', accessorFn: (row) => row.authors.join('；'), header: '作者', enableSorting: false },
  { accessorKey: 'publicationDate', header: '发表日期', enableSorting: false, meta: { width: 160 } },
  { accessorKey: 'primaryVenue', header: '期刊/来源', enableSorting: false },
  { id: 'topics', accessorFn: (row) => row.topics.join('，'), header: '主题', enableSorting: false },
]

async function load(page = 0): Promise<void> {
  const next = catalogRouteQuery({ ...filters, page, size: result.value.size })
  const target = router.resolve({ path: '/catalog', query: next })
  if (target.fullPath === route.fullPath) await fetchResults(readCatalogQuery(route.query))
  else await router.push(target)
}

async function fetchResults(query: AchievementQuery): Promise<void> {
  const sequence = ++querySequence
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await catalogApi.achievements(query)
    if (sequence === querySequence) { result.value = response; resultQuery.value = { ...query } }
  } catch (error) {
    if (sequence === querySequence) errorMessage.value = toErrorMessage(error)
  } finally {
    if (sequence === querySequence) loading.value = false
  }
}

function reset(): void {
  Object.assign(filters, readCatalogQuery({}))
  void load()
}

async function createExport(format: ExportFormat): Promise<void> {
  clearExportPolling()
  exportCreating.value = format
  exportErrorMessage.value = ''
  exportTask.value = null
  try {
    const exportFilters = resolveExportFilter(resultQuery.value)
    if (disposed) return
    const task = await exportApi.create(format, exportFilters)
    if (disposed) return
    exportTask.value = task
    scheduleExportPoll(task)
  } catch (error) {
    exportErrorMessage.value = toErrorMessage(error)
  } finally {
    exportCreating.value = null
  }
}

function scheduleExportPoll(task: ExportTask): void {
  if (disposed || isTerminal(task.status)) return
  exportPollTimer = window.setTimeout(() => void pollExport(task.id), 800)
}

async function pollExport(exportId: string): Promise<void> {
  try {
    const task = await exportApi.get(exportId)
    // 离开页面或换了一个导出任务后，迟到的轮询结果不得覆盖当前票据。
    if (exportTask.value?.id !== exportId) return
    exportTask.value = task
    scheduleExportPoll(task)
  } catch (error) {
    exportErrorMessage.value = toErrorMessage(error)
  }
}

async function downloadExport(): Promise<void> {
  const task = exportTask.value
  if (!task?.downloadAvailable || !task.downloadToken) return
  exportDownloading.value = true
  exportErrorMessage.value = ''
  try {
    const downloaded = await exportApi.download(task)
    if (disposed) return
    const objectUrl = URL.createObjectURL(downloaded)
    const anchor = document.createElement('a')
    anchor.href = objectUrl
    anchor.download = `aacv-achievements-${task.id}.${task.format.toLowerCase()}`
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(objectUrl)
  } catch (error) {
    exportErrorMessage.value = toErrorMessage(error)
  } finally {
    exportDownloading.value = false
  }
}

function clearExportPolling(): void {
  if (exportPollTimer !== undefined) {
    window.clearTimeout(exportPollTimer)
    exportPollTimer = undefined
  }
}

function isTerminal(status: ExportTask['status']): boolean {
  return status === 'SUCCEEDED' || status === 'FAILED' || status === 'EXPIRED'
}

function exportStatusText(status: ExportTask['status']): string {
  return {
    PENDING: '等待处理', RUNNING: '正在生成', SUCCEEDED: '导出完成',
    FAILED: '导出失败', EXPIRED: '文件已过期',
  }[status]
}

watch(() => route.query, (routeQuery) => {
  if (route.name !== 'catalog') return
  const query = readCatalogQuery(routeQuery)
  Object.assign(filters, query)
  searchField.value = searchFields.find(field => query[field.value] || (field.value !== 'title' && query[searchIds[field.value]]))?.value ?? 'title'
  void fetchResults(query)
}, { immediate: true })
function disposeExport(): void {
  disposed = true
  clearExportPolling()
  exportTask.value = null
}
onBeforeUnmount(disposeExport)
// 退出登录或会话失效时停止导出轮询，避免继续用已失效的会话请求。
useSessionCleanup(disposeExport)
</script>

<template>
  <section class="page-stack catalog-page">
    <header class="catalog-toolbar">
      <div class="catalog-heading"><h1>成果目录</h1><span>{{ loading ? '正在读取…' : `共 ${result.totalElements.toLocaleString('zh-CN')} 条` }}</span></div>
      <div class="catalog-actions">
        <CompactFieldSearch v-model="searchText" v-model:entity-id="searchEntityId" :field="searchField" :fields="searchFields" :loading="loading" @update:field="changeSearchField" @submit="load()" />
        <ElButton text size="small" @click="reset">重置</ElButton>
        <ElPopover v-if="hasPermission('EXPORT_CREATE')" trigger="click" placement="bottom-end" :width="350">
          <template #reference><ElButton size="small" aria-label="导出成果"><Download :size="14" class="mr-1" />导出</ElButton></template>
          <div class="catalog-export" aria-live="polite">
            <p class="text-xs text-muted-foreground">导出当前查询结果的全部记录</p>
            <div class="flex gap-2 my-3"><ElButton size="small" :loading="exportCreating === 'CSV'" :disabled="exportCreating !== null || loading || !!errorMessage" @click="createExport('CSV')">导出 CSV</ElButton><ElButton size="small" :loading="exportCreating === 'JSON'" :disabled="exportCreating !== null || loading || !!errorMessage" @click="createExport('JSON')">导出 JSON</ElButton></div>
            <ElAlert v-if="exportErrorMessage" type="error" :closable="false" :title="exportErrorMessage" />
            <template v-if="exportTask">
              <div class="flex items-center gap-2"><strong>{{ exportStatusText(exportTask.status) }}</strong><StatusPill :status="exportTask.status" /></div>
              <p class="text-xs my-2">任务 {{ exportTask.id }} · {{ exportTask.exportedCount }} / {{ exportTask.requestedCount }} 条</p>
              <p class="text-xs text-muted-foreground">创建 {{ formatDateTime(exportTask.createdAt) }} · 到期 {{ formatDateTime(exportTask.expiresAt) }}</p>
              <p v-if="exportTask.errorMessage" class="text-xs text-destructive">{{ exportTask.errorMessage }}</p>
              <ElButton v-if="exportTask.downloadAvailable && exportTask.downloadToken" type="primary" size="small" class="mt-3" :loading="exportDownloading" @click="downloadExport">下载文件</ElButton>
            </template>
          </div>
        </ElPopover>
      </div>
    </header>
    <p v-if="appliedConditions.length" class="catalog-scope">当前结果：{{ appliedConditions.join(' · ') }}</p>
    <p v-if="filtersChanged" class="catalog-scope" role="status">筛选条件已修改，搜索后更新结果；导出仍使用当前结果的条件。</p>
    <ElAlert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" show-icon />
    <SplitWorkspace :open="previewId !== null" title="成果快速预览" @update:open="previewId = null">
    <section class="catalog-workspace" aria-label="成果检索工作区">
      <DataTable fill
        :columns="columns"
        :data="result.items"
        :loading="loading"
        :page="result.page"
        :size="result.size"
        :total="result.totalElements"
        empty-text="暂无符合条件的成果"
        empty-description="尝试减少筛选条件，或在作者导入中补充学者信息表。"
        :get-row-id="(row) => String(row.id)"
        @update:page="load"
      >
        <template #header-publicationDate><span class="date-heading">发表日期 <YearPicker :model-value="filters.publicationYear" compact aria-label="按发表年份筛选" @update:model-value="changeYear" /></span></template>
        <template #cell-title="{ row }">
          <RouterLink class="catalog-title" :to="{ path: `/catalog/achievements/${row.id}`, query: detailQuery }">
            {{ row.title }}
          </RouterLink>
          <div class="flex items-center justify-between gap-2"><span class="mono-evidence text-xs text-muted-foreground">{{ row.doi || '无 DOI' }}</span><ElButton link type="primary" :aria-label="`预览成果：${row.title}`" @click="previewId = row.id">预览</ElButton></div>
        </template>
        <template #cell-authors="{ value }">
          <span class="text-muted-foreground">{{ value || '—' }}</span>
        </template>
        <template #cell-topics="{ row }">
          <div class="flex flex-wrap gap-1">
            <ElTag v-for="topic in row.topics.slice(0, 2)" :key="topic" size="small" type="info" effect="plain">
              {{ topic }}
            </ElTag>
            <span v-if="!row.topics.length" class="text-muted-foreground">—</span>
          </div>
        </template>
      </DataTable>
    </section>
    <template #detail><AchievementPreview v-if="previewId !== null" :id="previewId" :return-query="detailQuery" /></template>
    </SplitWorkspace>
  </section>
</template>

<style scoped>
.catalog-page { padding: 8px 14px; gap: 7px; }
.catalog-toolbar, .catalog-actions, .catalog-heading { display: flex; align-items: center; gap: 9px; min-width: 0; }
.catalog-toolbar { justify-content: space-between; flex-wrap: wrap; }
.catalog-heading h1 { font-size: 17px; font-weight: 650; white-space: nowrap; }
.catalog-heading > span, .catalog-scope { font-size: 11px; color: hsl(var(--muted-foreground)); }
.catalog-actions { justify-content: flex-end; }
.catalog-actions > .el-button + .el-button { margin-left: 0; }
.catalog-workspace > .data-table { flex: 1; min-height: 0; }
.date-heading { display: flex; align-items: center; gap: 5px; }
@media (max-width: 700px) {
  .catalog-page { padding: 8px; }
  .catalog-toolbar { gap: 7px; }
  .catalog-actions { width: 100%; gap: 4px; }
  .catalog-actions > .compact-search { flex: 1; }
}
</style>
