<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElInput,
  ElInputNumber,
  ElPagination,
  ElTable,
  ElTableColumn,
  ElTag,
  vLoading,
} from 'element-plus'

import { catalogApi, exportApi, type AchievementQuery } from '@/services/business'
import { toErrorMessage } from '@/services/api'
import { hasPermission } from '@/services/session'
import type { AchievementSummary, ExportFormat, ExportTask, PageResponse } from '@/types/api'
import {
  ExportFilterResolutionError,
  resolveExportFilter,
} from '@/utils/export-filter'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const errorMessage = ref('')
const exportCreating = ref<ExportFormat | null>(null)
const exportDownloading = ref(false)
const exportErrorMessage = ref('')
const exportTask = ref<ExportTask | null>(null)
let exportPollTimer: number | undefined
const result = ref<PageResponse<AchievementSummary>>({
  items: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
})
const filters = reactive({
  title: '',
  author: '',
  organization: '',
  publicationYear: undefined as number | undefined,
  achievementType: '',
  sourceCode: '',
  venue: '',
  topic: '',
})

async function load(page = 0): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const query: AchievementQuery = { ...filters, page, size: result.value.size }
    result.value = await catalogApi.achievements(query)
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    loading.value = false
  }
}

function reset(): void {
  Object.assign(filters, {
    title: '',
    author: '',
    organization: '',
    publicationYear: undefined,
    achievementType: '',
    sourceCode: '',
    venue: '',
    topic: '',
  })
  void load()
}

async function createExport(format: ExportFormat): Promise<void> {
  clearExportPolling()
  exportCreating.value = format
  exportErrorMessage.value = ''
  exportTask.value = null
  try {
    const exportFilters = await resolveExportFilter(filters, (collection, name) =>
      catalogApi.entities(collection, name, 0, 2),
    )
    const task = await exportApi.create(format, exportFilters)
    exportTask.value = task
    scheduleExportPoll(task)
  } catch (error) {
    exportErrorMessage.value = error instanceof ExportFilterResolutionError
      ? error.message
      : toErrorMessage(error)
  } finally {
    exportCreating.value = null
  }
}

function scheduleExportPoll(task: ExportTask): void {
  if (isTerminal(task.status)) return
  exportPollTimer = window.setTimeout(() => void pollExport(task.id), 800)
}

async function pollExport(exportId: string): Promise<void> {
  try {
    const task = await exportApi.get(exportId)
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
    const blob = await exportApi.download(task)
    const objectUrl = URL.createObjectURL(blob)
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
    PENDING: '等待处理',
    RUNNING: '正在生成',
    SUCCEEDED: '导出完成',
    FAILED: '导出失败',
    EXPIRED: '文件已过期',
  }[status]
}

onMounted(() => load())
onBeforeUnmount(clearExportPolling)
</script>

