<script setup lang="ts">
import type { ColumnDef } from '@tanstack/vue-table'
import { Download, ExternalLink, FileSpreadsheet, Library } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'

import {
  DataTable, FilterBar, FilterField, PageHeader, PanelSection, StatusPill,
} from '@/components/business'
import { Alert, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { toErrorMessage } from '@/services/api'
import { catalogApi, exportApi, type AchievementQuery } from '@/services/business'
import { hasPermission } from '@/services/session'
import type { AchievementSummary, ExportFormat, ExportTask, PageResponse } from '@/types/api'
import { ExportFilterResolutionError, resolveExportFilter } from '@/utils/export-filter'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const errorMessage = ref('')
const exportCreating = ref<ExportFormat | null>(null)
const exportDownloading = ref(false)
const exportErrorMessage = ref('')
const exportTask = ref<ExportTask | null>(null)
let exportPollTimer: number | undefined
const result = ref<PageResponse<AchievementSummary>>({
  items: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
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

/** 年份输入以字符串编辑，提交时转换为 number（导出契约要求数值）。 */
const yearModel = computed<string | number>({
  get: () => filters.publicationYear ?? '',
  set: (value) => {
    const text = String(value ?? '').trim()
    filters.publicationYear = text === '' ? undefined : Number(text)
  },
})

const columns: ColumnDef<AchievementSummary, any>[] = [
  { accessorKey: 'title', header: '题名', enableSorting: false, meta: { width: '34%' } },
  { id: 'authors', accessorFn: (row) => row.authors.join('；'), header: '作者', enableSorting: false },
  { accessorKey: 'publicationDate', header: '发表日期', enableSorting: false, meta: { width: '110px' } },
  { accessorKey: 'primaryVenue', header: '期刊/来源', enableSorting: false },
  { id: 'topics', accessorFn: (row) => row.topics.join('，'), header: '主题', enableSorting: false },
]

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
    title: '', author: '', organization: '', publicationYear: undefined,
    achievementType: '', sourceCode: '', venue: '', topic: '',
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
    PENDING: '等待处理', RUNNING: '正在生成', SUCCEEDED: '导出完成',
    FAILED: '导出失败', EXPIRED: '文件已过期',
  }[status]
}

onMounted(() => load())
onBeforeUnmount(clearExportPolling)
</script>

<template>
  <section class="page-stack">
    <PageHeader
      title="成果目录"
      description="检索规范化成果，进入详情核对作者、来源记录和字段级血缘。"
    >
      <template #actions>
        <nav class="flex flex-wrap gap-1.5" aria-label="编目实体入口">
          <Button v-for="link in [
            { to: '/catalog/authors', label: '作者' },
            { to: '/catalog/organizations', label: '机构' },
            { to: '/catalog/venues', label: '期刊' },
            { to: '/catalog/topics', label: '主题' },
          ]" :key="link.to" variant="outline" size="sm" as-child>
            <RouterLink :to="link.to">{{ link.label }}<ExternalLink class="size-3.5" /></RouterLink>
          </Button>
        </nav>
      </template>
    </PageHeader>

    <FilterBar :columns="4" :applying="loading" apply-text="查询成果" @apply="load()" @reset="reset">
      <FilterField label="题名"><Input v-model="filters.title" /></FilterField>
      <FilterField label="作者"><Input v-model="filters.author" /></FilterField>
      <FilterField label="机构"><Input v-model="filters.organization" /></FilterField>
      <FilterField label="出版年份"><Input v-model="yearModel" type="number" min="1000" max="9999" /></FilterField>
      <FilterField label="成果类型"><Input v-model="filters.achievementType" /></FilterField>
      <FilterField label="来源代码"><Input v-model="filters.sourceCode" /></FilterField>
      <FilterField label="期刊"><Input v-model="filters.venue" /></FilterField>
      <FilterField label="主题"><Input v-model="filters.topic" @keydown.enter="load()" /></FilterField>

      <template #meta>多个条件将由服务端组合过滤</template>
      <template #actions>
        <template v-if="hasPermission('EXPORT_CREATE')">
          <Button variant="outline" size="sm" :loading="exportCreating === 'CSV'" :disabled="exportCreating !== null" @click="createExport('CSV')">
            <FileSpreadsheet class="size-4" />导出 CSV
          </Button>
          <Button variant="outline" size="sm" :loading="exportCreating === 'JSON'" :disabled="exportCreating !== null" @click="createExport('JSON')">
            导出 JSON
          </Button>
        </template>
      </template>
    </FilterBar>

    <Alert v-if="errorMessage" variant="destructive"><AlertTitle>{{ errorMessage }}</AlertTitle></Alert>
    <Alert v-if="exportErrorMessage" variant="destructive"><AlertTitle>{{ exportErrorMessage }}</AlertTitle></Alert>

    <!-- 导出任务票据 -->
    <section
      v-if="exportTask"
      aria-live="polite"
      class="grid gap-4 rounded-xl border border-border border-l-4 border-l-success bg-card p-4 shadow-sm lg:grid-cols-[minmax(200px,0.8fr)_minmax(0,1.6fr)_auto] lg:items-center"
    >
      <div class="space-y-1">
        <span class="eyebrow">导出任务 · {{ exportTask.format }}</span>
        <div class="flex items-center gap-2">
          <strong class="text-lg font-semibold text-foreground">{{ exportStatusText(exportTask.status) }}</strong>
          <StatusPill :status="exportTask.status" />
        </div>
        <span class="mono-evidence block text-xs text-muted-foreground">任务 {{ exportTask.id }}</span>
      </div>
      <dl class="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <div class="space-y-0.5"><dt class="text-xs text-muted-foreground">预计记录</dt><dd class="text-sm font-medium tabular-nums">{{ exportTask.requestedCount.toLocaleString('zh-CN') }}</dd></div>
        <div class="space-y-0.5"><dt class="text-xs text-muted-foreground">已导出</dt><dd class="text-sm font-medium tabular-nums">{{ exportTask.exportedCount.toLocaleString('zh-CN') }}</dd></div>
        <div class="space-y-0.5"><dt class="text-xs text-muted-foreground">创建时间</dt><dd class="text-sm">{{ formatDateTime(exportTask.createdAt) }}</dd></div>
        <div class="space-y-0.5"><dt class="text-xs text-muted-foreground">过期时间</dt><dd class="text-sm">{{ formatDateTime(exportTask.expiresAt) }}</dd></div>
      </dl>
      <div class="flex flex-col items-start gap-2 lg:items-end">
        <span v-if="exportTask.errorMessage" class="text-xs text-destructive">{{ exportTask.errorMessage }}</span>
        <Button
          v-if="exportTask.downloadAvailable && exportTask.downloadToken"
          :loading="exportDownloading"
          @click="downloadExport"
        >
          <Download class="size-4" />下载文件
        </Button>
      </div>
    </section>

    <PanelSection title="检索结果" :subtitle="`共 ${result.totalElements.toLocaleString('zh-CN')} 条`">
      <template #actions><Library class="size-4 text-muted-foreground" aria-hidden="true" /></template>
      <DataTable
        :columns="columns"
        :data="result.items"
        :loading="loading"
        :page="result.page"
        :size="result.size"
        :total="result.totalElements"
        empty-text="暂无符合条件的成果"
        :get-row-id="(row) => String(row.id)"
        @update:page="load"
      >
        <template #cell-title="{ row }">
          <RouterLink class="font-medium text-foreground hover:text-primary" :to="`/catalog/achievements/${row.id}`">
            {{ row.title }}
          </RouterLink>
          <span class="mono-evidence mt-0.5 block text-xs text-muted-foreground">{{ row.doi || '无 DOI' }}</span>
        </template>
        <template #cell-authors="{ value }">
          <span class="text-muted-foreground">{{ value || '—' }}</span>
        </template>
        <template #cell-topics="{ row }">
          <div class="flex flex-wrap gap-1">
            <Badge v-for="topic in row.topics.slice(0, 2)" :key="topic" variant="subtle">{{ topic }}</Badge>
            <span v-if="!row.topics.length" class="text-muted-foreground">—</span>
          </div>
        </template>
      </DataTable>
    </PanelSection>
  </section>
</template>
