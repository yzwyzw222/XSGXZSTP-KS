<script setup lang="ts">
import type { EChartsCoreOption } from 'echarts/core'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus'

import CountUpNumber from '@/components/CountUpNumber.vue'
import EChartCanvas from '@/components/EChartCanvas.vue'
import { toErrorMessage } from '@/services/api'
import { analyticsApi } from '@/services/business'
import type {
  AnalyticsCollaborationResponse,
  AnalyticsDistributionItem,
  AnalyticsDistributionResponse,
  AnalyticsFilter,
  AnalyticsOverview,
  AnalyticsTrendResponse,
} from '@/types/api'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const isFiltering = ref(false)
const errorMessage = ref('')
const overview = ref<AnalyticsOverview | null>(null)
const trends = ref<AnalyticsTrendResponse | null>(null)
const distributions = ref<AnalyticsDistributionResponse | null>(null)
const collaboration = ref<AnalyticsCollaborationResponse | null>(null)
const filters = reactive<AnalyticsFilter>({})
const collaborationSort = ref<'count' | 'name'>('count')

const metricCards = computed(() => overview.value ? [
  { label: '成果', value: overview.value.achievementCount, note: '规范成果' },
  { label: '作者', value: overview.value.authorCount, note: '当前范围内去重' },
  { label: '机构', value: overview.value.organizationCount, note: '当前范围内去重' },
  { label: '来源', value: overview.value.sourceCount, note: '贡献数据源' },
] : [])
const appliedFilterText = computed(() => {
  const applied = overview.value?.scope.filters
  if (!applied) return '尚未加载'
  const parts = [
    applied.publicationYearFrom ? `起始年份 ${applied.publicationYearFrom}` : '',
    applied.publicationYearTo ? `结束年份 ${applied.publicationYearTo}` : '',
    applied.achievementType ? `成果类型 ${applied.achievementType}` : '',
    applied.sourceType ? `来源 ${applied.sourceType}` : '',
    applied.organizationId ? `机构ID ${applied.organizationId}` : '',
    applied.topicId ? `主题ID ${applied.topicId}` : '',
  ].filter(Boolean)
  return parts.length ? parts.join(' · ') : '全部规范成果'
})
const sortedAuthorCollaborations = computed(() => sortCollaborations(collaboration.value?.authors ?? []))
const sortedOrganizationCollaborations = computed(() => sortCollaborations(collaboration.value?.organizations ?? []))

const trendOption = computed<EChartsCoreOption>(() => ({
  aria: { enabled: true, description: '按发表年份统计的成果数量趋势。' },
  animationDuration: 600,
  animationDurationUpdate: 360,
  animationEasing: 'cubicOut',
  color: ['#38a8ff'],
  grid: { left: 46, right: 20, top: 24, bottom: 42 },
  tooltip: {
    trigger: 'axis',
    backgroundColor: '#0a1729',
    borderColor: '#2b4668',
    textStyle: { color: '#edf6ff' },
    axisPointer: {
      type: 'line',
      snap: true,
      animation: true,
      animationDuration: 180,
      lineStyle: { color: '#57bcff' },
    },
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: trends.value?.items.map((item) => item.publicationYear) ?? [],
    axisLine: { lineStyle: { color: '#304967' } },
    axisLabel: { color: '#8296ae' },
    axisTick: { show: false },
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    axisLabel: { color: '#8296ae' },
    splitLine: { lineStyle: { color: '#213a57' } },
  },
  series: [{
    type: 'line',
    name: '成果数',
    smooth: 0.28,
    symbolSize: 8,
    lineStyle: { width: 3 },
    itemStyle: { borderColor: '#10233d', borderWidth: 2 },
    emphasis: { scale: true, scaleSize: 5 },
    data: trends.value?.items.map((item) => item.achievementCount) ?? [],
  }],
}))

function distributionOption(items: AnalyticsDistributionItem[], color: string): EChartsCoreOption {
  const display = items.slice(0, 10).reverse()
  return {
    aria: { enabled: true },
    animationDuration: 600,
    animationDurationUpdate: 360,
    animationEasing: 'cubicOut',
    animationDelay: (dataIndex: number) => dataIndex * 35,
    color: [color],
    grid: { left: 110, right: 26, top: 16, bottom: 24 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: '#0a1729',
      borderColor: '#2b4668',
      textStyle: { color: '#edf6ff' },
    },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: '#8296ae' },
      splitLine: { lineStyle: { color: '#213a57' } },
    },
    yAxis: {
      type: 'category',
      data: display.map((item) => item.label),
      axisLabel: { color: '#aab9ca', overflow: 'truncate', width: 92 },
      axisLine: { lineStyle: { color: '#304967' } },
      axisTick: { show: false },
    },
    series: [{
      type: 'bar',
      data: display.map((item) => item.achievementCount),
      barMaxWidth: 20,
      itemStyle: { borderRadius: [0, 3, 3, 0] },
    }],
  }
}

