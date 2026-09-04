<script setup lang="ts">
import type { EChartsCoreOption } from 'echarts/core'
import {
  ArrowRight, Building2, FileText, Library, RefreshCw, TrendingUp, Users, Waypoints, Workflow,
} from 'lucide-vue-next'
import { computed, onMounted, ref, type Component } from 'vue'
import { RouterLink } from 'vue-router'

import { ChartFrame, LiveLogPanel, LoadingSkeleton, PageHeader, PanelSection, StatCard, StatusPill } from '@/components/business'
import type { LogEntry } from '@/components/business/types'
import { Alert, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger } from '@/components/ui/select'
import { useChartTheme } from '@/composables/useChartTheme'
import { analyticsApi, crawlApi, operationsApi } from '@/services/business'
import { hasPermission, session } from '@/services/session'
import type {
  AnalyticsCollaborationResponse, AnalyticsDistributionResponse, AnalyticsFilter,
  AnalyticsOverview, AnalyticsTrendResponse, AuditLog, CrawlTask, OperationsOverview,
} from '@/types/api'
import { formatDateTime } from '@/utils/format'

interface WorkspaceCard { title: string; description: string; to: string; permission: string; icon: Component }

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

const cards = computed<WorkspaceCard[]>(() => [
  { title: '成果检索', description: '按题名、作者、机构、年份与来源检索规范成果。', to: '/catalog', permission: 'CATALOG_READ', icon: Library },
  { title: '采集作业', description: '配置来源范围、触发执行并检查失败记录。', to: '/crawl', permission: 'CRAWL_TASK_READ', icon: Workflow },
  { title: '知识图谱', description: '探索作者、机构、主题与成果间的关联。', to: '/graph', permission: 'GRAPH_READ', icon: Waypoints },
  { title: '统计分析', description: '查看可追溯的成果趋势、分布和合作统计。', to: '/analytics', permission: 'ANALYTICS_READ', icon: TrendingUp },
].filter((card) => session.user?.permissions.includes(card.permission as never)))

const metricCards = computed(() => overview.value ? [
  { label: '成果总量', value: overview.value.achievementCount, note: '规范成果', icon: FileText, tone: 'blue' as const },
  { label: '数据来源', value: overview.value.sourceCount, note: '当前筛选范围', icon: Building2, tone: 'cyan' as const },
  { label: '作者总量', value: overview.value.authorCount, note: '范围内去重', icon: Users, tone: 'green' as const },
  { label: '机构总量', value: overview.value.organizationCount, note: '范围内去重', icon: Building2, tone: 'violet' as const },
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
const dashboardReady = computed(() => Boolean(overview.value && trends.value && distributions.value && collaboration.value))
const hasAnalyticsPermission = computed(() => hasPermission('ANALYTICS_READ'))

const logEntries = computed<LogEntry[]>(() => activityLogs.value.map((item) => ({
  id: item.id,
  time: formatDateTime(item.createdAt),
  level: item.result === 'SUCCESS' ? 'success' : 'error',
  message: activitySummary(item),
})))

const trendOption = computed<EChartsCoreOption>(() => ({
  aria: { enabled: true, description: '按发表年份统计的成果数量趋势。' },
  animationDuration: 600, animationEasing: 'cubicOut',
  grid: { left: 44, right: 16, top: 26, bottom: 34 },
  tooltip: { trigger: 'axis', axisPointer: { type: 'line', snap: true, lineStyle: { color: palette.value.series[0] } } },
  xAxis: {
    type: 'category', boundaryGap: false,
    data: trends.value?.items.map((item) => item.publicationYear) ?? [],
    axisLine: { lineStyle: { color: palette.value.grid } }, axisLabel: { color: palette.value.textMuted }, axisTick: { show: false },
  },
  yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: palette.value.grid } }, axisLabel: { color: palette.value.textMuted } },
  series: [{
    type: 'line', name: '成果数', smooth: 0.28, showSymbol: true, symbolSize: 7,
    lineStyle: { width: 3 }, areaStyle: { opacity: 0.12 }, emphasis: { scale: true, scaleSize: 5 },
    data: trends.value?.items.map((item) => item.achievementCount) ?? [],
  }],
}))

