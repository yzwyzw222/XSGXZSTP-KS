<script setup lang="ts">
import { ElAlert, ElButton, ElOption, ElSelect } from 'element-plus'
import type { EChartsCoreOption } from 'echarts/core'
import {
  ArrowRight, Building2, FileText, Library, RefreshCw, TrendingUp, Users, Waypoints, Workflow,
} from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref, type Component } from 'vue'
import { RouterLink } from 'vue-router'

import { LiveLogPanel, LoadingSkeleton, PageHeader, PanelSection, StatCard } from '@/components/business'
import ChartFrame from '@/components/business/ChartFrame.vue'
import type { LogEntry } from '@/components/business/types'
import { useChartTheme } from '@/composables/useChartTheme'
import { analyticsApi, crawlApi, operationsApi } from '@/services/business'
import { useSessionStore } from '@/stores/session'
import type {
  AnalyticsCollaborationResponse, AnalyticsDistributionResponse, AnalyticsFilter,
  AnalyticsOverview, AnalyticsTrendResponse, AuditLog, CrawlTask, OperationsOverview, Permission,
} from '@/types/api'
import { formatDateTime } from '@/utils/format'

interface WorkspaceCard { title: string; description: string; to: string; permission: Permission; icon: Component }

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
const collaboration = ref<AnalyticsCollaborationResponse | null>(null)
const operations = ref<OperationsOverview | null>(null)
const crawlTasks = ref<CrawlTask[]>([])
const activityLogs = ref<AuditLog[]>([])
const rankingSort = ref<'activity' | 'name'>('activity')
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
  { label: '机构总量', value: overview.value.organizationCount, note: '范围内去重', icon: Building2, tone: 'violet' as const },
  { label: '数据来源', value: overview.value.sourceCount, note: '当前筛选范围', icon: Library, tone: 'blue' as const },
] : [])

const topicOptions = computed(() =>
  (distributions.value?.topics ?? [])
    .filter((item) => Number.isSafeInteger(Number(item.key)) && Number(item.key) > 0)
    .slice(0, 20))

const activeAuthors = computed(() => {
  const scores = new Map<number, { id: number; name: string; value: number }>()
  for (const item of collaboration.value?.authors ?? []) {
    for (const author of [{ id: item.leftId, name: item.leftLabel }, { id: item.rightId, name: item.rightLabel }]) {
      const current = scores.get(author.id)
      scores.set(author.id, { ...author, value: (current?.value ?? 0) + item.sharedAchievementCount })
    }
  }
  return [...scores.values()]
    .sort((left, right) => rankingSort.value === 'activity'
      ? right.value - left.value || left.name.localeCompare(right.name, 'zh-CN')
      : left.name.localeCompare(right.name, 'zh-CN'))
    .slice(0, 7)
})
const maxAuthorActivity = computed(() => Math.max(1, ...activeAuthors.value.map((item) => item.value)))
const dashboardReady = computed(() => Boolean(overview.value || trends.value || distributions.value || collaboration.value))
const hasAnalyticsPermission = computed(() => hasPermission('ANALYTICS_READ'))

const logEntries = computed<LogEntry[]>(() => activityLogs.value.map((item) => ({
  id: item.id,
  time: formatDateTime(item.createdAt),
  level: item.result === 'SUCCESS' ? 'success' : 'error',
  message: activitySummary(item),
})))

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
    lineStyle: { width: 2.5 }, areaStyle: { opacity: 0.06 }, emphasis: { scale: true, scaleSize: 3 },
    data: trends.value?.items.map((item) => ({ id: String(item.publicationYear), name: String(item.publicationYear), value: item.achievementCount })) ?? [],
  }],
}))