async function loadAnalytics(): Promise<void> {
  isFiltering.value = Boolean(overview.value)
  loading.value = true
  errorMessage.value = ''
  try {
    const requested = cleanFilters()
    const [overviewValue, trendValue, distributionValue, collaborationValue] = await Promise.all([
      analyticsApi.overview(requested),
      analyticsApi.trends(requested),
      analyticsApi.distributions(requested),
      analyticsApi.collaboration(requested),
    ])
    overview.value = overviewValue
    trends.value = trendValue
    distributions.value = distributionValue
    collaboration.value = collaborationValue
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    loading.value = false
    await nextTick()
    isFiltering.value = false
  }
}

function resetFilters(): void {
  Object.assign(filters, {
    publicationYearFrom: undefined,
    publicationYearTo: undefined,
    achievementType: undefined,
    sourceType: undefined,
    organizationId: undefined,
    topicId: undefined,
  })
  void loadAnalytics()
}

function cleanFilters(): AnalyticsFilter {
  return Object.fromEntries(
    Object.entries(filters).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  ) as AnalyticsFilter
}

function sortCollaborations(items: AnalyticsCollaborationResponse['authors']) {
  return [...items].sort((left, right) => collaborationSort.value === 'count'
    ? right.sharedAchievementCount - left.sharedAchievementCount
    : `${left.leftLabel}${left.rightLabel}`.localeCompare(`${right.leftLabel}${right.rightLabel}`, 'zh-CN'))
}

onMounted(loadAnalytics)
</script>

