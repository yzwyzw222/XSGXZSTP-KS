<script setup lang="ts">
import { ElAlert, ElButton, ElOption, ElSelect } from 'element-plus'
import type { EChartsCoreOption } from 'echarts/core'
import {
  ArrowRight, Building2, FileText, Library, RefreshCw, TrendingUp, Users, Waypoints, Workflow,
} from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref, type Component } from 'vue'
import { RouterLink } from 'vue-router'

import { LoadingSkeleton, PageHeader, PanelSection, StatCard } from '@/components/business'
import ChartFrame from '@/components/business/ChartFrame.vue'
import { useChartTheme } from '@/composables/useChartTheme'
import { analyticsApi } from '@/services/business'
import { useSessionStore } from '@/stores/session'
import type {
  AnalyticsDistributionResponse, AnalyticsFilter,
  AnalyticsOverview, AnalyticsTrendResponse, Permission,
} from '@/types/api'
import { formatDateTime } from '@/utils/format'

interface WorkspaceCard { title: string; description: string; to: string; permission: Permission; icon: Component }

const showingTrendData = ref(false)

const sessionStore = useSessionStore()
const { hasPermission } = sessionStore

const loading = ref(false)
const hasLoaded = ref(false)
const partialError = ref('')
const yearRange = ref('5')
const topicId = ref('ALL')
const overview = ref<AnalyticsOverview | null>(null)
const trends = ref<AnalyticsTrendResponse | null>(null)
const distributions = ref<AnalyticsDistributionResponse | null>(null)
const { palette } = useChartTheme()
let loadSequence = 0
onBeforeUnmount(() => { loadSequence++ })

const cards = computed<WorkspaceCard[]>(() => ([
  { title: '成果检索', description: '按题名、作者、机构、年份与来源检索规范成果。', to: '/catalog', permission: 'CATALOG_READ', icon: Library },
  { title: '采集作业', description: '配置来源范围、触发执行并检查失败记录。', to: '/crawl', permission: 'CRAWL_TASK_READ', icon: Workflow },
  { title: '知识图谱', description: '探索作者、机构、主题与成果间的关联。', to: '/graph', permission: 'GRAPH_READ', icon: Waypoints },
  { title: '统计分析', description: '查看可追溯的成果趋势、分布和合作统计。', to: '/analytics', permission: 'ANALYTICS_READ', icon: TrendingUp },
] satisfies WorkspaceCard[]).filter((card) => hasPermission(card.permission)))

const metricCards = computed(() => overview.value ? [
  { label: '成果总量', value: overview.value.achievementCount, note: '规范成果', icon: FileText, tone: 'blue' as const },
  { label: '作者总量', value: overview.value.authorCount, note: '范围内去重', icon: Users, tone: 'green' as const },
  { label: '机构总量', value: overview.value.organizationCount, note: '范围内去重', icon: Building2, tone: 'amber' as const },
  { label: '数据来源', value: overview.value.sourceCount, note: '当前筛选范围', icon: Library, tone: 'rose' as const },
] : [])

const topicOptions = computed(() =>
  (distributions.value?.topics ?? [])
    .filter((item) => Number.isSafeInteger(Number(item.key)) && Number(item.key) > 0)
    .slice(0, 20))

const dashboardReady = computed(() => Boolean(overview.value || trends.value || distributions.value))
const hasAnalyticsPermission = computed(() => hasPermission('ANALYTICS_READ'))

