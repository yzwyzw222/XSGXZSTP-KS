<script setup lang="ts">
import { ElAlert, ElButton, ElInput, ElOption, ElSelect, ElTag } from 'element-plus'
import { Download, FileSpreadsheet } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'

import {
  DataTable, FilterBar, FilterField, PageHeader, PanelSection, StatusPill,
} from '@/components/business'
import EntitySuggestInput from '@/components/business/EntitySuggestInput.vue'
import YearPicker from '@/components/business/YearPicker.vue'
import type { DataTableColumn } from '@/components/business/types'
import { useSessionCleanup } from '@/composables/useSessionCleanup'
import { toErrorMessage } from '@/services/api'
import { catalogApi, exportApi, type AchievementQuery } from '@/services/business'
import { useSessionStore } from '@/stores/session'
import type { AchievementSummary, ExportFormat, ExportTask, PageResponse } from '@/types/api'
import { ExportFilterResolutionError, resolveExportFilter } from '@/utils/export-filter'
import { achievementTypeOptions, sourceCodeOptions } from '@/utils/filter-options'
import { formatDateTime } from '@/utils/format'

const sessionStore = useSessionStore()
const { hasPermission } = sessionStore

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

/** 本页筛选只保存在组件状态里，不映射到路由查询，避免两个来源重复触发请求。 */
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

/** 下拉选择以 ALL 表示不过滤，提交前映射回空字符串。 */
const achievementTypeModel = computed<string>({
  get: () => filters.achievementType || 'ALL',
  set: (value) => { filters.achievementType = value === 'ALL' ? '' : value },
})
const sourceCodeModel = computed<string>({
  get: () => filters.sourceCode || 'ALL',
  set: (value) => { filters.sourceCode = value === 'ALL' ? '' : value },
})

const entityEntries = [
  { to: '/catalog/authors', label: '作者' },
  { to: '/catalog/organizations', label: '机构' },
  { to: '/catalog/venues', label: '期刊' },
  { to: '/catalog/topics', label: '主题' },
]

const columns: DataTableColumn<AchievementSummary>[] = [
  { accessorKey: 'title', header: '题名', enableSorting: false, meta: { minWidth: 320 } },
  { id: 'authors', accessorFn: (row) => row.authors.join('；'), header: '作者', enableSorting: false },
  { accessorKey: 'publicationDate', header: '发表日期', enableSorting: false, meta: { width: 110 } },
  { accessorKey: 'primaryVenue', header: '期刊/来源', enableSorting: false },
  { id: 'topics', accessorFn: (row) => row.topics.join('，'), header: '主题', enableSorting: false },
]

