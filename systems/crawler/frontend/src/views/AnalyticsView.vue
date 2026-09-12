<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import { ElAlert, ElButton, ElPopover } from 'element-plus'
import { Download, Info } from 'lucide-vue-next'
import type { EChartsCoreOption } from 'echarts/core'
import { RouterLink } from 'vue-router'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'

import { DataTable, LoadingSkeleton, PanelSection } from '@/components/business'
import AnalyticsCoveragePanel from '@/components/business/AnalyticsCoveragePanel.vue'
import ChartFrame from '@/components/business/ChartFrame.vue'
import CompactFieldSearch from '@/components/business/CompactFieldSearch.vue'
import YearRangeFilter from '@/components/business/YearRangeFilter.vue'
import { useSessionStore } from '@/stores/session'
import { analyticsCsv, type AnalyticsSnapshot } from '@/utils/analytics-export'
import type { DataTableColumn } from '@/components/business/types'
import { useChartTheme } from '@/composables/useChartTheme'
import { toErrorMessage } from '@/services/api'
import { analyticsApi } from '@/services/business'
import type {
  AnalyticsCollaborationResponse, AnalyticsDistributionItem, AnalyticsDistributionResponse,
  AnalyticsFilter, AnalyticsOverview, AnalyticsTrendItem, AnalyticsTrendResponse,
} from '@/types/api'
import { achievementTypeLabel } from '@/utils/filter-options'
import { formatDateTime } from '@/utils/format'

type CollaborationItem = AnalyticsCollaborationResponse['authors'][number]

const props = withDefaults(defineProps<{
  section?: 'overview' | 'coverage' | 'distributions' | 'research' | 'collaboration'
}>(), { section: 'overview' })
const sectionTitle = computed(() => ({
  overview: '统计分析', coverage: '字段覆盖率', distributions: '成果分布', research: '机构与主题', collaboration: '合作排行',
})[props.section])
const wideOverview = useMediaQuery('(min-width: 1600px) and (min-height: 1000px)')
const showingTables = reactive<Record<string, boolean>>({})
const distributionFocus = reactive({ distributions: 'type', research: 'org' })
const selectedDistribution = computed({
  get: () => distributionFocus[props.section === 'research' ? 'research' : 'distributions'],
  set: (value: string) => { distributionFocus[props.section === 'research' ? 'research' : 'distributions'] = value },
})
const collaborationFocus = ref<'authors' | 'organizations'>('authors')

const loading = ref(false)
const isFiltering = ref(false)
const errorMessage = ref('')
const overview = ref<AnalyticsOverview | null>(null)
const trends = ref<AnalyticsTrendResponse | null>(null)
const distributions = ref<AnalyticsDistributionResponse | null>(null)
const achievementTypes = computed(() => (distributions.value?.achievementTypes ?? []).map(item => ({ ...item, label: achievementTypeLabel(item.key, item.label) })))
const collaboration = ref<AnalyticsCollaborationResponse | null>(null)
const collaborationSort = ref<'count' | 'name'>('count')
const { palette } = useChartTheme()
let querySequence = 0
onBeforeUnmount(() => { querySequence++ })

const session = useSessionStore()
const searchFields = [
  { value: 'organizationId', label: '机构', collection: 'organizations' },
  { value: 'topicId', label: '主题', collection: 'topics' },
] as const
const searchField = ref<'organizationId' | 'topicId'>('organizationId')
const searchText = ref('')
const searchEntityId = ref<number>()
const yearFrom = ref<number>()
const yearTo = ref<number>()
function changeSearchField(value: string): void {
  searchField.value = value as 'organizationId' | 'topicId'
  searchText.value = ''
  searchEntityId.value = undefined
}
function applyYears(from: number | undefined, to: number | undefined): void {
  yearFrom.value = from; yearTo.value = to
  void loadAnalytics()
}
const exportReady = computed(() => !loading.value && !errorMessage.value && overview.value && trends.value && distributions.value && collaboration.value)
function downloadStatistics(format: 'CSV' | 'JSON'): void {
  if (!exportReady.value || !session.hasPermission('EXPORT_CREATE')) return
  const snapshot: AnalyticsSnapshot = { overview: overview.value!, trends: trends.value!, distributions: distributions.value!, collaboration: collaboration.value! }
  const blob = new Blob([format === 'CSV' ? analyticsCsv(snapshot) : JSON.stringify(snapshot, null, 2)], { type: format === 'CSV' ? 'text/csv;charset=utf-8' : 'application/json' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `aacv-statistics.${format.toLowerCase()}`
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

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
    applied.organizationId ? `机构ID ${applied.organizationId}` : '',
    applied.topicId ? `主题ID ${applied.topicId}` : '',
  ].filter(Boolean)
  return parts.length ? parts.join(' · ') : '全部规范成果'
})