const collaborationOption = computed<EChartsCoreOption>(() => {
  const nodeMap = new Map<number, { id: string; name: string; value: number; symbolSize: number; itemStyle: { color: string } }>()
  const colors = palette.value.series
  const links = (collaboration.value?.authors ?? []).map((item, index) => {
    for (const author of [{ id: item.leftId, name: item.leftLabel }, { id: item.rightId, name: item.rightLabel }]) {
      const current = nodeMap.get(author.id)
      const value = (current?.value ?? 0) + item.sharedAchievementCount
      nodeMap.set(author.id, {
        id: String(author.id), name: author.name, value,
        symbolSize: Math.min(34, 12 + value * 2),
        itemStyle: { color: colors[author.id % colors.length]! },
      })
    }
    return {
      source: String(item.leftId), target: String(item.rightId),
      value: item.sharedAchievementCount,
      lineStyle: { width: Math.min(3, 1 + item.sharedAchievementCount / 3) },
      id: `author-link-${index}`,
    }
  })
  return {
    aria: { enabled: true, description: '当前筛选范围内的作者合作网络。' },
    animationDuration: 800, animationEasing: 'cubicOut',
    tooltip: {},
    series: [{
      type: 'graph', layout: 'force', roam: true, draggable: true,
      data: [...nodeMap.values()], links,
      force: { repulsion: 90, gravity: 0.12, edgeLength: [44, 92] },
      label: { show: false, color: palette.value.text, fontSize: 10 },
      lineStyle: { color: palette.value.grid, opacity: 0.6, curveness: 0.12 },
      emphasis: { focus: 'adjacency', scale: true, label: { show: true }, lineStyle: { color: palette.value.series[0], opacity: 1, width: 3, type: 'dashed' } },
    }],
  }
})

