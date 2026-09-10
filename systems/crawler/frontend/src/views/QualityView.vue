<script setup lang="ts">
import { ElAlert, ElButton, ElDialog, ElInput, ElInputNumber, ElOption, ElSelect, ElTag } from 'element-plus'
import { Gauge } from 'lucide-vue-next'
import { onMounted, reactive, ref } from 'vue'

import {
  DataTable, FilterBar, FilterField, JsonEvidence, LoadingSkeleton, PageHeader, PanelSection,
} from '@/components/business'
import type { DataTableColumn } from '@/components/business/types'
import { useDataSources } from '@/composables/useDataSources'
import { toErrorMessage } from '@/services/api'
import { qualityApi } from '@/services/business'
import type { PageResponse, QualityMetric, QualityMetricDetail } from '@/types/api'
import { formatDateTime } from '@/utils/format'

type SampleRow = QualityMetricDetail['samples'][number]

const metrics = ref<PageResponse<QualityMetric>>({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const loading = ref(false)
const detailLoading = ref(false)
const errorMessage = ref('')
/** 弹窗内错误独立于页面级错误，避免提示跑到弹窗下层。 */
const detailError = ref('')
const detailVisible = ref(false)
const detail = ref<QualityMetricDetail | null>(null)
/** 打开弹窗时作废上一次详情请求，避免迟到响应覆盖当前指标。 */
let detailVersion = 0
const { sources, sourceLoading, sourceError, loadSources, sourceName } = useDataSources()
const filters = reactive({ sourceId: '', runId: '', metricCode: '' })

const columns: DataTableColumn<QualityMetric>[] = [
  { accessorKey: 'metricCode', header: '指标', enableSorting: false },
  { accessorKey: 'sourceId', header: '数据源', enableSorting: false, meta: { width: '90px' } },
  { accessorKey: 'taskId', header: '任务 ID', enableSorting: false, meta: { width: '90px' } },
  { accessorKey: 'runId', header: '运行 ID', enableSorting: false, meta: { width: '90px' } },
  { id: 'result', accessorFn: (row) => metricPercent(row), header: '结果', enableSorting: false, meta: { width: '110px' } },
  { id: 'count', accessorFn: (row) => `${row.numerator} / ${row.denominator}`, header: '计数', enableSorting: false, meta: { width: '120px' } },
  { id: 'measuredAt', accessorFn: (row) => formatDateTime(row.measuredAt), header: '测量时间', enableSorting: false, meta: { width: '170px' } },
  { id: 'actions', header: '操作', enableSorting: false, meta: { width: '100px' } },
]

const sampleColumns: DataTableColumn<SampleRow>[] = [
  { accessorKey: 'rawRecordId', header: '原始记录 ID', enableSorting: false, meta: { width: '120px' } },
  { accessorKey: 'externalRecordId', header: '外部记录', enableSorting: false },
  { id: 'evidence', accessorFn: (row) => JSON.stringify(row.evidence), header: '证据', enableSorting: false },
  { id: 'createdAt', accessorFn: (row) => formatDateTime(row.createdAt), header: '记录时间', enableSorting: false, meta: { width: '160px' } },
]

async function load(page = 0): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    metrics.value = await qualityApi.page({
      sourceId: filters.sourceId ? Number(filters.sourceId) : undefined,
      runId: filters.runId ? Number(filters.runId) : undefined,
      metricCode: filters.metricCode.trim(),
      page,
      size: metrics.value.size,
    })
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    loading.value = false
  }
}

function reset(): void {
  filters.sourceId = ''
  filters.runId = ''
  filters.metricCode = ''
  void load()
}

async function showDetail(metric: QualityMetric): Promise<void> {
  const version = ++detailVersion
  detailVisible.value = true
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  try {
    const response = await qualityApi.detail(metric.id)
    if (version !== detailVersion) return
    detail.value = response
  } catch (error) {
    if (version === detailVersion) detailError.value = toErrorMessage(error)
  } finally {
    if (version === detailVersion) detailLoading.value = false
  }
}

function closeDetail(): void {
  // 弹窗关闭后作废在途详情请求并清理上一个指标的数据。
  detailVersion++
  detail.value = null
  detailError.value = ''
  detailLoading.value = false
}