async function load(page = 0): Promise<void> {
  const sequence = ++querySequence
  loading.value = true
  errorMessage.value = ''
  try {
    const query: AchievementQuery = { ...filters, page, size: result.value.size }
    const response = await catalogApi.achievements(query)
    if (sequence === querySequence) result.value = response
  } catch (error) {
    if (sequence === querySequence) errorMessage.value = toErrorMessage(error)
  } finally {
    if (sequence === querySequence) loading.value = false
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
    // 作者、机构、期刊和主题文本必须唯一解析为规范实体后才创建任务。
    const exportFilters = await resolveExportFilter(filters, (collection, name) =>
      catalogApi.entities(collection, name, 0, 2),
    )
    if (disposed) return
    const task = await exportApi.create(format, exportFilters)
    if (disposed) return
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

onMounted(() => load())
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
  <section class="page-stack">
    <PageHeader
      title="成果目录"
      description="检索规范化成果，进入详情核对作者、来源记录和字段级血缘。"
    />

    <section class="catalog-workspace" aria-label="成果检索工作区">
    <nav class="catalog-navigation" aria-label="编目实体入口">
      <span aria-current="page">全部成果</span>
      <RouterLink v-for="link in entityEntries" :key="link.to" :to="link.to">{{ link.label }}</RouterLink>
    </nav>

    <FilterBar :columns="4" :applying="loading" apply-text="查询成果" @apply="load()" @reset="reset">
      <FilterField label="题名">
        <ElInput v-model="filters.title" placeholder="按题名关键词模糊检索" clearable @keydown.enter="load()" />
      </FilterField>
      <FilterField label="作者">
        <EntitySuggestInput v-model="filters.author" collection="authors" label="作者" placeholder="输入作者名称后选择" @enter="load()" />
      </FilterField>
      <FilterField label="机构">
        <EntitySuggestInput v-model="filters.organization" collection="organizations" label="机构" placeholder="输入机构名称后选择" @enter="load()" />
      </FilterField>
      <FilterField label="出版年份">
        <YearPicker v-model="filters.publicationYear" aria-label="选择出版年份" />
      </FilterField>
      <FilterField label="成果类型">
        <ElSelect v-model="achievementTypeModel" placeholder="全部类型" filterable>
          <ElOption value="ALL" label="全部类型" />
          <ElOption
            v-for="item in achievementTypeOptions"
            :key="item.value"
            :value="item.value"
            :label="item.label"
          />
        </ElSelect>
      </FilterField>
      <FilterField label="来源代码">
        <ElSelect v-model="sourceCodeModel" placeholder="全部来源" filterable>
          <ElOption value="ALL" label="全部来源" />
          <ElOption
            v-for="item in sourceCodeOptions"
            :key="item.value"
            :value="item.value"
            :label="item.label"
          />
        </ElSelect>
      </FilterField>
      <FilterField label="期刊">
        <EntitySuggestInput v-model="filters.venue" collection="venues" label="期刊" placeholder="输入期刊名称后选择" @enter="load()" />
      </FilterField>
      <FilterField label="主题">
        <EntitySuggestInput v-model="filters.topic" collection="topics" label="主题" placeholder="输入主题名称后选择" @enter="load()" />
      </FilterField>

      <template #meta>组合条件缩小检索范围，留空表示不限</template>
      <template #actions>
        <template v-if="hasPermission('EXPORT_CREATE')">
          <ElButton
            plain
            size="small"
            :loading="exportCreating === 'CSV'"
            :disabled="exportCreating !== null"
            @click="createExport('CSV')"
          >
            <FileSpreadsheet class="mr-1 size-4" aria-hidden="true" />导出 CSV
          </ElButton>
          <ElButton
            plain
            size="small"
            :loading="exportCreating === 'JSON'"
            :disabled="exportCreating !== null"
            @click="createExport('JSON')"
          >
            导出 JSON
          </ElButton>
        </template>
      </template>
    </FilterBar>

    <ElAlert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" show-icon />
    <ElAlert v-if="exportErrorMessage" type="error" :closable="false" :title="exportErrorMessage" show-icon />

    <!-- 导出任务票据 -->
    <section
      v-if="exportTask"
      aria-live="polite"
      class="grid gap-4 border-b border-border bg-muted/40 p-5 lg:grid-cols-[minmax(200px,0.8fr)_minmax(0,1.6fr)_auto] lg:items-center"
    >
      <div class="space-y-1">
        <span class="text-xs text-muted-foreground">导出任务 · {{ exportTask.format }}</span>
        <div class="flex items-center gap-2">
          <strong class="text-lg font-semibold text-foreground">{{ exportStatusText(exportTask.status) }}</strong>
          <StatusPill :status="exportTask.status" />
        </div>
        <span class="mono-evidence block text-xs text-muted-foreground">任务 {{ exportTask.id }}</span>
      </div>
      <dl class="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <div class="space-y-0.5">
          <dt class="text-xs text-muted-foreground">预计记录</dt>
          <dd class="text-sm font-medium tabular-nums">{{ exportTask.requestedCount.toLocaleString('zh-CN') }}</dd>
        </div>
        <div class="space-y-0.5">
          <dt class="text-xs text-muted-foreground">已导出</dt>
          <dd class="text-sm font-medium tabular-nums">{{ exportTask.exportedCount.toLocaleString('zh-CN') }}</dd>
        </div>
        <div class="space-y-0.5">
          <dt class="text-xs text-muted-foreground">创建时间</dt>
          <dd class="text-sm">{{ formatDateTime(exportTask.createdAt) }}</dd>
        </div>
        <div class="space-y-0.5">
          <dt class="text-xs text-muted-foreground">过期时间</dt>
          <dd class="text-sm">{{ formatDateTime(exportTask.expiresAt) }}</dd>
        </div>
      </dl>
      <div class="flex flex-col items-start gap-2 lg:items-end">
        <span v-if="exportTask.errorMessage" class="text-xs text-destructive">{{ exportTask.errorMessage }}</span>
        <ElButton
          v-if="exportTask.downloadAvailable && exportTask.downloadToken"
          type="primary"
          :loading="exportDownloading"
          @click="downloadExport"
        >
          <Download class="mr-1 size-4" aria-hidden="true" />下载文件
        </ElButton>
      </div>
    </section>

    <PanelSection title="检索结果" :subtitle="loading && !result.items.length ? '正在读取…' : `共 ${result.totalElements.toLocaleString('zh-CN')} 条`">
      <template #actions><span class="text-xs text-muted-foreground">题名进入详情 · DOI 保留原始标识</span></template>
      <DataTable
        :columns="columns"
        :data="result.items"
        :loading="loading"
        :page="result.page"
        :size="result.size"
        :total="result.totalElements"
        empty-text="暂无符合条件的成果"
        empty-description="尝试减少筛选条件，或在采集任务中补充当前研究范围。"
        :get-row-id="(row) => String(row.id)"
        @update:page="load"
      >
        <template #cell-title="{ row }">
          <RouterLink class="catalog-title" :to="`/catalog/achievements/${row.id}`">
            {{ row.title }}
          </RouterLink>
          <span class="mono-evidence mt-0.5 block text-xs text-muted-foreground">{{ row.doi || '无 DOI' }}</span>
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
    </PanelSection>
    </section>
  </section>
</template>