const sortedAuthorCollaborations = computed(() => sortCollaborations(collaboration.value?.authors ?? []))
const sortedOrganizationCollaborations = computed(() => sortCollaborations(collaboration.value?.organizations ?? []))

const collaborationColumns: DataTableColumn<CollaborationItem>[] = [
  { id: 'pair', accessorFn: (row) => `${row.leftLabel} × ${row.rightLabel}`, header: '合作双方', enableSorting: false },
  { accessorKey: 'sharedAchievementCount', header: '共同成果', enableSorting: false, meta: { width: '90px' } },
]
const trendColumns: DataTableColumn<AnalyticsTrendItem>[] = [
  { accessorKey: 'publicationYear', header: '年份', enableSorting: false },
  { accessorKey: 'achievementCount', header: '成果数', enableSorting: false },
]
const distributionColumns: DataTableColumn<AnalyticsDistributionItem>[] = [
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
    id: 'analytics-trend', type: 'line', name: '成果数', smooth: 0.15, symbolSize: 6,
    lineStyle: { width: 2.5 }, emphasis: { scale: true, scaleSize: 3 },
    areaStyle: { opacity: 0.06 },
    data: trends.value?.items.map((item) => ({ id: String(item.publicationYear), name: String(item.publicationYear), value: item.achievementCount })) ?? [],
  }],
}))

function distributionOption(items: AnalyticsDistributionItem[], color: string): EChartsCoreOption {
  const display = items.slice(0, 10).reverse()
  return {
    aria: { enabled: true },
    color: [color],
    grid: { left: 110, right: 26, top: 16, bottom: 24 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'value', minInterval: 1, axisLabel: { color: axis.value.label }, splitLine: { lineStyle: { color: axis.value.split } } },
    yAxis: {
      type: 'category', data: display.map((item) => item.label),
      axisLabel: { color: axis.value.label, overflow: 'truncate', width: 92 },
      axisLine: { lineStyle: { color: axis.value.line } }, axisTick: { show: false },
    },
    series: [{ id: 'distribution', type: 'bar', data: display.map((item) => ({ id: item.key, name: item.label, value: item.achievementCount })), barMaxWidth: 14, itemStyle: { borderRadius: [0, 2, 2, 0] } }],
  }
}

const typeOption = computed(() => distributionOption(achievementTypes.value, palette.value.series[0]!))
const typeRingOption = computed<EChartsCoreOption>(() => ({
  aria: { enabled: true, description: '当前统计范围的成果类型分布' },
  tooltip: { trigger: 'item' },
  legend: { right: 12, top: 'middle', orient: 'vertical', type: 'scroll', textStyle: { color: palette.value.text } },
  series: [{ type: 'pie', radius: ['46%', '76%'], center: ['35%', '50%'], label: { show: false }, itemStyle: { borderColor: palette.value.cardBg, borderWidth: 2 }, data: achievementTypes.value.map(item => ({ name: item.label, value: item.achievementCount })) }],
}))
const sourceOption = computed(() => distributionOption(distributions.value?.sources ?? [], palette.value.series[1]!))
const orgOption = computed(() => distributionOption(distributions.value?.organizations ?? [], palette.value.series[2]!))
const topicOption = computed(() => distributionOption(distributions.value?.topics ?? [], palette.value.series[3]!))
const distributionPanels = computed(() => {
  if (!distributions.value) return []
  return props.section === 'research' ? [
    { key: 'org', title: '机构分布', option: orgOption.value, label: '机构成果分布条形图', items: distributions.value.organizations, summary: '查看机构表格' },
    { key: 'topic', title: '主题分布', option: topicOption.value, label: '主题成果分布条形图', items: distributions.value.topics, summary: '查看主题表格' },
  ] : [
    { key: 'type', title: '成果类型', option: typeOption.value, label: '成果类型分布条形图', items: achievementTypes.value, summary: '查看类型表格' },
    { key: 'source', title: '数据来源', option: sourceOption.value, label: '数据来源分布条形图', items: distributions.value.sources, summary: '查看来源表格' },
  ]
})