function metricPercent(metric: QualityMetric): string {
  return (Number(metric.metricValue) * 100).toFixed(2) + '%'
}

onMounted(() => { void load(); void loadSources() })
</script>

<template>
  <section class="page-stack">
    <PageHeader
      title="质量指标"
      description="按来源和采集运行检查质量度量，进入详情审阅构成指标的原始记录样本。"
    />

    <ElAlert v-if="sourceError" type="error" :closable="false" :title="sourceError">
      <ElButton link :loading="sourceLoading" @click="loadSources">重试读取数据源</ElButton>
    </ElAlert>
    <FilterBar :columns="4" :applying="loading" apply-text="查询指标" @apply="load()" @reset="reset">
      <FilterField label="数据源">
        <ElSelect v-model="filters.sourceId" :loading="sourceLoading" clearable placeholder="全部数据源" style="width: 100%">
          <ElOption v-for="source in sources" :key="source.id" :value="String(source.id)" :label="sourceName(source.id)" />
        </ElSelect>
      </FilterField>
      <FilterField label="运行 ID">
        <ElInputNumber :model-value="filters.runId ? Number(filters.runId) : undefined" :min="1" :max="Number.MAX_SAFE_INTEGER" :step="1" :precision="0" controls-position="right" style="width: 100%" placeholder="全部运行" @update:model-value="filters.runId = String($event ?? '')" />
      </FilterField>
      <FilterField label="指标代码">
        <ElInput v-model="filters.metricCode" placeholder="按指标代码过滤" clearable @keydown.enter="load()" />
      </FilterField>
    </FilterBar>

    <ElAlert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" show-icon />

    <PanelSection title="质量度量" :subtitle="`共 ${metrics.totalElements.toLocaleString('zh-CN')} 条`">
      <template #actions><Gauge class="size-4 text-muted-foreground" aria-hidden="true" /></template>
      <DataTable fill
        :columns="columns"
        :data="metrics.items"
        :loading="loading"
        :page="metrics.page"
        :size="metrics.size"
        :total="metrics.totalElements"
        empty-text="暂无质量指标"
        empty-description="调整来源、运行或指标代码后重新查询。"
        :get-row-id="(row) => String(row.id)"
        @update:page="load"
      >
        <template #cell-sourceId="{ row }">{{ sourceName(row.sourceId) }}</template>
        <template #cell-result="{ value }">
          <ElTag size="small" type="info" effect="plain">{{ value }}</ElTag>
        </template>
        <template #cell-actions="{ row }">
          <ElButton link type="primary" @click="showDetail(row)">查看样本</ElButton>
        </template>
      </DataTable>
    </PanelSection>

    <ElDialog
      v-model="detailVisible"
      title="质量指标样本"
      width="min(880px, calc(100vw - 32px))"
      append-to-body
      destroy-on-close
      class="aacv-form-dialog"
      body-class="aacv-dialog-body"
      @closed="closeDetail"
    >
      <div class="space-y-4">
        <ElAlert v-if="detailError" type="error" :closable="false" :title="detailError" show-icon />
        <LoadingSkeleton v-if="detailLoading" variant="table" :rows="4" />
        <template v-else-if="detail">
          <div class="space-y-1 border-l-4 border-primary bg-muted/40 p-4">
            <span class="eyebrow">{{ detail.metric.metricCode }}</span>
            <strong class="block text-3xl font-semibold tabular-nums">{{ metricPercent(detail.metric) }}</strong>
            <small class="text-xs text-muted-foreground">
              {{ detail.metric.numerator }} / {{ detail.metric.denominator }} · 运行 #{{ detail.metric.runId }}
            </small>
          </div>
          <DataTable
            :columns="sampleColumns"
            :data="detail.samples"
            :get-row-id="(row) => String(row.id)"
            empty-text="该指标没有问题样本"
            dense
          >
            <template #cell-evidence="{ row }">
              <JsonEvidence :data="row.evidence" max-height="120px" />
            </template>
          </DataTable>
        </template>
      </div>
    </ElDialog>
  </section>
</template>