const collaborationOption = computed<EChartsCoreOption>(() => {
  const nodeMap = new Map<number, { id: string; name: string; value: number; symbolSize: number; itemStyle: { color: string } }>()
  const colors = palette.value.series
  const links = (collaboration.value?.authors ?? []).map((item) => {
    for (const author of [{ id: item.leftId, name: item.leftLabel }, { id: item.rightId, name: item.rightLabel }]) {
      const current = nodeMap.get(author.id)
      const value = (current?.value ?? 0) + item.sharedAchievementCount
      nodeMap.set(author.id, {
        id: String(author.id), name: author.name, value,
        symbolSize: Math.min(34, 12 + value * 2),
        itemStyle: { color: colors[0]! },
      })
    }
    return {
      source: String(item.leftId), target: String(item.rightId),
      value: item.sharedAchievementCount,
      lineStyle: { width: Math.min(3, 1 + item.sharedAchievementCount / 3) },
      id: `author-link-${item.leftId}-${item.rightId}`,
    }
  })
  return {
    aria: { enabled: true, description: '当前筛选范围内的作者合作网络。' },
    tooltip: {},
    series: [{
      id: 'overview-collaboration', type: 'graph', layout: 'circular', roam: true, draggable: true,
      data: [...nodeMap.values()], links,
      top: 28, bottom: 40, left: 24, right: 24,
      label: { show: true, position: 'bottom', color: palette.value.textMuted, fontSize: 12 },
      lineStyle: { color: palette.value.grid, opacity: 0.6, curveness: 0.12 },
      emphasis: { focus: 'adjacency', scale: true, label: { show: true }, lineStyle: { color: palette.value.series[0], opacity: 1, width: 3, type: 'dashed' } },
    }],
  }
})

const topicOption = computed<EChartsCoreOption>(() => {
  const items = (distributions.value?.topics ?? []).slice(0, 12).reverse()
  return {
    aria: { enabled: true, description: '前十二个研究主题的规范成果数量。' },
    grid: { left: 4, right: 32, top: 12, bottom: 20, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'value', minInterval: 1, axisLabel: { color: palette.value.textMuted }, splitLine: { lineStyle: { color: palette.value.grid } } },
    yAxis: { type: 'category', data: items.map(item => item.label), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: palette.value.textMuted, width: 92, overflow: 'truncate' } },
    series: [{ id: 'overview-topics', type: 'bar', barMaxWidth: 12, data: items.map(item => ({ id: item.key, name: item.label, value: item.achievementCount })), itemStyle: { color: palette.value.series[0], borderRadius: 2 }, label: { show: true, position: 'right', color: palette.value.textMuted } }],
  }
})

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
          analyticsApi.distributions(filters), analyticsApi.collaboration(filters),
        ] as const)
        if (sequence !== loadSequence) return
        overview.value = results[0].status === 'fulfilled' ? results[0].value : null
        trends.value = results[1].status === 'fulfilled' ? results[1].value : null
        distributions.value = results[2].status === 'fulfilled' ? results[2].value : null
        collaboration.value = results[3].status === 'fulfilled' ? results[3].value : null
        const labels = ['统计概览', '成果趋势', '领域分布', '作者合作']
        results.forEach((result, index) => { if (result.status === 'rejected') failures.push(labels[index]!) })
      } catch { failures.push('统计概览') }
    })())
  }
  if (hasPermission('OPERATIONS_READ')) {
    requests.push((async () => {
      try {
        const [nextOperations, audits] = await Promise.all([operationsApi.overview(), operationsApi.audits(0, 8)])
        if (sequence !== loadSequence) return
        operations.value = nextOperations
        activityLogs.value = audits.items
      } catch { failures.push('系统活动') }
    })())
  }
  if (hasPermission('CRAWL_TASK_READ')) {
    requests.push((async () => {
      try {
        const response = await crawlApi.tasks(0, 6)
        if (sequence === loadSequence) crawlTasks.value = response.items
      } catch { failures.push('采集任务') }
    })())
  }

  await Promise.all(requests)
  if (sequence !== loadSequence) return
  partialError.value = failures.length ? `部分数据暂不可用：${failures.join('、')}` : ''
  hasLoaded.value = true
  loading.value = false
}

