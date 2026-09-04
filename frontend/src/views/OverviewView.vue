<script setup lang="ts">
import {
  ArrowRight,
  Collection,
  Connection,
  DataAnalysis,
  Document,
  OfficeBuilding,
  RefreshRight,
  TrendCharts,
  User,
} from '@element-plus/icons-vue'
import type { EChartsCoreOption } from 'echarts/core'
import { ElButton, ElIcon, ElOption, ElSelect } from 'element-plus'
import { computed, onMounted, ref, type Component } from 'vue'

import CountUpNumber from '@/components/CountUpNumber.vue'
import EChartCanvas from '@/components/EChartCanvas.vue'
import { analyticsApi, crawlApi, operationsApi } from '@/services/business'
import { hasPermission, session } from '@/services/session'
import type {
  AnalyticsCollaborationResponse,
  AnalyticsDistributionResponse,
  AnalyticsFilter,
  AnalyticsOverview,
  AnalyticsTrendResponse,
  AuditLog,
  CrawlTask,
  OperationsOverview,
} from '@/types/api'
import { formatDateTime } from '@/utils/format'

interface WorkspaceCard {
  title: string
  description: string
  to: string
  permission: string
  icon: Component
}

const loading = ref(false)
const hasLoaded = ref(false)
const partialError = ref('')
const yearRange = ref('5')
const topicId = ref('')
const overview = ref<AnalyticsOverview | null>(null)
const trends = ref<AnalyticsTrendResponse | null>(null)
const distributions = ref<AnalyticsDistributionResponse | null>(null)
const collaboration = ref<AnalyticsCollaborationResponse | null>(null)
const operations = ref<OperationsOverview | null>(null)
const crawlTasks = ref<CrawlTask[]>([])
const activityLogs = ref<AuditLog[]>([])
const rankingSort = ref<'activity' | 'name'>('activity')

const cards = computed<WorkspaceCard[]>(() => [
  {
    title: '成果检索',
    description: '按题名、作者、机构、年份与来源检索规范成果。',
    to: '/catalog',
    permission: 'CATALOG_READ',
    icon: Collection,
  },
  {
    title: '采集作业',
    description: '配置来源范围、触发执行并检查失败记录。',
    to: '/crawl',
    permission: 'CRAWL_TASK_READ',
    icon: Connection,
  },
  {
    title: '知识图谱',
    description: '探索作者、机构、主题与成果间的关联。',
    to: '/graph',
    permission: 'GRAPH_READ',
    icon: DataAnalysis,
  },
  {
    title: '统计分析',
    description: '查看可追溯的成果趋势、分布和合作统计。',
    to: '/analytics',
    permission: 'ANALYTICS_READ',
    icon: TrendCharts,
  },
].filter((card) => session.user?.permissions.includes(card.permission as never)))

const metricCards = computed(() => overview.value ? [
  { label: '成果总量', value: overview.value.achievementCount, note: '规范成果', icon: Document, tone: 'blue' },
  { label: '数据来源', value: overview.value.sourceCount, note: '当前筛选范围', icon: DataAnalysis, tone: 'cyan' },
  { label: '作者总量', value: overview.value.authorCount, note: '范围内去重', icon: User, tone: 'green' },
  { label: '机构总量', value: overview.value.organizationCount, note: '范围内去重', icon: OfficeBuilding, tone: 'violet' },
] : [])

const topicOptions = computed(() =>
  (distributions.value?.topics ?? [])
    .filter((item) => Number.isSafeInteger(Number(item.key)) && Number(item.key) > 0)
    .slice(0, 20),
)

const activeAuthors = computed(() => {
  const scores = new Map<number, { id: number; name: string; value: number }>()
  for (const item of collaboration.value?.authors ?? []) {
    for (const author of [
      { id: item.leftId, name: item.leftLabel },
      { id: item.rightId, name: item.rightLabel },
    ]) {
      const current = scores.get(author.id)
      scores.set(author.id, {
        ...author,
        value: (current?.value ?? 0) + item.sharedAchievementCount,
      })
    }
  }
  const result = [...scores.values()]
  return result
    .sort((left, right) => rankingSort.value === 'activity'
      ? right.value - left.value || left.name.localeCompare(right.name, 'zh-CN')
      : left.name.localeCompare(right.name, 'zh-CN'))
    .slice(0, 7)
})