<template>
  <section class="page-stack">
    <header class="page-heading">
      <div>
        <span class="eyebrow">CATALOG / ACHIEVEMENTS</span>
        <h1>成果目录</h1>
        <p>检索规范化成果，进入详情核对作者、来源记录和字段级血缘。</p>
      </div>
      <div class="entity-links">
        <RouterLink to="/catalog/authors">作者</RouterLink>
        <RouterLink to="/catalog/organizations">机构</RouterLink>
        <RouterLink to="/catalog/venues">期刊</RouterLink>
        <RouterLink to="/catalog/topics">主题</RouterLink>
      </div>
    </header>

    <div class="filter-panel">
      <div class="filter-grid">
        <label><span>题名</span><ElInput v-model="filters.title" clearable /></label>
        <label><span>作者</span><ElInput v-model="filters.author" clearable /></label>
        <label><span>机构</span><ElInput v-model="filters.organization" clearable /></label>
        <label><span>出版年份</span><ElInputNumber v-model="filters.publicationYear" :min="1000" :max="9999" controls-position="right" /></label>
        <label><span>成果类型</span><ElInput v-model="filters.achievementType" clearable /></label>
        <label><span>来源代码</span><ElInput v-model="filters.sourceCode" clearable /></label>
        <label><span>期刊</span><ElInput v-model="filters.venue" clearable /></label>
        <label><span>主题</span><ElInput v-model="filters.topic" clearable @keyup.enter="load()" /></label>
      </div>
      <div class="filter-footer">
        <span class="meta-line">多个条件将由服务端组合过滤</span>
        <div class="filter-actions">
          <template v-if="hasPermission('EXPORT_CREATE')">
            <ElButton :loading="exportCreating === 'CSV'" :disabled="exportCreating !== null" @click="createExport('CSV')">导出 CSV</ElButton>
            <ElButton :loading="exportCreating === 'JSON'" :disabled="exportCreating !== null" @click="createExport('JSON')">导出 JSON</ElButton>
          </template>
          <ElButton @click="reset">重置</ElButton>
          <ElButton type="primary" :loading="loading" @click="load()">查询成果</ElButton>
        </div>
      </div>
    </div>

    <ElAlert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
    <ElAlert v-if="exportErrorMessage" :title="exportErrorMessage" type="error" :closable="false" show-icon />

    <div v-if="exportTask" class="content-panel export-ticket" aria-live="polite">
      <div>
        <span class="eyebrow">EXPORT / {{ exportTask.format }}</span>
        <strong>{{ exportStatusText(exportTask.status) }}</strong>
        <span class="meta-line">任务 {{ exportTask.id }}</span>
      </div>
      <dl>
        <div><dt>预计记录</dt><dd>{{ exportTask.requestedCount.toLocaleString('zh-CN') }}</dd></div>
        <div><dt>已导出</dt><dd>{{ exportTask.exportedCount.toLocaleString('zh-CN') }}</dd></div>
        <div><dt>创建时间</dt><dd>{{ formatDateTime(exportTask.createdAt) }}</dd></div>
        <div><dt>过期时间</dt><dd>{{ formatDateTime(exportTask.expiresAt) }}</dd></div>
      </dl>
      <div class="export-ticket-action">
        <span v-if="exportTask.errorMessage" class="danger-text">{{ exportTask.errorMessage }}</span>
        <ElButton
          v-if="exportTask.downloadAvailable && exportTask.downloadToken"
          type="primary"
          :loading="exportDownloading"
          @click="downloadExport"
        >下载文件</ElButton>
      </div>
    </div>

    <div class="content-panel">
      <div class="toolbar">
        <strong>检索结果</strong>
        <span class="meta-line">共 {{ result.totalElements.toLocaleString('zh-CN') }} 条</span>
      </div>
      <ElTable v-loading="loading" :data="result.items" empty-text="暂无符合条件的成果">
        <ElTableColumn prop="title" label="题名" min-width="300">
          <template #default="{ row }">
            <RouterLink class="table-title" :to="'/catalog/achievements/' + row.id">{{ row.title }}</RouterLink>
            <small>{{ row.doi || '无 DOI' }}</small>
          </template>
        </ElTableColumn>
        <ElTableColumn label="作者" min-width="180">
          <template #default="{ row }">{{ row.authors.join('；') || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn prop="publicationDate" label="发表日期" width="120" />
        <ElTableColumn prop="primaryVenue" label="期刊/来源" min-width="150" />
        <ElTableColumn label="主题" min-width="160">
          <template #default="{ row }">
            <ElTag v-for="topic in row.topics.slice(0, 2)" :key="topic" size="small" effect="plain">{{ topic }}</ElTag>
          </template>
        </ElTableColumn>
      </ElTable>
      <div v-if="result.totalPages > 1" class="pagination-row">
        <ElPagination
          :current-page="result.page + 1"
          :page-size="result.size"
          :total="result.totalElements"
          layout="prev, pager, next"
          @current-change="(page: number) => load(page - 1)"
        />
      </div>
    </div>
  </section>
</template>
