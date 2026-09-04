<script setup lang="ts">
import type { ColumnDef } from '@tanstack/vue-table'
import { Gauge } from 'lucide-vue-next'
import { onMounted, reactive, ref } from 'vue'

import { DataTable, FilterBar, FilterField, JsonEvidence, LoadingSkeleton, PageHeader, PanelSection } from '@/components/business'
import { Alert, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { toErrorMessage } from '@/services/api'
import { qualityApi } from '@/services/business'
import type { PageResponse, QualityMetric, QualityMetricDetail } from '@/types/api'
import { formatDateTime } from '@/utils/format'

type SampleRow = QualityMetricDetail['samples'][number]

const metrics = ref<PageResponse<QualityMetric>>({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const loading = ref(false)
const detailLoading = ref(false)
const errorMessage = ref('')
const detailVisible = ref(false)
const detail = ref<QualityMetricDetail | null>(null)
const filters = reactive({ sourceId: '', runId: '', metricCode: '' })

const columns: ColumnDef<QualityMetric, any>[] = [
  { accessorKey: 'metricCode', header: '指标', enableSorting: false },
  { accessorKey: 'sourceId', header: '来源 ID', enableSorting: false, meta: { width: '90px' } },
  { accessorKey: 'taskId', header: '任务 ID', enableSorting: false, meta: { width: '90px' } },
  { accessorKey: 'runId', header: '运行 ID', enableSorting: false, meta: { width: '90px' } },
  { id: 'result', accessorFn: (row) => metricPercent(row), header: '结果', enableSorting: false, meta: { width: '110px' } },
  { id: 'count', accessorFn: (row) => `${row.numerator} / ${row.denominator}`, header: '计数', enableSorting: false, meta: { width: '120px' } },
  { id: 'measuredAt', accessorFn: (row) => formatDateTime(row.measuredAt), header: '测量时间', enableSorting: false, meta: { width: '170px' } },
  { id: 'actions', header: '操作', enableSorting: false, meta: { width: '100px' } },
]

const sampleColumns: ColumnDef<SampleRow, any>[] = [
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
  detailVisible.value = true
  detailLoading.value = true
  errorMessage.value = ''
  try {
    detail.value = await qualityApi.detail(metric.id)
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    detailLoading.value = false
  }
}

function metricPercent(metric: QualityMetric): string {
  return (Number(metric.metricValue) * 100).toFixed(2) + '%'
}

onMounted(() => load())
</script>

<template>
  <section class="page-stack">
    <PageHeader
      title="质量指标"
      description="按来源和采集运行检查质量度量，进入详情审阅构成指标的原始记录样本。"
    />

    <FilterBar :columns="4" :applying="loading" apply-text="查询指标" @apply="load()" @reset="reset">
      <FilterField label="来源 ID"><Input v-model="filters.sourceId" type="number" min="1" /></FilterField>
      <FilterField label="运行 ID"><Input v-model="filters.runId" type="number" min="1" /></FilterField>
      <FilterField label="指标代码"><Input v-model="filters.metricCode" @keydown.enter="load()" /></FilterField>
    </FilterBar>

    <Alert v-if="errorMessage" variant="destructive"><AlertTitle>{{ errorMessage }}</AlertTitle></Alert>

    <PanelSection title="质量度量" :subtitle="`共 ${metrics.totalElements} 条`">
      <template #actions><Gauge class="size-4 text-muted-foreground" aria-hidden="true" /></template>
      <DataTable
        :columns="columns"
        :data="metrics.items"
        :loading="loading"
        :page="metrics.page"
        :size="metrics.size"
        :total="metrics.totalElements"
        empty-text="暂无质量指标"
        :get-row-id="(row) => String(row.id)"
        @update:page="load"
      >
        <template #cell-result="{ value }"><Badge variant="subtle">{{ value }}</Badge></template>
        <template #cell-actions="{ row }">
          <Button variant="link" size="sm" class="h-auto p-0" @click="showDetail(row)">查看样本</Button>
        </template>
      </DataTable>
    </PanelSection>

    <Dialog v-model:open="detailVisible">
      <DialogContent class="sm:max-w-3xl">
        <DialogHeader><DialogTitle>质量指标样本</DialogTitle></DialogHeader>
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
      </DialogContent>
    </Dialog>
  </section>
</template>