<template>
  <section class="page-stack analytics-page">
    <header class="page-heading analytics-heading">
      <div>
        <span class="eyebrow">MYSQL / ANALYTICS</span>
        <h1>统计分析</h1>
        <p>从MySQL规范数据读取聚合结果。图投影不可用时，本页的统计口径和筛选能力不受影响。</p>
      </div>
      <div v-if="overview" class="analytics-stamp">
        <span class="eyebrow">DATA UPDATED</span>
        <strong>{{ formatDateTime(overview.updatedAt) }}</strong>
        <small>权威来源 · {{ overview.scope.source }}</small>
      </div>
    </header>

    <div class="filter-panel">
      <div class="filter-grid analytics-filters">
        <label><span>起始年份</span><ElInputNumber v-model="filters.publicationYearFrom" :min="1000" :max="9999" /></label>
        <label><span>结束年份</span><ElInputNumber v-model="filters.publicationYearTo" :min="1000" :max="9999" /></label>
        <label><span>成果类型</span><ElInput v-model="filters.achievementType" maxlength="64" placeholder="例如 article" /></label>
        <label><span>来源</span><ElSelect v-model="filters.sourceType" clearable placeholder="全部"><ElOption label="OpenAlex" value="OPENALEX" /><ElOption label="Crossref" value="CROSSREF" /></ElSelect></label>
        <label><span>机构业务ID</span><ElInputNumber v-model="filters.organizationId" :min="1" /></label>
        <label><span>主题业务ID</span><ElInputNumber v-model="filters.topicId" :min="1" /></label>
      </div>
      <div class="filter-footer">
        <span class="meta-line">实际范围：{{ appliedFilterText }}</span>
        <div class="filter-actions"><ElButton @click="resetFilters">重置</ElButton><ElButton type="primary" :loading="loading" @click="loadAnalytics">应用筛选</ElButton></div>
      </div>
    </div>

    <ElAlert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />

    <template v-if="overview && trends && distributions && collaboration">
      <div class="analytics-metrics">
        <article v-for="metric in metricCards" :key="metric.label" class="analytics-metric">
          <span>{{ metric.label }}</span><strong><CountUpNumber :value="metric.value" /></strong><small>{{ metric.note }}</small>
        </article>
      </div>

      <div class="analytics-results" :class="{ 'is-filtering': isFiltering }" :aria-busy="loading">
      <div class="analytics-grid">
        <article class="content-panel analytics-panel analytics-panel-wide">
          <div class="section-title"><span class="eyebrow">TREND</span><h2>年度成果趋势</h2></div>
          <EChartCanvas :option="trendOption" label="年度成果趋势折线图" />
          <details class="analytics-table-summary"><summary>查看趋势表格</summary>
            <ElTable :data="trends.items" size="small" empty-text="暂无趋势数据"><ElTableColumn prop="publicationYear" label="年份" /><ElTableColumn prop="achievementCount" label="成果数" /></ElTable>
          </details>
        </article>

        <article class="content-panel analytics-panel">
          <div class="section-title"><span class="eyebrow">TYPE</span><h2>成果类型</h2></div>
          <EChartCanvas :option="distributionOption(distributions.achievementTypes, '#315a72')" label="成果类型分布条形图" />
          <details class="analytics-table-summary"><summary>查看类型表格</summary>
            <ElTable :data="distributions.achievementTypes" size="small"><ElTableColumn prop="label" label="类型" /><ElTableColumn prop="achievementCount" label="成果数" /></ElTable>
          </details>
        </article>

        <article class="content-panel analytics-panel">
          <div class="section-title"><span class="eyebrow">SOURCE</span><h2>数据来源</h2></div>
          <EChartCanvas :option="distributionOption(distributions.sources, '#4c695c')" label="数据来源分布条形图" />
          <details class="analytics-table-summary"><summary>查看来源表格</summary>
            <ElTable :data="distributions.sources" size="small"><ElTableColumn prop="label" label="来源" /><ElTableColumn prop="achievementCount" label="成果数" /></ElTable>
          </details>
        </article>

        <article class="content-panel analytics-panel">
          <div class="section-title"><span class="eyebrow">ORGANIZATION / TOP 20</span><h2>机构分布</h2></div>
          <EChartCanvas :option="distributionOption(distributions.organizations, '#8d6738')" label="机构成果分布条形图" />
          <details class="analytics-table-summary"><summary>查看机构表格</summary>
            <ElTable :data="distributions.organizations" size="small"><ElTableColumn prop="label" label="机构" /><ElTableColumn prop="achievementCount" label="成果数" /></ElTable>
          </details>
        </article>

        <article class="content-panel analytics-panel">
          <div class="section-title"><span class="eyebrow">TOPIC / TOP 20</span><h2>主题分布</h2></div>
          <EChartCanvas :option="distributionOption(distributions.topics, '#765274')" label="主题成果分布条形图" />
          <details class="analytics-table-summary"><summary>查看主题表格</summary>
            <ElTable :data="distributions.topics" size="small"><ElTableColumn prop="label" label="主题" /><ElTableColumn prop="achievementCount" label="成果数" /></ElTable>
          </details>
        </article>
      </div>

      <div class="two-column-panels">
        <section class="content-panel collaboration-panel">
          <div class="section-title collaboration-heading">
            <div><span class="eyebrow">AUTHORS</span><h2>作者合作 Top 20</h2></div>
            <div class="ranking-sort" aria-label="合作排行排序">
              <button :class="{ active: collaborationSort === 'count' }" @click="collaborationSort = 'count'">共同成果</button>
              <button :class="{ active: collaborationSort === 'name' }" @click="collaborationSort = 'name'">姓名</button>
            </div>
          </div>
          <table class="collaboration-table">
            <thead><tr><th>合作双方</th><th>共同成果</th></tr></thead>
            <TransitionGroup name="rank" tag="tbody">
              <tr v-for="item in sortedAuthorCollaborations" :key="`author-${item.leftId}-${item.rightId}`">
                <td>{{ item.leftLabel }} × {{ item.rightLabel }}</td>
                <td>{{ item.sharedAchievementCount }}</td>
              </tr>
            </TransitionGroup>
          </table>
          <p v-if="!sortedAuthorCollaborations.length" class="panel-empty">当前范围没有作者合作关系</p>
        </section>
        <section class="content-panel collaboration-panel"><div class="section-title"><span class="eyebrow">ORGANIZATIONS</span><h2>机构合作 Top 20</h2></div>
          <table class="collaboration-table">
            <thead><tr><th>合作双方</th><th>共同成果</th></tr></thead>
            <TransitionGroup name="rank" tag="tbody">
              <tr v-for="item in sortedOrganizationCollaborations" :key="`organization-${item.leftId}-${item.rightId}`">
                <td>{{ item.leftLabel }} × {{ item.rightLabel }}</td>
                <td>{{ item.sharedAchievementCount }}</td>
              </tr>
            </TransitionGroup>
          </table>
          <p v-if="!sortedOrganizationCollaborations.length" class="panel-empty">当前范围没有机构合作关系</p>
        </section>
      </div>
      </div>
    </template>
  </section>
</template>