async function loadAnalytics(): Promise<void> {
  const sequence = ++querySequence
  isFiltering.value = Boolean(overview.value)
  loading.value = true
  errorMessage.value = ''
  try {
    const requested = cleanFilters()
    const results = await Promise.allSettled([
      analyticsApi.overview(requested),
      analyticsApi.trends(requested),
      analyticsApi.distributions(requested),
      analyticsApi.collaboration(requested),
    ] as const)
    if (sequence !== querySequence) return
    overview.value = results[0].status === 'fulfilled' ? results[0].value : null
    trends.value = results[1].status === 'fulfilled' ? results[1].value : null
    distributions.value = results[2].status === 'fulfilled' ? results[2].value : null
    collaboration.value = results[3].status === 'fulfilled' ? results[3].value : null
    const labels = ['统计概览', '年度趋势', '分类分布', '合作统计']
    errorMessage.value = results.flatMap((result, index) => result.status === 'rejected'
      ? [`${labels[index]}：${toErrorMessage(result.reason)}`] : []).join('；')
  } catch (error) {
    if (sequence === querySequence) errorMessage.value = toErrorMessage(error)
  } finally {
    if (sequence === querySequence) {
      loading.value = false
      isFiltering.value = false
    }
  }
}

function resetFilters(): void {
  searchText.value = ''; searchEntityId.value = undefined
  yearFrom.value = undefined; yearTo.value = undefined
  void loadAnalytics()
}

function cleanFilters(): AnalyticsFilter {
  if (searchText.value.trim() && !searchEntityId.value) throw new Error('请从候选列表选择机构或主题后搜索。')
  if (yearFrom.value !== undefined && yearTo.value !== undefined && yearFrom.value > yearTo.value) throw new Error('起始年份不能晚于结束年份。')
  return { publicationYearFrom: yearFrom.value, publicationYearTo: yearTo.value,
    ...(searchText.value.trim() && searchEntityId.value ? { [searchField.value]: searchEntityId.value } : {}) }
}

function sortCollaborations(items: CollaborationItem[]): CollaborationItem[] {
  return [...items].sort((left, right) => collaborationSort.value === 'count'
    ? right.sharedAchievementCount - left.sharedAchievementCount
    : `${left.leftLabel}${left.rightLabel}`.localeCompare(`${right.leftLabel}${right.rightLabel}`, 'zh-CN'))
}

onMounted(loadAnalytics)
</script>