const topicOption = computed<EChartsCoreOption>(() => ({
  aria: { enabled: true, description: '主题成果数量矩形树图。' },
  animationDuration: 600, animationEasing: 'cubicOut',
  tooltip: {},
  series: [{
    type: 'treemap', roam: false, nodeClick: false, breadcrumb: { show: false },
    label: { color: palette.value.text, fontSize: 11, overflow: 'truncate' },
    itemStyle: { borderColor: palette.value.cardBg, borderWidth: 3, gapWidth: 2 },
    color: palette.value.series,
    data: (distributions.value?.topics ?? []).slice(0, 12).map((item) => ({ name: item.label, value: item.achievementCount })),
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
  loading.value = true
  partialError.value = ''
  const failures: string[] = []
  const requests: Promise<void>[] = []

  if (hasPermission('ANALYTICS_READ')) {
    requests.push((async () => {
      try {
        const filters = cleanFilters()
        const [nextOverview, nextTrends, nextDistributions, nextCollaboration] = await Promise.all([
          analyticsApi.overview(filters), analyticsApi.trends(filters),
          analyticsApi.distributions(filters), analyticsApi.collaboration(filters),
        ])
        overview.value = nextOverview
        trends.value = nextTrends
        distributions.value = nextDistributions
        collaboration.value = nextCollaboration
      } catch { failures.push('统计概览') }
    })())
  }
  if (hasPermission('OPERATIONS_READ')) {
    requests.push((async () => {
      try {
        const [nextOperations, audits] = await Promise.all([operationsApi.overview(), operationsApi.audits(0, 8)])
        operations.value = nextOperations
        activityLogs.value = audits.items
      } catch { failures.push('系统活动') }
    })())
  }
  if (hasPermission('CRAWL_TASK_READ')) {
    requests.push((async () => {
      try { crawlTasks.value = (await crawlApi.tasks(0, 6)).items } catch { failures.push('采集任务') }
    })())
  }

  await Promise.all(requests)
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
  <section class="page-stack">
    <PageHeader
      title="学术成果可视化驾驶舱"
      description="从权威数据聚合中快速掌握成果趋势、合作关系与采集运行状态。"
    >
      <template #actions>
        <div v-if="hasAnalyticsPermission" class="flex flex-wrap items-end gap-2" aria-label="全局筛选">
          <label class="grid gap-1 text-xs">
            <span class="font-medium text-muted-foreground">年份区间</span>
            <Select :model-value="yearRange" @update:model-value="(v) => { yearRange = String(v); loadDashboard() }">
              <SelectTrigger class="h-9 w-32" aria-label="年份区间" />
              <SelectContent>
                <SelectItem value="5">近 5 年</SelectItem>
                <SelectItem value="10">近 10 年</SelectItem>
                <SelectItem value="all">全部年份</SelectItem>
              </SelectContent>
            </Select>
          </label>
          <label class="grid gap-1 text-xs">
            <span class="font-medium text-muted-foreground">研究领域</span>
            <Select :model-value="topicId" @update:model-value="(v) => { topicId = String(v); loadDashboard() }">
              <SelectTrigger class="h-9 w-40" aria-label="研究领域" />
              <SelectContent>
                <SelectItem value="ALL">全部领域</SelectItem>
                <SelectItem v-for="item in topicOptions" :key="item.key" :value="item.key">{{ item.label }}</SelectItem>
              </SelectContent>
            </Select>
          </label>
          <Button variant="outline" size="icon" :loading="loading" aria-label="刷新仪表盘" @click="loadDashboard">
            <RefreshCw class="size-4" />
          </Button>
        </div>
      </template>
    </PageHeader>

    <Alert v-if="partialError" variant="warning" role="status"><AlertTitle>{{ partialError }}</AlertTitle></Alert>

    <LoadingSkeleton v-if="loading && !hasLoaded" variant="metrics" />

    <template v-if="dashboardReady">
      <div class="grid grid-cols-2 gap-3 lg:grid-cols-4" :class="loading ? 'opacity-50 transition-opacity' : 'transition-opacity'">
        <StatCard v-for="metric in metricCards" :key="metric.label" :label="metric.label" :value="metric.value" :note="metric.note" :icon="metric.icon" :tone="metric.tone" />
      </div>

      <div class="grid grid-cols-1 gap-4 lg:grid-cols-2" :class="loading ? 'opacity-50 transition-opacity' : 'transition-opacity'">
        <PanelSection title="成果发表趋势" subtitle="按年份 · MySQL 规范数据" class="lg:col-span-2">
          <template #actions>
            <RouterLink to="/analytics" aria-label="查看完整统计" class="text-muted-foreground hover:text-foreground"><ArrowRight class="size-4" /></RouterLink>
          </template>
          <ChartFrame :option="trendOption" label="工作台成果发表趋势折线图" height="300px" />
        </PanelSection>

        <PanelSection title="学者合作网络" subtitle="悬停聚焦 · 滚轮缩放">
          <template #actions><StatusPill status="RUNNING" label="实时" pulse /></template>
          <ChartFrame :option="collaborationOption" label="工作台作者合作网络图" height="300px" />
        </PanelSection>

        <PanelSection title="研究领域分布" subtitle="前 12 主题">
          <ChartFrame :option="topicOption" label="工作台研究领域矩形树图" height="300px" />
        </PanelSection>

        <PanelSection title="合作活跃作者" subtitle="真实共同成果统计">
          <template #actions>
            <div class="flex items-center gap-1 rounded-md border border-border p-0.5" role="group" aria-label="作者排行排序">
              <button
                v-for="opt in ([['activity', '共同成果'], ['name', '姓名']] as const)" :key="opt[0]"
                type="button" :aria-pressed="rankingSort === opt[0]"
                class="rounded px-2 py-0.5 text-xs transition-colors"
                :class="rankingSort === opt[0] ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground'"
                @click="rankingSort = opt[0]"
              >{{ opt[1] }}</button>
            </div>
          </template>
          <TransitionGroup name="rank" tag="ol" class="grid gap-1">
            <li v-for="author in activeAuthors" :key="author.id" class="grid grid-cols-[28px_minmax(0,1fr)_minmax(60px,80px)_28px] items-center gap-2 border-b border-border py-1.5 last:border-0">
              <span class="grid size-6 place-items-center rounded-full bg-primary/15 text-primary"><Users class="size-3.5" aria-hidden="true" /></span>
              <span class="min-w-0">
                <strong class="block truncate text-sm">{{ author.name }}</strong>
                <small class="block truncate text-xs text-muted-foreground">{{ author.value }} 项共同成果</small>
              </span>
              <span class="h-1.5 overflow-hidden rounded-full bg-muted"><i class="block h-full rounded-full bg-primary transition-all" :style="{ width: `${author.value / maxAuthorActivity * 100}%` }" /></span>
              <b class="text-right text-sm tabular-nums text-muted-foreground">{{ author.value }}</b>
            </li>
          </TransitionGroup>
          <p v-if="!activeAuthors.length" class="py-6 text-center text-sm text-muted-foreground">当前筛选范围暂无作者合作关系</p>
        </PanelSection>

        <PanelSection title="采集状态监控" subtitle="当前任务定义与运维摘要">
          <template #actions>
            <RouterLink v-if="hasPermission('CRAWL_TASK_READ')" to="/crawl" aria-label="进入采集任务" class="text-muted-foreground hover:text-foreground"><ArrowRight class="size-4" /></RouterLink>
          </template>
          <div v-if="hasPermission('CRAWL_TASK_READ')" class="grid">
            <div v-for="task in crawlTasks" :key="task.id" class="grid grid-cols-[14px_minmax(0,1fr)_auto] items-center gap-2 border-b border-border py-2 last:border-0">
              <span class="size-2 shrink-0 rounded-full" :class="task.enabled ? 'animate-status-pulse bg-status-running' : 'bg-status-idle'" aria-hidden="true" />
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

        <PanelSection title="系统活动日志" subtitle="安全审计摘要" class="lg:col-span-2">
          <template #actions><StatusPill v-if="operations" status="RUNNING" label="实时" pulse /></template>
          <LiveLogPanel
            v-if="activityLogs.length" :entries="logEntries" max-height="250px" :auto-scroll="false"
          />
          <p v-else class="py-6 text-center text-sm text-muted-foreground">
            {{ hasPermission('OPERATIONS_READ') ? '暂无审计活动' : '当前账号没有审计日志读取权限' }}
          </p>
        </PanelSection>
      </div>
    </template>

    <div v-else-if="hasLoaded" class="grid grid-cols-1 gap-4 sm:grid-cols-2">
      <RouterLink
        v-for="card in cards" :key="card.to" :to="card.to"
        class="group relative rounded-xl border border-border bg-card p-6 shadow-sm transition-all hover:-translate-y-0.5 hover:border-primary/45 hover:shadow-md"
      >
        <component :is="card.icon" class="size-6 text-primary" aria-hidden="true" />
        <h2 class="mt-4 text-lg font-semibold">{{ card.title }}</h2>
        <p class="mt-1.5 max-w-sm text-sm text-muted-foreground">{{ card.description }}</p>
        <span class="mt-3 inline-flex items-center gap-1 text-sm font-medium text-primary">进入模块 <ArrowRight class="size-4 transition-transform group-hover:translate-x-0.5" /></span>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.rank-move {
  transition: transform 350ms var(--ease-standard);
}
@media (prefers-reduced-motion: reduce) {
  .rank-move { transition: none; }
}
</style>