const maxAuthorActivity = computed(() => Math.max(1, ...activeAuthors.value.map((item) => item.value)))
const dashboardReady = computed(() => Boolean(overview.value && trends.value && distributions.value && collaboration.value))
const hasAnalyticsPermission = computed(() => hasPermission('ANALYTICS_READ'))

const trendOption = computed<EChartsCoreOption>(() => ({
  aria: { enabled: true, description: '按发表年份统计的成果数量趋势。' },
  animationDuration: 600,
  animationDurationUpdate: 360,
  animationEasing: 'cubicOut',
  color: ['#38a8ff'],
  grid: { left: 44, right: 16, top: 26, bottom: 34 },
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
      lineStyle: { color: '#57bcff', width: 1 },
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
    splitLine: { lineStyle: { color: '#213a57' } },
    axisLabel: { color: '#8296ae' },
  },
  series: [{
    type: 'line',
    name: '成果数',
    smooth: 0.28,
    showSymbol: true,
    symbolSize: 7,
    lineStyle: { width: 3 },
    itemStyle: { borderColor: '#10233d', borderWidth: 2 },
    emphasis: { scale: true, scaleSize: 5 },
    data: trends.value?.items.map((item) => item.achievementCount) ?? [],
  }],
}))

const collaborationOption = computed<EChartsCoreOption>(() => {
  const nodeMap = new Map<number, { id: string; name: string; value: number; symbolSize: number; itemStyle: { color: string } }>()
  const colors = ['#38a8ff', '#35c98c', '#f5a04b', '#a77af2', '#1fc0d8']
  const links = (collaboration.value?.authors ?? []).map((item, index) => {
    for (const author of [
      { id: item.leftId, name: item.leftLabel },
      { id: item.rightId, name: item.rightLabel },
    ]) {
      const current = nodeMap.get(author.id)
      const value = (current?.value ?? 0) + item.sharedAchievementCount
      nodeMap.set(author.id, {
        id: String(author.id),
        name: author.name,
        value,
        symbolSize: Math.min(34, 12 + value * 2),
        itemStyle: { color: colors[author.id % colors.length] },
      })
    }
    return {
      source: String(item.leftId),
      target: String(item.rightId),
      value: item.sharedAchievementCount,
      lineStyle: { width: Math.min(3, 1 + item.sharedAchievementCount / 3) },
      id: `author-link-${index}`,
    }
  })
  return {
    aria: { enabled: true, description: '当前筛选范围内的作者合作网络。' },
    animationDuration: 800,
    animationEasing: 'cubicOut',
    tooltip: { backgroundColor: '#0a1729', borderColor: '#2b4668', textStyle: { color: '#edf6ff' } },
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      data: [...nodeMap.values()],
      links,
      force: { repulsion: 90, gravity: 0.12, edgeLength: [44, 92] },
      label: { show: false, color: '#dfeeff', fontSize: 10 },
      lineStyle: { color: '#52718f', opacity: 0.45, curveness: 0.12 },
      emphasis: {
        focus: 'adjacency',
        scale: true,
        label: { show: true },
        lineStyle: { color: '#63c7ff', opacity: 1, width: 3, type: 'dashed' },
      },
    }],
  }
})

const topicOption = computed<EChartsCoreOption>(() => ({
  aria: { enabled: true, description: '主题成果数量矩形树图。' },
  animationDuration: 600,
  animationEasing: 'cubicOut',
  tooltip: { backgroundColor: '#0a1729', borderColor: '#2b4668', textStyle: { color: '#edf6ff' } },
  series: [{
    type: 'treemap',
    roam: false,
    nodeClick: false,
    breadcrumb: { show: false },
    label: { color: '#f5fbff', fontSize: 11, overflow: 'truncate' },
    itemStyle: { borderColor: '#10233d', borderWidth: 3, gapWidth: 2 },
    color: ['#258ee8', '#18a98b', '#1c9cad', '#6c68d9', '#d57b3e', '#8a668f'],
    data: (distributions.value?.topics ?? []).slice(0, 12).map((item) => ({
      name: item.label,
      value: item.achievementCount,
    })),
  }],
}))