function activitySummary(item: AuditLog): string {
  const target = item.targetId ? `${item.targetType} #${item.targetId}` : item.targetType
  return `${item.action} · ${target}`
}

onMounted(loadDashboard)
</script>

<template>
  <section class="page-stack overview-page">
    <PageHeader
      title="工作台"
      description="掌握成果积累、研究合作与采集进展。"
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
      <span>MySQL 规范数据</span>
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

      <div class="overview-grid" :class="{ 'is-updating': loading }" :aria-busy="loading">
        <div class="overview-main">
          <PanelSection v-if="trends" title="成果发表趋势" subtitle="按发表年份统计规范成果" class="overview-trend">
            <template #actions>
              <RouterLink to="/analytics" aria-label="查看完整统计" class="text-muted-foreground transition-colors hover:text-foreground"><ArrowRight class="size-4" aria-hidden="true" /></RouterLink>
            </template>
            <ChartFrame :option="trendOption" label="工作台成果发表趋势折线图" height="240px" />
            <details class="mt-3 border-t border-border pt-2 text-sm">
              <summary class="cursor-pointer text-primary">查看趋势数据</summary>
              <ul class="mt-2 grid gap-1"><li v-for="item in trends.items" :key="item.publicationYear">{{ item.publicationYear }} 年：{{ item.achievementCount }} 项成果</li></ul>
            </details>
          </PanelSection>

          <PanelSection v-if="collaboration" title="学者合作网络" subtitle="悬停聚焦 · 滚轮缩放" class="overview-network">
            <ChartFrame :option="collaborationOption" label="工作台作者合作网络图" height="200px" />
            <details class="mt-3 border-t border-border pt-2 text-sm">
              <summary class="cursor-pointer text-primary">查看合作数据</summary>
              <ul class="mt-2 grid gap-1"><li v-for="item in collaboration.authors" :key="`${item.leftId}-${item.rightId}`">{{ item.leftLabel }}与{{ item.rightLabel }}：{{ item.sharedAchievementCount }} 项共同成果</li></ul>
            </details>
          </PanelSection>

          <PanelSection v-if="distributions" title="研究领域分布" subtitle="前 12 主题 · 分类数量不可相加" class="overview-topics">
            <ChartFrame :option="topicOption" label="工作台研究领域条形图" :height="`${Math.max(200, Math.min(12, distributions.topics.length) * 28)}px`" />
            <details class="mt-3 border-t border-border pt-2 text-sm">
              <summary class="cursor-pointer text-primary">查看领域数据</summary>
              <ul class="mt-2 grid gap-1"><li v-for="item in distributions.topics.slice(0, 12)" :key="item.key">{{ item.label }}：{{ item.achievementCount }} 项成果</li></ul>
            </details>
          </PanelSection>

          <PanelSection title="系统活动日志" subtitle="最近一次读取的安全审计摘要" class="overview-activity">
            <LiveLogPanel
              v-if="activityLogs.length" :entries="logEntries" max-height="250px" :auto-scroll="false"
            />
            <p v-else class="py-6 text-center text-sm text-muted-foreground">
              {{ hasPermission('OPERATIONS_READ') ? '暂无审计活动' : '当前账号没有审计日志读取权限' }}
            </p>
          </PanelSection>
        </div>

        <div class="overview-sidebar">
          <PanelSection v-if="collaboration" title="合作活跃作者" subtitle="共同署名成果汇总" class="overview-ranking">
            <template #actions>
              <div class="flex items-center gap-1 rounded-md border border-border p-0.5" role="group" aria-label="作者排行排序">
                <ElButton
                  v-for="opt in ([['activity', '共同成果'], ['name', '姓名']] as const)" :key="opt[0]"
                  text size="small" :type="rankingSort === opt[0] ? 'primary' : 'default'"
                  :aria-pressed="rankingSort === opt[0]"
                  @click="rankingSort = opt[0]"
                >
                  {{ opt[1] }}
                </ElButton>
              </div>
            </template>
            <TransitionGroup name="rank" tag="ol" class="grid gap-1">
              <li v-for="author in activeAuthors" :key="author.id" class="grid grid-cols-[28px_minmax(0,1fr)_minmax(60px,80px)_28px] items-center gap-2 border-b border-border py-1.5 last:border-0">
                <span class="grid size-6 place-items-center rounded-full bg-primary/15 text-primary"><Users class="size-3.5" aria-hidden="true" /></span>
                <span class="min-w-0">
                  <strong class="block truncate text-sm">{{ author.name }}</strong>
                  <small class="block truncate text-xs text-muted-foreground">{{ author.value }} 项共同成果</small>
                </span>
                <span class="h-1 overflow-hidden rounded bg-muted"><i class="block h-full origin-left rounded bg-primary" :style="{ transform: `scaleX(${author.value / maxAuthorActivity})` }" /></span>
                <b class="text-right text-sm tabular-nums text-muted-foreground">{{ author.value }}</b>
              </li>
            </TransitionGroup>
            <p v-if="!activeAuthors.length" class="py-6 text-center text-sm text-muted-foreground">当前筛选范围暂无作者合作关系</p>
          </PanelSection>

          <PanelSection title="采集状态监控" subtitle="任务定义与运维摘要" class="overview-crawl">
            <template #actions>
              <RouterLink v-if="hasPermission('CRAWL_TASK_READ')" to="/crawl" aria-label="进入采集任务" class="text-muted-foreground transition-colors hover:text-foreground"><ArrowRight class="size-4" aria-hidden="true" /></RouterLink>
            </template>
            <div v-if="hasPermission('CRAWL_TASK_READ')" class="grid">
              <div v-for="task in crawlTasks" :key="task.id" class="grid grid-cols-[14px_minmax(0,1fr)_auto] items-center gap-2 border-b border-border py-2 last:border-0">
                <span class="size-2 shrink-0 rounded-full" :class="task.enabled ? 'bg-status-running' : 'bg-status-idle'" aria-hidden="true" />
                <span class="min-w-0">
                  <strong class="block truncate text-sm">{{ task.name }}</strong>
                  <small class="block truncate text-xs text-muted-foreground">来源 #{{ task.sourceId }}</small>
                </span>
                <b class="text-xs" :class="task.enabled ? 'text-success' : 'text-muted-foreground'">{{ task.enabled ? '已启用' : '已停用' }}</b>
              </div>
              <p v-if="!crawlTasks.length" class="py-6 text-center text-sm text-muted-foreground">暂无采集任务</p>
            </div>
            <p v-else class="py-6 text-center text-sm text-muted-foreground">当前账号没有采集任务读取权限</p>
            <footer v-if="operations" class="mt-2 flex justify-between border-t border-border pt-2 text-xs text-muted-foreground">
              <span>活动运行 <b class="text-foreground">{{ operations.activeCrawlRunCount }}</b></span>
              <span>近 24h 失败 <b class="text-foreground">{{ operations.recentCrawlFailureCount }}</b></span>
            </footer>
          </PanelSection>
        </div>
      </div>
    </template>

    <div v-else-if="hasLoaded" class="grid grid-cols-1 gap-4 sm:grid-cols-2">
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
.rank-move {
  transition: transform var(--duration-normal) var(--ease-standard);
}
.overview-grid { display: grid; gap: var(--space-4); transition: opacity var(--duration-fast); }
.overview-grid.is-updating { opacity: .65; }
.overview-main,
.overview-sidebar { display: grid; min-width: 0; align-content: start; gap: var(--space-4); }
@media (min-width: 1280px) {
  .overview-grid { grid-template-columns: minmax(0, 2.15fr) minmax(280px, .95fr); align-items: start; }
  .overview-main { grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr); }
  .overview-trend,
  .overview-activity { grid-column: 1 / -1; }
}
@media (prefers-reduced-motion: reduce) {
  .rank-move { transition: none; }
}
</style>
