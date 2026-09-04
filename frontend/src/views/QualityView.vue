<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElInput,
  ElInputNumber,
  ElPagination,
  ElTable,
  ElTableColumn,
  ElTag,
  vLoading,
} from 'element-plus'

import { toErrorMessage } from '@/services/api'
import { qualityApi } from '@/services/business'
import type { PageResponse, QualityMetric, QualityMetricDetail } from '@/types/api'
import { formatDateTime } from '@/utils/format'

const metrics = ref<PageResponse<QualityMetric>>({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const loading = ref(false)
const detailLoading = ref(false)
const errorMessage = ref('')
const detailVisible = ref(false)
const detail = ref<QualityMetricDetail | null>(null)
const filters = reactive({
  sourceId: undefined as number | undefined,
  runId: undefined as number | undefined,
  metricCode: '',
})

async function load(page = 0): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    metrics.value = await qualityApi.page({ ...filters, page, size: metrics.value.size })
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    loading.value = false
  }
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
    <header class="page-heading">
      <div>
        <span class="eyebrow">DATA / QUALITY METRICS</span>
        <h1>质量指标</h1>
        <p>按来源和采集运行检查质量度量，进入详情审阅构成指标的原始记录样本。</p>
      </div>
    </header>
    <div class="filter-panel">
      <div class="filter-grid quality-filters">
        <label><span>来源 ID</span><ElInputNumber v-model="filters.sourceId" :min="1" /></label>
        <label><span>运行 ID</span><ElInputNumber v-model="filters.runId" :min="1" /></label>
        <label><span>指标代码</span><ElInput v-model="filters.metricCode" clearable @keyup.enter="load()" /></label>
        <div class="filter-actions"><ElButton type="primary" :loading="loading" @click="load()">查询指标</ElButton></div>
      </div>
    </div>
    <ElAlert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
    <div class="content-panel">
      <div class="toolbar"><strong>质量度量</strong><span class="meta-line">共 {{ metrics.totalElements }} 条</span></div>
      <ElTable v-loading="loading" :data="metrics.items" empty-text="暂无质量指标">
        <ElTableColumn prop="metricCode" label="指标" min-width="210" />
        <ElTableColumn prop="sourceId" label="来源 ID" width="100" />
        <ElTableColumn prop="taskId" label="任务 ID" width="100" />
        <ElTableColumn prop="runId" label="运行 ID" width="100" />
        <ElTableColumn label="结果" width="130"><template #default="{ row }"><ElTag effect="plain">{{ metricPercent(row as QualityMetric) }}</ElTag></template></ElTableColumn>
        <ElTableColumn label="计数" width="140"><template #default="{ row }">{{ row.numerator }} / {{ row.denominator }}</template></ElTableColumn>
        <ElTableColumn label="测量时间" width="180"><template #default="{ row }">{{ formatDateTime(row.measuredAt) }}</template></ElTableColumn>
        <ElTableColumn label="操作" width="100"><template #default="{ row }"><ElButton link type="primary" @click="showDetail(row as QualityMetric)">查看样本</ElButton></template></ElTableColumn>
      </ElTable>
      <div v-if="metrics.totalPages > 1" class="pagination-row">
        <ElPagination :current-page="metrics.page + 1" :page-size="metrics.size" :total="metrics.totalElements" layout="prev, pager, next" @current-change="(page: number) => load(page - 1)" />
      </div>
    </div>

    <ElDialog v-model="detailVisible" title="质量指标样本" width="820px">
      <div v-loading="detailLoading">
        <div v-if="detail" class="metric-summary">
          <span class="eyebrow">{{ detail.metric.metricCode }}</span>
          <strong>{{ metricPercent(detail.metric) }}</strong>
          <small>{{ detail.metric.numerator }} / {{ detail.metric.denominator }} · 运行 #{{ detail.metric.runId }}</small>
        </div>
        <ElTable :data="detail?.samples ?? []" empty-text="该指标没有问题样本">
          <ElTableColumn prop="rawRecordId" label="原始记录 ID" width="130" />
          <ElTableColumn prop="externalRecordId" label="外部记录" min-width="180" />
          <ElTableColumn label="证据" min-width="300"><template #default="{ row }"><code>{{ JSON.stringify(row.evidence) }}</code></template></ElTableColumn>
          <ElTableColumn label="记录时间" width="170"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></ElTableColumn>
        </ElTable>
      </div>
    </ElDialog>
  </section>
</template>