function cleanFilters(): AnalyticsFilter {
  const filters: AnalyticsFilter = {}
  if (yearRange.value !== 'all') {
    filters.publicationYearFrom = new Date().getFullYear() - Number(yearRange.value) + 1
  }
  if (topicId.value) filters.topicId = Number(topicId.value)
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
          analyticsApi.overview(filters),
          analyticsApi.trends(filters),
          analyticsApi.distributions(filters),
          analyticsApi.collaboration(filters),
        ])
        overview.value = nextOverview
        trends.value = nextTrends
        distributions.value = nextDistributions
        collaboration.value = nextCollaboration
      } catch {
        failures.push('统计概览')
      }
    })())
  }

  if (hasPermission('OPERATIONS_READ')) {
    requests.push((async () => {
      try {
        const [nextOperations, audits] = await Promise.all([
          operationsApi.overview(),
          operationsApi.audits(0, 8),
        ])
        operations.value = nextOperations
        activityLogs.value = audits.items
      } catch {
        failures.push('系统活动')
      }
    })())
  }

  if (hasPermission('CRAWL_TASK_READ')) {
    requests.push((async () => {
      try {
        crawlTasks.value = (await crawlApi.tasks(0, 6)).items
      } catch {
        failures.push('采集任务')
      }
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
  <section class="page-stack dashboard-page">
    <header class="dashboard-heading">
      <div>
        <span class="eyebrow">OVERVIEW / REAL-TIME INSIGHTS</span>
        <h1>学术成果可视化驾驶舱</h1>
        <p>从权威数据聚合中快速掌握成果趋势、合作关系与采集运行状态。</p>
      </div>
      <div v-if="hasAnalyticsPermission" class="dashboard-filters" aria-label="全局筛选">
        <label>
          <span>年份区间</span>
          <ElSelect v-model="yearRange" aria-label="年份区间" @change="loadDashboard">
            <ElOption label="近 5 年" value="5" />
            <ElOption label="近 10 年" value="10" />
            <ElOption label="全部年份" value="all" />
          </ElSelect>
        </label>
        <label>
          <span>研究领域</span>
          <ElSelect v-model="topicId" clearable placeholder="全部领域" aria-label="研究领域" @change="loadDashboard">
            <ElOption
              v-for="item in topicOptions"
              :key="item.key"
              :label="item.label"
              :value="item.key"
            />
          </ElSelect>
        </label>
        <ElButton
          class="dashboard-refresh"
          :icon="RefreshRight"
          :loading="loading"
          circle
          aria-label="刷新仪表盘"
          @click="loadDashboard"
        />
      </div>
    </header>

    <p v-if="partialError" class="dashboard-notice" role="status">{{ partialError }}</p>

    <div v-if="loading && !hasLoaded" class="dashboard-skeleton-grid" aria-label="正在加载仪表盘">
      <article v-for="key in ['metric-a', 'metric-b', 'metric-c', 'metric-d']" :key="key" class="dashboard-skeleton-card">
        <span />
        <strong />
        <small />
      </article>
    </div>

    <template v-if="dashboardReady">
      <div class="dashboard-metrics" :class="{ 'is-filtering': loading }">
        <article
          v-for="metric in metricCards"
          :key="metric.label"
          class="dashboard-metric"
          :data-tone="metric.tone"
        >
          <span>{{ metric.label }}</span>
          <strong><CountUpNumber :value="metric.value" /></strong>
          <small>{{ metric.note }}</small>
          <ElIcon aria-hidden="true"><component :is="metric.icon" /></ElIcon>
        </article>
      </div>

      <div class="dashboard-grid" :class="{ 'is-filtering': loading }">
        <article class="dashboard-panel dashboard-trend">
          <header class="panel-heading">
            <div><strong>成果发表趋势</strong><span>按年份 · MySQL 规范数据</span></div>
            <RouterLink to="/analytics" aria-label="查看完整统计"><ElIcon><ArrowRight /></ElIcon></RouterLink>
          </header>
          <EChartCanvas :option="trendOption" label="工作台成果发表趋势折线图" />
        </article>

        <article class="dashboard-panel dashboard-network">
          <header class="panel-heading">
            <div><strong>学者合作网络</strong><span>悬停聚焦 · 滚轮缩放</span></div>
            <span class="panel-live"><i aria-hidden="true" />LIVE</span>
          </header>
          <EChartCanvas :option="collaborationOption" label="工作台作者合作网络图" />
        </article>

        <article class="dashboard-panel dashboard-topics">
          <header class="panel-heading">
            <div><strong>研究领域分布</strong><span>Top 12 主题</span></div>
          </header>
          <EChartCanvas :option="topicOption" label="工作台研究领域矩形树图" />
        </article>

        <article class="dashboard-panel dashboard-ranking">
          <header class="panel-heading">
            <div><strong>合作活跃作者</strong><span>真实共同成果统计</span></div>
            <div class="ranking-sort" aria-label="作者排行排序">
              <button :class="{ active: rankingSort === 'activity' }" @click="rankingSort = 'activity'">共同成果</button>
              <button :class="{ active: rankingSort === 'name' }" @click="rankingSort = 'name'">姓名</button>
            </div>
          </header>
          <TransitionGroup name="rank" tag="ol" class="author-ranking">
            <li v-for="author in activeAuthors" :key="author.id">
              <span class="ranking-avatar" aria-hidden="true"><ElIcon><User /></ElIcon></span>
              <span class="ranking-copy"><strong>{{ author.name }}</strong><small>{{ author.value }} 项共同成果</small></span>
              <span class="ranking-bar"><i :style="{ width: `${author.value / maxAuthorActivity * 100}%` }" /></span>
              <b>{{ author.value }}</b>
            </li>
          </TransitionGroup>
          <p v-if="!activeAuthors.length" class="panel-empty">当前筛选范围暂无作者合作关系</p>
        </article>

        <article class="dashboard-panel dashboard-crawlers">
          <header class="panel-heading">
            <div><strong>采集状态监控</strong><span>当前任务定义与运维摘要</span></div>
            <RouterLink v-if="hasPermission('CRAWL_TASK_READ')" to="/crawl" aria-label="进入采集任务"><ElIcon><ArrowRight /></ElIcon></RouterLink>
          </header>
          <div v-if="hasPermission('CRAWL_TASK_READ')" class="crawler-list">
            <div v-for="task in crawlTasks" :key="task.id" class="crawler-row">
              <span class="status-pulse" :class="task.enabled ? 'is-running' : 'is-idle'" aria-hidden="true" />
              <span><strong>{{ task.name }}</strong><small>来源 #{{ task.sourceId }}</small></span>
              <b>{{ task.enabled ? '已启用' : '已停用' }}</b>
            </div>
            <p v-if="!crawlTasks.length" class="panel-empty">暂无采集任务</p>
          </div>
          <div v-else class="panel-empty">当前账号没有采集任务读取权限</div>
          <footer v-if="operations" class="crawler-summary">
            <span>活动运行 <b>{{ operations.activeCrawlRunCount }}</b></span>
            <span>近 24h 失败 <b>{{ operations.recentCrawlFailureCount }}</b></span>
          </footer>
        </article>

        <article class="dashboard-panel dashboard-activity">
          <header class="panel-heading">
            <div><strong>系统活动日志</strong><span>安全审计摘要</span></div>
            <span v-if="operations" class="panel-live"><i aria-hidden="true" />LIVE</span>
          </header>
          <ol v-if="activityLogs.length" class="activity-stream" aria-live="polite">
            <li v-for="item in activityLogs" :key="item.id" :data-result="item.result">
              <i aria-hidden="true" />
              <span><strong>{{ activitySummary(item) }}</strong><small>{{ formatDateTime(item.createdAt) }}</small></span>
            </li>
          </ol>
          <p v-else class="panel-empty">
            {{ hasPermission('OPERATIONS_READ') ? '暂无审计活动' : '当前账号没有审计日志读取权限' }}
          </p>
        </article>
      </div>
    </template>

    <div v-else-if="hasLoaded" class="workspace-grid dashboard-workspace-grid">
      <RouterLink v-for="card in cards" :key="card.to" :to="card.to" class="workspace-card">
        <ElIcon aria-hidden="true"><component :is="card.icon" /></ElIcon>
        <h2>{{ card.title }}</h2>
        <p>{{ card.description }}</p>
        <span class="text-link">进入模块 <ElIcon><ArrowRight /></ElIcon></span>
      </RouterLink>
    </div>
  </section>
</template>
