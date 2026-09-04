<script setup lang="ts">
import type { ColumnDef } from '@tanstack/vue-table'
import type { EChartsCoreOption } from 'echarts/core'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'

import { ChartFrame, DataTable, FilterBar, FilterField, PageHeader, PanelSection, StatCard } from '@/components/business'
import { Alert, AlertTitle } from '@/components/ui/alert'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger } from '@/components/ui/select'
import { useChartTheme } from '@/composables/useChartTheme'
import { toErrorMessage } from '@/services/api'
import { analyticsApi } from '@/services/business'
import type {
  AnalyticsCollaborationResponse, AnalyticsDistributionItem, AnalyticsDistributionResponse,
  AnalyticsFilter, AnalyticsOverview, AnalyticsTrendItem, AnalyticsTrendResponse,
} from '@/types/api'
import { formatDateTime } from '@/utils/format'

type CollaborationItem = AnalyticsCollaborationResponse['authors'][number]

const loading = ref(false)
const isFiltering = ref(false)
const errorMessage = ref('')
const overview = ref<AnalyticsOverview | null>(null)
const trends = ref<AnalyticsTrendResponse | null>(null)
const distributions = ref<AnalyticsDistributionResponse | null>(null)
const collaboration = ref<AnalyticsCollaborationResponse | null>(null)
const collaborationSort = ref<'count' | 'name'>('count')
const { palette } = useChartTheme()

const filterForm = reactive({
  publicationYearFrom: '',
  publicationYearTo: '',
  achievementType: '',
  sourceType: 'ALL' as 'ALL' | 'OPENALEX' | 'CROSSREF',
  organizationId: '',
  topicId: '',
})