<template>
  <section class="page-stack analytics-page">
    <header class="analytics-toolbar">
      <h1>{{ sectionTitle }}</h1>
      <div class="analytics-actions">
        <CompactFieldSearch v-model="searchText" v-model:entity-id="searchEntityId" :field="searchField" :fields="searchFields" :loading="loading" @update:field="changeSearchField" @submit="loadAnalytics" />
        <ElButton text size="small" @click="resetFilters">重置</ElButton>
        <ElPopover v-if="session.hasPermission('EXPORT_CREATE')" trigger="click" placement="bottom-end" :width="260">
          <template #reference><ElButton size="small" aria-label="导出统计"><Download :size="14" class="mr-1" />导出</ElButton></template>
          <p class="text-xs mb-3">导出当前查询范围内已加载的统计结果，合作排行最多二十项。</p>
          <ElButton size="small" :disabled="!exportReady" @click="downloadStatistics('CSV')">导出 CSV</ElButton><ElButton size="small" :disabled="!exportReady" @click="downloadStatistics('JSON')">导出 JSON</ElButton>
        </ElPopover>
      </div>
    </header>
    <div class="analytics-meta">
      <nav class="workspace-tabs" aria-label="统计分类">
        <RouterLink to="/analytics" :aria-current="section === 'overview' ? 'page' : undefined">发表趋势</RouterLink>
        <RouterLink to="/analytics/coverage" :aria-current="section === 'coverage' ? 'page' : undefined">字段覆盖</RouterLink>
        <RouterLink to="/analytics/distributions" :aria-current="section === 'distributions' ? 'page' : undefined">类型与来源</RouterLink>
        <RouterLink to="/analytics/research" :aria-current="section === 'research' ? 'page' : undefined">机构与主题</RouterLink>
        <RouterLink to="/analytics/collaboration" :aria-current="section === 'collaboration' ? 'page' : undefined">合作分析</RouterLink>
      </nav>
      <dl v-if="overview" class="analytics-metrics" :aria-busy="loading"><div v-for="metric in metricCards" :key="metric.label" :title="metric.note"><dt>{{ metric.label }}</dt><dd>{{ metric.value.toLocaleString('zh-CN') }}</dd></div></dl>
      <span class="analytics-scope">实际范围：{{ appliedFilterText }}</span>
      <YearRangeFilter v-if="section !== 'overview'" :from="yearFrom" :to="yearTo" @apply="applyYears" />
      <ElPopover trigger="click" placement="bottom-end" :width="300">
        <template #reference><ElButton text size="small" aria-label="统计口径与更新时间"><Info :size="15" /></ElButton></template>
        <p class="text-xs">规范成果去重；机构、主题和来源按完整计数，一项成果可贡献多个分类，各分类数量不能相加作为成果总量。合作数表示共同署名的规范成果数，不表示合作强度或质量。</p>
        <p v-if="overview" class="text-xs mt-2">数据更新时间 {{ formatDateTime(overview.updatedAt) }}</p>
      </ElPopover>
    </div>

    <ElAlert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" show-icon />
    <LoadingSkeleton v-if="loading && !overview && !trends && !distributions && !collaboration" variant="metrics" />

    <template v-if="overview || trends || distributions || collaboration">
      <div :class="[{ 'analytics-content--summary': section === 'overview' && wideOverview }, isFiltering ? 'opacity-50 transition-opacity' : 'transition-opacity']" :aria-busy="loading" class="analytics-content workspace-fill">
        <PanelSection v-if="section === 'overview' && trends" title="年度成果趋势" class="workspace-panel analytics-chart-panel">
          <template #actions><YearRangeFilter v-if="!showingTables.trend" :from="yearFrom" :to="yearTo" @apply="applyYears" /><ElButton text size="small" :aria-pressed="Boolean(showingTables.trend)" @click="showingTables.trend = !showingTables.trend">{{ showingTables.trend ? '返回趋势图' : '查看趋势表格' }}</ElButton></template>
          <ChartFrame v-if="!showingTables.trend" :option="trendOption" label="年度成果趋势折线图" height="100%" />
          <DataTable v-else :columns="trendColumns" :data="trends.items" :get-row-id="(row) => String(row.publicationYear)" empty-text="暂无趋势数据" dense fill><template #header-publicationYear><span class="flex items-center gap-2">发表年份<YearRangeFilter :from="yearFrom" :to="yearTo" @apply="applyYears" /></span></template></DataTable>
        </PanelSection>
        <template v-if="section === 'overview' && wideOverview">
          <PanelSection v-if="distributions" title="成果类型分布" class="workspace-panel analytics-chart-panel">
            <template #actions><RouterLink to="/analytics/distributions" class="text-sm text-primary">查看分布 →</RouterLink></template>
            <ChartFrame :option="typeRingOption" label="统计成果类型分布环形图" height="100%" />
          </PanelSection>
          <PanelSection v-if="overview?.coverage" title="字段覆盖摘要" class="workspace-panel analytics-coverage">
            <template #actions><RouterLink to="/analytics/coverage" class="text-sm text-primary">查看覆盖明细 →</RouterLink></template>
            <AnalyticsCoveragePanel :coverage="overview.coverage" :total="overview.achievementCount" />
          </PanelSection>
          <PanelSection v-if="collaboration" title="作者合作排行" class="workspace-panel">
            <template #actions><RouterLink to="/analytics/collaboration" class="text-sm text-primary">查看合作分析 →</RouterLink></template>
            <DataTable :columns="collaborationColumns" :data="sortedAuthorCollaborations.slice(0, 5)" :get-row-id="row => `summary-${row.leftId}-${row.rightId}`" empty-text="当前范围没有作者合作关系" dense fill />
          </PanelSection>
        </template>
        <PanelSection v-if="section === 'coverage' && overview?.coverage" title="本地字段覆盖率" subtitle="仅当前范围，非全球采集覆盖率" class="workspace-panel analytics-coverage">
          <AnalyticsCoveragePanel :coverage="overview.coverage" :total="overview.achievementCount" />
        </PanelSection>

        <template v-if="(section === 'distributions' || section === 'research') && distributions">
          <div class="analytics-mobile-switch" role="group" aria-label="分布内容">
            <ElButton v-for="panel in distributionPanels" :key="panel.key" :type="selectedDistribution === panel.key ? 'primary' : 'default'" :aria-pressed="selectedDistribution === panel.key" @click="selectedDistribution = panel.key">{{ panel.title }}</ElButton>
          </div>
          <div class="analytics-pair workspace-fill">
            <PanelSection v-for="panel in distributionPanels" :key="panel.key" :title="panel.title" class="workspace-panel analytics-chart-panel" :class="selectedDistribution !== panel.key ? 'is-mobile-inactive' : ''">
              <template #actions><ElButton text size="small" :aria-pressed="Boolean(showingTables[panel.key])" @click="showingTables[panel.key] = !showingTables[panel.key]">{{ showingTables[panel.key] ? '返回分布图' : panel.summary }}</ElButton></template>
              <ChartFrame v-if="!showingTables[panel.key]" :option="panel.option" :label="panel.label" height="100%" />
              <DataTable v-else :columns="distributionColumns" :data="panel.items" :get-row-id="(row) => String(row.key)" dense fill />
            </PanelSection>
          </div>
        </template>

        <template v-if="section === 'collaboration' && collaboration">
          <div class="analytics-mobile-switch" role="group" aria-label="合作内容">
            <ElButton v-for="item in ([['authors', '作者合作'], ['organizations', '机构合作']] as const)" :key="item[0]" :type="collaborationFocus === item[0] ? 'primary' : 'default'" :aria-pressed="collaborationFocus === item[0]" @click="collaborationFocus = item[0]">{{ item[1] }}</ElButton>
          </div>
          <div class="analytics-pair workspace-fill">
          <PanelSection title="作者合作前 20" subtitle="按共同署名的规范成果数排序" class="workspace-panel" :class="collaborationFocus !== 'authors' ? 'is-mobile-inactive' : ''">
            <template #actions>
              <div class="flex items-center gap-1 rounded-md border border-border p-0.5" role="group" aria-label="合作排行排序">
                <ElButton
                  v-for="opt in ([['count', '共同成果'], ['name', '姓名']] as const)"
                  :key="opt[0]"
                  text
                  size="small"
                  :type="collaborationSort === opt[0] ? 'primary' : 'default'"
                  :aria-pressed="collaborationSort === opt[0]"
                  @click="collaborationSort = opt[0]"
                >
                  {{ opt[1] }}
                </ElButton>
              </div>
            </template>
            <DataTable
              :columns="collaborationColumns"
              :data="sortedAuthorCollaborations"
              :get-row-id="(row) => `author-${row.leftId}-${row.rightId}`"
              empty-text="当前范围没有作者合作关系"
              dense
              fill
            />
          </PanelSection>
          <PanelSection title="机构合作前 20" subtitle="按共同署名的规范成果数排序" class="workspace-panel" :class="collaborationFocus !== 'organizations' ? 'is-mobile-inactive' : ''">
            <DataTable
              :columns="collaborationColumns"
              :data="sortedOrganizationCollaborations"
              :get-row-id="(row) => `org-${row.leftId}-${row.rightId}`"
              empty-text="当前范围没有机构合作关系"
              dense
              fill
            />
          </PanelSection>
          </div>
        </template>
      </div>
    </template>
  </section>