const trendOption = computed<EChartsCoreOption>(() => ({
  aria: { enabled: true, description: '按发表年份统计的成果数量趋势。' },
  grid: { left: 44, right: 16, top: 26, bottom: 34 },
  tooltip: { trigger: 'axis', axisPointer: { type: 'line', snap: true, lineStyle: { color: palette.value.series[0] } } },
  xAxis: {
    type: 'category', boundaryGap: false,
    data: trends.value?.items.map((item) => item.publicationYear) ?? [],
    axisLine: { lineStyle: { color: palette.value.grid } }, axisLabel: { color: palette.value.textMuted }, axisTick: { show: false },
  },
  yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: palette.value.grid } }, axisLabel: { color: palette.value.textMuted } },
  series: [{
    id: 'overview-trend', type: 'line', name: '成果数', smooth: 0.15, showSymbol: true, symbolSize: 6,
    lineStyle: { width: 2.5 }, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: palette.value.series[0]! }, { offset: 1, color: 'transparent' }] }, opacity: 0.18 }, emphasis: { scale: true, scaleSize: 3 },
    data: trends.value?.items.map((item) => ({ id: String(item.publicationYear), name: String(item.publicationYear), value: item.achievementCount })) ?? [],
  }],
}))

function cleanFilters(): AnalyticsFilter {
  const filters: AnalyticsFilter = {}
  if (yearRange.value !== 'all') filters.publicationYearFrom = new Date().getFullYear() - Number(yearRange.value) + 1
  if (topicId.value !== 'ALL' && topicId.value) filters.topicId = Number(topicId.value)
  return filters
}

/** 首页只读取当前账号有权访问的聚合接口，任一区域失败不会遮蔽其他区域。 */
async function loadDashboard(): Promise<void> {
  const sequence = ++loadSequence
  loading.value = true
  partialError.value = ''
  const failures: string[] = []
  const requests: Promise<void>[] = []

  if (hasPermission('ANALYTICS_READ')) {
    requests.push((async () => {
      try {
        const filters = cleanFilters()
        const results = await Promise.allSettled([
          analyticsApi.overview(filters), analyticsApi.trends(filters),
          analyticsApi.distributions(filters),
        ] as const)
        if (sequence !== loadSequence) return
        overview.value = results[0].status === 'fulfilled' ? results[0].value : null
        trends.value = results[1].status === 'fulfilled' ? results[1].value : null
        distributions.value = results[2].status === 'fulfilled' ? results[2].value : null
        const labels = ['统计概览', '成果趋势', '领域分布']
        results.forEach((result, index) => { if (result.status === 'rejected') failures.push(labels[index]!) })
      } catch { failures.push('统计概览') }
    })())
  }
  await Promise.all(requests)
  if (sequence !== loadSequence) return
  partialError.value = failures.length ? `部分数据暂不可用：${failures.join('、')}` : ''
  hasLoaded.value = true
  loading.value = false
}

onMounted(loadDashboard)
</script>