const metricCards = computed(() => overview.value ? [
  { label: '成果', value: overview.value.achievementCount, note: '规范成果', tone: 'blue' as const },
  { label: '作者', value: overview.value.authorCount, note: '当前范围内去重', tone: 'green' as const },
  { label: '机构', value: overview.value.organizationCount, note: '当前范围内去重', tone: 'violet' as const },
  { label: '来源', value: overview.value.sourceCount, note: '贡献数据源', tone: 'cyan' as const },
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

const collaborationColumns: ColumnDef<CollaborationItem, any>[] = [
  { id: 'pair', accessorFn: (row) => `${row.leftLabel} × ${row.rightLabel}`, header: '合作双方', enableSorting: false },
  { accessorKey: 'sharedAchievementCount', header: '共同成果', enableSorting: false, meta: { width: '90px' } },
]
const trendColumns: ColumnDef<AnalyticsTrendItem, any>[] = [
  { accessorKey: 'publicationYear', header: '年份', enableSorting: false },
  { accessorKey: 'achievementCount', header: '成果数', enableSorting: false },
]
const distributionColumns: ColumnDef<AnalyticsDistributionItem, any>[] = [
  { accessorKey: 'label', header: '名称', enableSorting: false },
  { accessorKey: 'achievementCount', header: '成果数', enableSorting: false, meta: { width: '90px' } },
]

const axis = computed(() => ({
  line: palette.value.grid,
  label: palette.value.textMuted,
  split: palette.value.grid,
}))

const trendOption = computed<EChartsCoreOption>(() => ({
  aria: { enabled: true, description: '按发表年份统计的成果数量趋势。' },
  animationDuration: 600,
  animationEasing: 'cubicOut',
  grid: { left: 46, right: 20, top: 24, bottom: 42 },
  tooltip: { trigger: 'axis', axisPointer: { type: 'line', snap: true, lineStyle: { color: palette.value.series[0] } } },
  xAxis: {
    type: 'category', boundaryGap: false,
    data: trends.value?.items.map((item) => item.publicationYear) ?? [],
    axisLine: { lineStyle: { color: axis.value.line } },
    axisLabel: { color: axis.value.label }, axisTick: { show: false },
  },
  yAxis: {
    type: 'value', minInterval: 1,
    axisLabel: { color: axis.value.label }, splitLine: { lineStyle: { color: axis.value.split } },
  },
  series: [{
    type: 'line', name: '成果数', smooth: 0.28, symbolSize: 8,
    lineStyle: { width: 3 }, emphasis: { scale: true, scaleSize: 5 },
    areaStyle: { opacity: 0.12 },
    data: trends.value?.items.map((item) => item.achievementCount) ?? [],
  }],
}))

function distributionOption(items: AnalyticsDistributionItem[], color: string): EChartsCoreOption {
  const display = items.slice(0, 10).reverse()
  return {
    aria: { enabled: true },
    animationDuration: 600,
    animationEasing: 'cubicOut',
    color: [color],
    grid: { left: 110, right: 26, top: 16, bottom: 24 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'value', minInterval: 1, axisLabel: { color: axis.value.label }, splitLine: { lineStyle: { color: axis.value.split } } },
    yAxis: {
      type: 'category', data: display.map((item) => item.label),
      axisLabel: { color: axis.value.label, overflow: 'truncate', width: 92 },
      axisLine: { lineStyle: { color: axis.value.line } }, axisTick: { show: false },
    },
    series: [{ type: 'bar', data: display.map((item) => item.achievementCount), barMaxWidth: 20, itemStyle: { borderRadius: [0, 3, 3, 0] } }],
  }
}

const typeOption = computed(() => distributionOption(distributions.value?.achievementTypes ?? [], palette.value.series[0]!))
const sourceOption = computed(() => distributionOption(distributions.value?.sources ?? [], palette.value.series[1]!))
const orgOption = computed(() => distributionOption(distributions.value?.organizations ?? [], palette.value.series[2]!))
const topicOption = computed(() => distributionOption(distributions.value?.topics ?? [], palette.value.series[3]!))

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
  Object.assign(filterForm, {
    publicationYearFrom: '', publicationYearTo: '', achievementType: '',
    sourceType: 'ALL', organizationId: '', topicId: '',
  })
  void loadAnalytics()
}

function cleanFilters(): AnalyticsFilter {
  const result: AnalyticsFilter = {}
  if (filterForm.publicationYearFrom.trim()) result.publicationYearFrom = Number(filterForm.publicationYearFrom)
  if (filterForm.publicationYearTo.trim()) result.publicationYearTo = Number(filterForm.publicationYearTo)
  if (filterForm.achievementType.trim()) result.achievementType = filterForm.achievementType.trim()
  if (filterForm.sourceType && filterForm.sourceType !== 'ALL') result.sourceType = filterForm.sourceType
  if (filterForm.organizationId.trim()) result.organizationId = Number(filterForm.organizationId)
  if (filterForm.topicId.trim()) result.topicId = Number(filterForm.topicId)
  return result
}

function sortCollaborations(items: CollaborationItem[]): CollaborationItem[] {
  return [...items].sort((left, right) => collaborationSort.value === 'count'
    ? right.sharedAchievementCount - left.sharedAchievementCount
    : `${left.leftLabel}${left.rightLabel}`.localeCompare(`${right.leftLabel}${right.rightLabel}`, 'zh-CN'))
}

onMounted(loadAnalytics)
</script>

<template>
  <section class="page-stack">
    <PageHeader
      title="统计分析"
      description="从 MySQL 规范数据读取聚合结果。图投影不可用时，本页的统计口径和筛选能力不受影响。"
      divided
    >
      <template #actions>
        <div v-if="overview" class="flex flex-wrap items-center gap-3">
          <span class="text-xs text-muted-foreground">数据更新时间 {{ formatDateTime(overview.updatedAt) }}</span>
          <span class="text-xs text-muted-foreground">权威来源 · {{ overview.scope.source }}</span>
        </div>
      </template>
    </PageHeader>

    <FilterBar :columns="3" :applying="loading" apply-text="应用筛选" @apply="loadAnalytics" @reset="resetFilters">
      <FilterField label="起始年份"><Input v-model="filterForm.publicationYearFrom" type="number" min="1000" max="9999" /></FilterField>
      <FilterField label="结束年份"><Input v-model="filterForm.publicationYearTo" type="number" min="1000" max="9999" /></FilterField>
      <FilterField label="成果类型"><Input v-model="filterForm.achievementType" :maxlength="64" placeholder="例如 article" /></FilterField>
      <FilterField label="来源">
        <Select v-model="filterForm.sourceType">
          <SelectTrigger placeholder="全部" />
          <SelectContent>
            <SelectItem value="ALL">全部</SelectItem>
            <SelectItem value="OPENALEX">OpenAlex</SelectItem>
            <SelectItem value="CROSSREF">Crossref</SelectItem>
          </SelectContent>
        </Select>
      </FilterField>
      <FilterField label="机构业务ID"><Input v-model="filterForm.organizationId" type="number" min="1" /></FilterField>
      <FilterField label="主题业务ID"><Input v-model="filterForm.topicId" type="number" min="1" /></FilterField>
      <template #meta>实际范围：{{ appliedFilterText }}</template>
    </FilterBar>

    <Alert v-if="errorMessage" variant="destructive"><AlertTitle>{{ errorMessage }}</AlertTitle></Alert>

    <template v-if="overview && trends && distributions && collaboration">
      <div class="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatCard v-for="metric in metricCards" :key="metric.label" :label="metric.label" :value="metric.value" :note="metric.note" :tone="metric.tone" />
      </div>

      <div :class="isFiltering ? 'opacity-50 transition-opacity' : 'transition-opacity'" :aria-busy="loading" class="grid gap-4">
        <!-- 趋势（宽） -->
        <PanelSection title="年度成果趋势">
          <ChartFrame :option="trendOption" label="年度成果趋势折线图" height="320px" />
          <details class="mt-3 border-t border-border pt-2">
            <summary class="cursor-pointer text-sm font-medium text-primary">查看趋势表格</summary>
            <div class="mt-2">
              <DataTable :columns="trendColumns" :data="trends.items" :get-row-id="(row) => String(row.publicationYear)" empty-text="暂无趋势数据" dense />
            </div>
          </details>
        </PanelSection>

        <!-- 分布图网格 -->
        <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <PanelSection v-for="panel in [
            { key: 'type', title: '成果类型', option: typeOption, label: '成果类型分布条形图', items: distributions.achievementTypes, summary: '查看类型表格', name: '类型' },
            { key: 'source', title: '数据来源', option: sourceOption, label: '数据来源分布条形图', items: distributions.sources, summary: '查看来源表格', name: '来源' },
            { key: 'org', title: '机构分布', option: orgOption, label: '机构成果分布条形图', items: distributions.organizations, summary: '查看机构表格', name: '机构' },
            { key: 'topic', title: '主题分布', option: topicOption, label: '主题成果分布条形图', items: distributions.topics, summary: '查看主题表格', name: '主题' },
          ]" :key="panel.key" :title="panel.title">
            <ChartFrame :option="panel.option" :label="panel.label" height="280px" />
            <details class="mt-3 border-t border-border pt-2">
              <summary class="cursor-pointer text-sm font-medium text-primary">{{ panel.summary }}</summary>
              <div class="mt-2">
                <DataTable :columns="distributionColumns" :data="panel.items" :get-row-id="(row) => String(row.key)" dense />
              </div>
            </details>
          </PanelSection>
        </div>

        <!-- 合作 -->
        <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <PanelSection title="作者合作 Top 20" subtitle="AUTHORS">
            <template #actions>
              <div class="flex items-center gap-1 rounded-md border border-border p-0.5" role="group" aria-label="合作排行排序">
                <button
                  v-for="opt in ([['count', '共同成果'], ['name', '姓名']] as const)"
                  :key="opt[0]" type="button" :aria-pressed="collaborationSort === opt[0]"
                  class="rounded px-2 py-0.5 text-xs transition-colors"
                  :class="collaborationSort === opt[0] ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground'"
                  @click="collaborationSort = opt[0]"
                >{{ opt[1] }}</button>
              </div>
            </template>
            <DataTable
              :columns="collaborationColumns"
              :data="sortedAuthorCollaborations"
              :get-row-id="(row) => `author-${row.leftId}-${row.rightId}`"
              empty-text="当前范围没有作者合作关系"
              dense
            />
          </PanelSection>
          <PanelSection title="机构合作 Top 20" subtitle="ORGANIZATIONS">
            <DataTable
              :columns="collaborationColumns"
              :data="sortedOrganizationCollaborations"
              :get-row-id="(row) => `org-${row.leftId}-${row.rightId}`"
              empty-text="当前范围没有机构合作关系"
              dense
            />
          </PanelSection>
        </div>
      </div>
    </template>
  </section>
</template>