</template>

<style scoped>
.analytics-page { padding: 8px 14px; gap: 7px; }
.analytics-toolbar, .analytics-actions, .analytics-meta, .analytics-metrics, .analytics-metrics > div { display: flex; align-items: center; gap: 9px; min-width: 0; }
.analytics-toolbar { justify-content: space-between; flex-wrap: wrap; }
.analytics-toolbar h1 { font-size: 24px; font-weight: 650; }
.analytics-actions > .el-button + .el-button { margin-left: 0; }
.analytics-meta { flex-wrap: wrap; font-size: 11px; color: hsl(var(--muted-foreground)); }
.analytics-metrics { gap: 16px; }
.analytics-metrics > div { gap: 5px; }
.analytics-metrics dd { color: hsl(var(--primary)); font-size: 16px; font-variant-numeric: tabular-nums; }
.analytics-scope { margin-left: auto; }
.analytics-page .workspace-tabs { padding: 0; border: 0; }
.analytics-page .workspace-tabs a { min-height: 28px; padding: 4px 9px; font-size: 12px; }
.analytics-content { display: flex; flex-direction: column; gap: 9px; }
.analytics-content--summary { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); grid-template-rows: repeat(2, minmax(0, 1fr)); }
.analytics-page :deep(.panel-section__header) { min-height: 36px; padding: 5px 12px; }
.analytics-page :deep(.panel-section__body) { padding: 8px 12px; }
.analytics-pair { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.analytics-chart-panel :deep(.panel-section__body) { display: flex; flex-direction: column; overflow: hidden; }
.analytics-coverage :deep(.panel-section__body > div) { max-width: 900px; }
.analytics-mobile-switch { display: none; flex-shrink: 0; gap: 7px; }
.analytics-mobile-switch .el-button + .el-button { margin-left: 0; }
@media (max-width: 1023px) {
  .analytics-pair { grid-template-columns: minmax(0, 1fr); }
  .analytics-mobile-switch { display: flex; }
  .analytics-pair > .is-mobile-inactive { display: none; }
}
@media (max-width: 700px) {
  .analytics-page { padding: 8px; }
  .analytics-actions { width: 100%; gap: 4px; }
  .analytics-actions > .compact-search { flex: 1; }
  .analytics-metrics { gap: 10px; }
  .analytics-scope { margin-left: 0; }
}
</style>