<template>
  <section class="page-stack overview-page">
    <PageHeader
      title="工作台"
      description="掌握成果积累与发表趋势。"
    >
      <template #actions>
        <div v-if="hasAnalyticsPermission" class="flex flex-wrap items-end gap-2" aria-label="全局筛选">
          <label class="grid gap-1 text-xs">
            <span class="font-medium text-muted-foreground">年份区间</span>
            <ElSelect
              :model-value="yearRange"
              aria-label="年份区间"
              filterable
              style="width: 128px"
              @update:model-value="(value: string) => { yearRange = String(value); loadDashboard() }"
            >
              <ElOption value="5" label="近 5 年" />
              <ElOption value="10" label="近 10 年" />
              <ElOption value="all" label="全部年份" />
            </ElSelect>
          </label>
          <label class="grid gap-1 text-xs">
            <span class="font-medium text-muted-foreground">研究领域</span>
            <ElSelect
              :model-value="topicId"
              aria-label="研究领域"
              filterable
              style="width: 160px"
              @update:model-value="(value: string) => { topicId = String(value); loadDashboard() }"
            >
              <ElOption value="ALL" label="全部领域" />
              <ElOption
                v-for="item in topicOptions"
                :key="item.key"
                :value="item.key"
                :label="item.label"
              />
            </ElSelect>
          </label>
          <ElButton plain circle :loading="loading" aria-label="刷新仪表盘" @click="loadDashboard">
            <RefreshCw class="size-4" aria-hidden="true" />
          </ElButton>
        </div>
      </template>
    </PageHeader>
    <div v-if="overview" class="context-note">
      <span>规范成果数据</span>
      <span>更新于 {{ formatDateTime(overview.updatedAt) }}</span>
      <span role="status">{{ loading ? '正在更新当前范围…' : '按当前范围汇总' }}</span>
    </div>

    <!-- 区域降级使用 role="status" 的礼貌播报，不用虚构零值掩盖失败 -->
    <ElAlert
      v-if="partialError"
      type="warning"
      role="status"
      :closable="false"
      :title="partialError"
      show-icon
    />

    <LoadingSkeleton v-if="loading && !hasLoaded" variant="metrics" />

    <template v-if="dashboardReady">
      <div class="metric-strip" :aria-busy="loading">
        <StatCard v-for="metric in metricCards" :key="metric.label" :label="metric.label" :value="metric.value" :note="metric.note" :icon="metric.icon" :tone="metric.tone" />
      </div>

      <div class="overview-grid workspace-fill" :class="{ 'is-updating': loading }" :aria-busy="loading">
          <PanelSection v-if="trends" title="成果发表趋势" subtitle="按发表年份统计规范成果" class="overview-trend workspace-panel overview-chart-panel">
            <template #actions>
              <ElButton text size="small" :aria-pressed="showingTrendData" @click="showingTrendData = !showingTrendData">{{ showingTrendData ? '返回趋势图' : '查看趋势数据' }}</ElButton>
              <RouterLink to="/analytics" aria-label="查看完整统计" class="text-muted-foreground transition-colors hover:text-foreground"><ArrowRight class="size-4" aria-hidden="true" /></RouterLink>
            </template>
            <ChartFrame v-if="!showingTrendData" :option="trendOption" label="工作台成果发表趋势折线图" height="100%" />
            <ul v-else class="overview-data-list"><li v-for="item in trends.items" :key="item.publicationYear">{{ item.publicationYear }} 年：{{ item.achievementCount }} 项成果</li></ul>
          </PanelSection>

      </div>
    </template>

    <div v-else-if="hasLoaded" class="overview-entry-list grid grid-cols-1 gap-4 sm:grid-cols-2">
      <RouterLink
        v-for="card in cards" :key="card.to" :to="card.to"
        class="group rounded-lg border border-border bg-card p-5 transition-colors hover:bg-accent"
      >
        <component :is="card.icon" class="size-6 text-primary" aria-hidden="true" />
        <h2 class="mt-4 text-lg font-semibold">{{ card.title }}</h2>
        <p class="mt-1.5 max-w-sm text-sm text-muted-foreground">{{ card.description }}</p>
        <span class="mt-3 inline-flex items-center gap-1 text-sm font-medium text-primary">进入模块 <ArrowRight class="size-4 transition-transform group-hover:translate-x-0.5" aria-hidden="true" /></span>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.overview-grid { display: grid; min-height: 0; gap: var(--space-4); transition: opacity var(--duration-fast); }
.overview-grid.is-updating { opacity: .65; }
.overview-chart-panel :deep(.panel-section__body) { display: flex; flex-direction: column; overflow: hidden; }
.overview-data-list { display: grid; align-content: start; gap: var(--space-3); min-height: 0; overflow: auto; font-size: var(--font-size-md); }
.overview-chart-panel :deep(.panel-section__body > .overview-data-list) { flex: 1 1 0; }
.overview-entry-list { min-height: 0; overflow: auto; }
.overview-page > .metric-strip { flex-shrink: 0; }
@media (max-width: 767px) {
  .overview-page :deep(.stat-card) { padding: var(--space-3); }
  .overview-page :deep(.stat-card__icon) { width: 26px; height: 26px; }
  .overview-page :deep(.stat-card__note) { margin-top: var(--space-1); }
}
</style>
