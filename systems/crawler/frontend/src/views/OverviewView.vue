<script setup lang="ts">
import { integrated } from '@/services/portal-auth'
import { ElButton, ElOption, ElSelect } from 'element-plus'
import type { EChartsCoreOption } from 'echarts/core'
import { Building2, FileText, Layers3, RefreshCw, Users } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import DashboardPanel from '@/components/business/DashboardPanel.vue'
import ChartFrame from '@/components/business/ChartFrame.vue'
import ModuleNavigation from '@/components/business/ModuleNavigation.vue'
import { navItems } from '@/config/nav'
import { useChartTheme } from '@/composables/useChartTheme'
import { useDashboard } from '@/composables/useDashboard'
import { useDataSources } from '@/composables/useDataSources'
import { useSessionStore } from '@/stores/session'
import { auditActionLabel } from '@/utils/audit'
import { formatDateTime } from '@/utils/format'

const { state, loading, yearRange, topicId, refresh } = useDashboard()
const session = useSessionStore()
const { sourceName, sourceError, sourceLoading, loadSources } = useDataSources()
onMounted(() => { if (session.hasPermission('SOURCE_READ')) void loadSources() })
const { palette } = useChartTheme()
const showingTrendData = ref(false)
const overview = computed(() => state.overview.data)
const topics = computed(() => state.distributions.data?.topics.slice(0, 6) ?? [])
const topicOptions = computed(() => (state.distributions.data?.topics ?? []).filter(item => Number.isSafeInteger(Number(item.key)) && Number(item.key) > 0))
const coreModules = computed(() => ['/sources', '/catalog', '/crawl', '/graph', '/governance', '/analytics'].flatMap(path => navItems.filter(item => item.to === path && session.hasPermission(item.permission))))
const metricCards = computed(() => [
  { label: '成果总量', value: overview.value?.achievementCount, icon: FileText, to: '/catalog', permission: 'CATALOG_READ' as const },
  { label: '作者总量', value: overview.value?.authorCount, icon: Users, to: '/catalog/authors', permission: 'CATALOG_READ' as const },
  { label: '机构总量', value: overview.value?.organizationCount, icon: Building2, to: '/catalog/organizations', permission: 'CATALOG_READ' as const },
  { label: '数据来源', value: overview.value?.sourceCount, icon: Layers3, to: '/sources', permission: 'SOURCE_READ' as const },
])
const coverage = computed(() => {
  const data = overview.value?.coverage
  const total = overview.value?.achievementCount ?? 0
  if (!data) return []
  return [
    { label: 'DOI', count: data.withDoiCount }, { label: '发表年份', count: data.withPublicationYearCount },
    { label: '摘要', count: data.withAbstractCount }, { label: '引用计数', count: data.withCitationCount },
  ].map(item => ({ ...item, percentage: total > 0 ? Math.min(100, item.count / total * 100) : null }))
})
const topTopicCount = computed(() => Math.max(1, ...topics.value.map(item => item.achievementCount)))
const authorPairs = computed(() => state.collaboration.data?.authors.slice(0, 5) ?? [])
const institutionPairs = computed(() => state.collaboration.data?.organizations.slice(0, 20) ?? [])
const networkNodes = computed(() => {
  const nodes = new Map<number, { id: string; name: string; value: number }>()
  institutionPairs.value.forEach(pair => {
    for (const [id, name] of [[pair.leftId, pair.leftLabel], [pair.rightId, pair.rightLabel]] as const) {
      const node = nodes.get(id) ?? { id: String(id), name, value: 0 }
      node.value += pair.sharedAchievementCount
      nodes.set(id, node)
    }
  })
  return [...nodes.values()]
})
const trendOption = computed<EChartsCoreOption>(() => ({
  aria: { enabled: true, description: '按发表年份统计的规范成果数量' }, tooltip: { trigger: 'axis' },
  grid: { left: 40, right: 12, top: 16, bottom: 27 },
  xAxis: { type: 'category', boundaryGap: false, data: state.trends.data?.items.map(item => item.publicationYear) ?? [], axisLabel: { color: palette.value.textMuted }, axisLine: { lineStyle: { color: palette.value.grid } } },
  yAxis: { type: 'value', minInterval: 1, axisLabel: { color: palette.value.textMuted }, splitLine: { lineStyle: { color: palette.value.grid } } },
  series: [{ id: 'dashboard-trend', type: 'line', name: '成果数', smooth: .2, symbolSize: 6, lineStyle: { width: 2 }, areaStyle: { opacity: .12 }, data: state.trends.data?.items.map(item => item.achievementCount) ?? [] }],
}))
const typeOption = computed<EChartsCoreOption>(() => ({
  aria: { enabled: true, description: '成果类型数量分布' }, tooltip: { trigger: 'item' },
  legend: { orient: 'vertical', right: 0, top: 'middle', textStyle: { color: palette.value.text, fontSize: 12 }, itemWidth: 9, itemHeight: 9, type: 'scroll', formatter: (name: string) => `${name}  ${number(state.distributions.data?.achievementTypes.find(item => item.label === name)?.achievementCount)}` },
  title: { text: number(overview.value?.achievementCount), subtext: '规范成果', left: '32%', top: '39%', textAlign: 'center', itemGap: 3, textStyle: { color: palette.value.text, fontSize: 18, fontWeight: 600 }, subtextStyle: { color: palette.value.textMuted, fontSize: 11 } },
  series: [{ id: 'achievement-types', type: 'pie', radius: ['48%', '76%'], center: ['32%', '50%'], label: { show: false }, itemStyle: { borderColor: '#061B38', borderWidth: 2 }, data: state.distributions.data?.achievementTypes.map(item => ({ name: item.label, value: item.achievementCount })) ?? [] }],
}))
const networkOption = computed<EChartsCoreOption>(() => ({
  aria: { enabled: true, description: '前二十组机构合作关系网络，连线数值为共同成果数，不代表地理位置' },
  tooltip: { trigger: 'item' },
  series: [{ id: 'institution-collaboration', type: 'graph', layout: 'none', left: 100, right: 120, top: 24, bottom: 26, roam: true,
    symbolSize: 15, emphasis: { focus: 'adjacency' },
    label: { show: true, position: 'right', color: palette.value.text, fontSize: 12, width: 100, overflow: 'truncate' },
    itemStyle: { color: palette.value.series[0], shadowBlur: 14, shadowColor: palette.value.series[0] },
    lineStyle: { color: palette.value.series[0], opacity: .65, curveness: .16, width: 1.5 },
    data: networkNodes.value.map((node, i) => ({ ...node, x: Math.cos(i / networkNodes.value.length * Math.PI * 2) * 340, y: Math.sin(i / networkNodes.value.length * Math.PI * 2) * 95, label: { show: networkNodes.value.length <= 14, position: Math.cos(i / networkNodes.value.length * Math.PI * 2) >= 0 ? 'right' : 'left' }, emphasis: { label: { show: true } }, symbolSize: 13 + Math.min(12, Math.log2(node.value + 1) * 2), itemStyle: { color: palette.value.series[i % 3] } })),
    links: institutionPairs.value.map(pair => ({ source: String(pair.leftId), target: String(pair.rightId), value: pair.sharedAchievementCount })),
  }],
}))
function number(value: number | undefined): string { return value === undefined ? '—' : value.toLocaleString('zh-CN') }
</script>

<template>
  <section class="dashboard-page" aria-label="科研可视化大屏">
    <div class="dashboard-toolbar">
      <h1>科研成果分析中枢</h1>
      <span class="dashboard-update">{{ state.overview.data ? `统计更新于 ${formatDateTime(state.overview.data.updatedAt)}` : '按账号权限展示可用模块' }}</span>
      <div class="dashboard-filters" aria-label="全局筛选">
        <template v-if="state.overview.allowed">
          <ElSelect v-model="yearRange" aria-label="年份区间" size="small" @change="refresh"><ElOption value="all" label="全部年份" /><ElOption value="5" label="近 5 年" /><ElOption value="10" label="近 10 年" /></ElSelect>
          <ElSelect v-model="topicId" aria-label="研究领域" filterable size="small" @change="refresh"><ElOption value="ALL" label="全部领域" /><ElOption v-for="item in topicOptions" :key="item.key" :value="item.key" :label="item.label" /></ElSelect>
        </template>
        <ElButton plain size="small" :loading="loading" aria-label="刷新仪表盘" @click="refresh"><RefreshCw :size="14" /><span>刷新</span></ElButton>
      </div>
    </div>
    <div class="dashboard-scroll">
      <div class="dashboard-grid">
        <div class="dashboard-column dashboard-column--left">
          <DashboardPanel title="成果总览" to="/analytics" :region="state.overview">
            <div class="dashboard-metrics">
              <component :is="session.hasPermission(metric.permission) ? RouterLink : 'div'" v-for="metric in metricCards" :key="metric.label" :to="session.hasPermission(metric.permission) ? metric.to : undefined" class="dashboard-metric">
                <span class="dashboard-metric__icon"><component :is="metric.icon" :size="24" /></span><strong>{{ number(metric.value) }}</strong><span>{{ metric.label }}</span>
              </component>
            </div>
          </DashboardPanel>
          <DashboardPanel title="采集任务概况" to="/crawl" :region="state.tasks" :empty="!state.tasks.data?.items.length">
            <p v-if="sourceError">{{ sourceError }} <ElButton link :loading="sourceLoading" @click="loadSources">重试读取数据源</ElButton></p>
            <ul class="dashboard-task-list"><li v-for="task in state.tasks.data?.items.slice(0, 3)" :key="task.id">
              <RouterLink to="/crawl"><strong>{{ task.name }}</strong><span :class="task.enabled ? 'text-success' : 'text-muted-foreground'">{{ task.enabled ? '已启用' : '已停用' }}</span></RouterLink>
              <p>{{ sourceName(task.sourceId) }} · {{ task.parameters?.keyword || '自定义范围' }} · 上限 {{ task.parameters?.maxPages ?? '—' }} 页 / {{ number(task.parameters?.maxRecords) }} 条</p>
            </li></ul>
          </DashboardPanel>
          <DashboardPanel title="研究领域分布" to="/analytics/research" :region="state.distributions" :empty="!topics.length">
            <div class="dashboard-bars"><RouterLink v-for="item in topics" :key="item.key" to="/analytics/research" class="dashboard-bar"><span :title="item.label">{{ item.label }}</span><i><b :style="{ width: `${item.achievementCount / topTopicCount * 100}%` }" /></i><strong>{{ number(item.achievementCount) }}</strong></RouterLink></div>
          </DashboardPanel>
          <DashboardPanel title="年度增长趋势" to="/analytics" :region="state.trends" :empty="!state.trends.data?.items.length" class="dashboard-trend">
            <template #actions><div class="dashboard-chart-tools"><ElButton text size="small" :aria-pressed="showingTrendData" @click="showingTrendData = !showingTrendData">{{ showingTrendData ? '返回趋势图' : '查看趋势数据' }}</ElButton></div></template>
            <ChartFrame v-if="!showingTrendData" :option="trendOption" label="工作台成果发表趋势折线图" height="100%" />
            <ul v-else class="dashboard-data-list"><li v-for="item in state.trends.data?.items" :key="item.publicationYear">{{ item.publicationYear }} 年：{{ item.achievementCount }} 项成果</li></ul>
          </DashboardPanel>
        </div>
        <div class="dashboard-column dashboard-column--center">
          <div class="dashboard-kpis" :aria-busy="state.overview.loading"><div v-for="metric in metricCards" :key="metric.label"><component :is="metric.icon" :size="24" /><strong>{{ number(metric.value) }}</strong><span>{{ metric.label }}</span></div></div>
          <section class="research-core" aria-label="科研模块入口">
            <h2>汇聚学术资源 · 探索知识关联</h2><p>从数据获取到知识发现</p>
            <img :src="'/images/research-core.png'" alt="" aria-hidden="true" class="research-core__image" />
            <div class="research-core__modules"><RouterLink v-for="item in coreModules" :key="item.to" :to="item.to"><component :is="item.icon" :size="24" /><span><strong>{{ item.label }}</strong><small>{{ item.caption }}</small></span></RouterLink></div>
            <span class="research-core__caption">开放 · 共享 · 智能 · 创新</span>
          </section>
          <DashboardPanel title="机构合作网络" to="/analytics/collaboration" :region="state.collaboration" :empty="!institutionPairs.length" class="dashboard-network">
            <div class="dashboard-network__summary"><span><b>{{ networkNodes.length }}</b> 个机构</span><span><b>{{ institutionPairs.length }}</b> 组合作对</span><small>Top 20 共同成果关系</small></div>
            <ChartFrame :option="networkOption" label="机构合作关系网络图" height="100%" />
          </DashboardPanel>
        </div>
        <div class="dashboard-column dashboard-column--right">
          <DashboardPanel title="作者合作排行" to="/analytics/collaboration" :region="state.collaboration" :empty="!authorPairs.length">
            <div class="dashboard-ranking-heading"><span>合作双方</span><span>共同成果</span></div>
            <ol class="dashboard-ranking"><li v-for="(pair, index) in authorPairs" :key="`${pair.leftId}-${pair.rightId}`"><em>{{ index + 1 }}</em><RouterLink to="/analytics/collaboration">{{ pair.leftLabel }}<span> × </span>{{ pair.rightLabel }}</RouterLink><strong>{{ number(pair.sharedAchievementCount) }}</strong></li></ol>
          </DashboardPanel>
          <DashboardPanel title="成果类型分布" to="/analytics/distributions" :region="state.distributions" :empty="!state.distributions.data?.achievementTypes.some(item => item.achievementCount > 0)" class="dashboard-types"><ChartFrame :option="typeOption" label="成果类型分布环形图" height="100%" /></DashboardPanel>
          <DashboardPanel title="字段覆盖率" to="/analytics/coverage" :region="state.overview" :empty="!coverage.length">
            <div class="dashboard-bars"><RouterLink v-for="item in coverage" :key="item.label" to="/analytics/coverage" class="dashboard-bar dashboard-bar--coverage"><span>{{ item.label }}</span><i><b :style="{ width: `${item.percentage ?? 0}%` }" /></i><strong>{{ item.percentage === null ? '—' : `${item.percentage.toFixed(1)}%` }}</strong></RouterLink></div>
          </DashboardPanel>
          <DashboardPanel v-if="!integrated" title="近期操作" to="/logs" :region="state.audits" :empty="!state.audits.data?.items.length">
            <ul class="dashboard-audits"><li v-for="log in state.audits.data?.items" :key="log.id"><span :class="log.result === 'SUCCESS' ? 'text-success' : 'text-destructive'">{{ log.result === 'SUCCESS' ? '成功' : '失败' }}</span><RouterLink to="/logs">{{ auditActionLabel(log) }}</RouterLink><time :title="formatDateTime(log.createdAt)">{{ new Date(log.createdAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }) }}</time></li></ul>
            <p class="dashboard-read-time">读取于 {{ formatDateTime(state.audits.loadedAt) }}</p>
          </DashboardPanel>
        </div>
      </div>
      <ModuleNavigation dock />
    </div>
  </section>
</template>

<style>
.dashboard-page { display: flex; flex-direction: column; height: 100%; min-height: 0; padding: 10px 16px 12px; gap: 10px; }
.dashboard-toolbar { display: flex; align-items: center; gap: 16px; min-height: 32px; flex-shrink: 0; }
.dashboard-toolbar h1 { font-size: 16px; font-weight: 600; letter-spacing: .12em; white-space: nowrap; color: #b2e9ff; }
.dashboard-update { font-size: 12px; color: hsl(var(--muted-foreground)); }
.dashboard-filters { margin-left: auto; display: flex; align-items: center; gap: 8px; }
.dashboard-filters .el-select { width: 120px; }
.dashboard-scroll { display: flex; flex-direction: column; flex: 1; min-height: 0; overflow: auto; gap: 12px; padding-block: 2px; scroll-padding-block: 4px; }
.dashboard-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.88fr) minmax(0, 1fr); gap: 12px; flex: 1 0 670px; min-height: 670px; }
.dashboard-column { display: grid; gap: 10px; min-width: 0; min-height: 0; }
.dashboard-column--left { grid-template-rows: 1fr 1fr 1fr 1.1fr; }
.dashboard-column--center { grid-template-rows: 98px minmax(280px, 1.3fr) minmax(200px, 1fr); }
.dashboard-column--right { grid-template-rows: 1fr 1.1fr .85fr 1.05fr; }
.dashboard-panel { scroll-margin-block: 4px; display: flex; flex-direction: column; min-width: 0; min-height: 0; overflow: hidden; border-color: #008cbe; box-shadow: 0 0 10px #00b4ff22, inset 0 0 18px #0085cf0d; }
.dashboard-panel .panel-section__header { flex-shrink: 0; min-height: 34px; padding: 7px 12px; }
.dashboard-panel .panel-section__title { font-size: 15px; letter-spacing: .1em; }
.dashboard-panel .panel-section__body { display: flex; flex-direction: column; flex: 1; min-height: 0; padding: 10px 12px; overflow: auto; }
.dashboard-panel__link { display: grid; place-items: center; min-width: 24px; min-height: 24px; color: hsl(var(--primary)); }
.dashboard-state { display: grid; place-content: center; flex: 1; color: hsl(var(--muted-foreground)); font-size: 14px; padding: 10px 0; }
.dashboard-state--compact { flex: 0; padding: 0 0 4px; font-size: 12px; }
.dashboard-state--error { color: hsl(var(--warning)); flex: 0; }
.dashboard-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); align-items: center; flex: 1; gap: 6px; }
.dashboard-metric { display: flex; flex-direction: column; align-items: center; gap: 5px; min-width: 0; font-size: 12px; text-align: center; }
.dashboard-metric strong { font-size: clamp(17px, 1.3vw, 26px); font-variant-numeric: tabular-nums; color: #00e0ff; line-height: 1.2; }
.dashboard-metric__icon { display: grid; place-items: center; width: 50px; height: 50px; border: 1px solid #0496d3; outline: 1px solid #124770; outline-offset: 3px; border-radius: 50%; color: #00d8ff; background: radial-gradient(#095985, #032147 70%); box-shadow: inset 0 0 12px #00cfff25; }
.dashboard-metric:nth-child(2) .dashboard-metric__icon { color: #4ee4c5; border-color: #28bdab; }
.dashboard-metric:nth-child(3) .dashboard-metric__icon { color: #ffcf62; border-color: #b48e3a; }
.dashboard-metric:nth-child(4) .dashboard-metric__icon { color: #a9b7ff; }
.dashboard-kpis { display: grid; grid-template-columns: repeat(4, minmax(0,1fr)); gap: 8px; }
.dashboard-kpis > div { display: flex; flex-direction: column; justify-content: center; align-items: center; gap: 3px; border: 1px solid #167fab; border-radius: 6px; background: linear-gradient(#092b53, #04172f); box-shadow: inset 0 -3px 8px #00cfff18; }
.dashboard-kpis svg { color: #6bdeff; }
.dashboard-kpis strong { color: #00e0ff; font-size: 25px; line-height: 1.2; font-variant-numeric: tabular-nums; }
.dashboard-kpis span { color: #c1d6ed; font-size: 14px; }
.research-core { position: relative; display: flex; flex-direction: column; align-items: center; min-height: 0; padding: 14px 8px 20px; overflow: hidden; background: radial-gradient(ellipse at 50% 65%, #0063b025, transparent 65%); }
.research-core h2 { z-index: 1; font-size: clamp(17px, 1.4vw, 24px); letter-spacing: .1em; font-weight: 600; color: #dbf4ff; }
.research-core > p { z-index: 1; font-size: 12px; color: #90badb; letter-spacing: .2em; margin-top: 5px; }
.research-core__image { position: absolute; top: 26px; bottom: 0; width: 88%; height: calc(100% - 26px); object-fit: contain; transform: scale(1.22); pointer-events: none; }
.research-core__modules { position: relative; flex: 1; display: grid; grid-template-columns: 1fr 1fr; align-content: space-around; width: 100%; column-gap: 40%; padding: 18px 8px 0; }
.research-core__modules a { display: flex; align-items: center; gap: 10px; width: fit-content; padding: 9px 10px; border: 1px solid #17749a; border-radius: 4px; background: #03162edb; box-shadow: inset 0 0 12px #00bfff1a; }
.research-core__modules a:nth-child(even) { justify-self: end; }
.research-core__modules a:hover { border-color: #00cfff; background: #064a6d; }
.research-core__modules svg { color: #38d7ff; flex-shrink: 0; }
.research-core__modules strong { display: block; font-size: 14px; white-space: nowrap; }
.research-core__modules small { display: block; font-size: 10px; color: #a4c4e5; }
.research-core__caption { position: absolute; bottom: 0; color: #a2d8f4; letter-spacing: .4em; font-size: 12px; }
.dashboard-task-list { display: grid; gap: 4px; font-size: 12px; }
.dashboard-task-list li + li { border-top: 1px solid hsl(var(--border)); padding-top: 4px; }
.dashboard-task-list a { display: flex; justify-content: space-between; gap: 12px; font-size: 14px; line-height: 1.4; }
.dashboard-task-list strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 500; }
.dashboard-task-list a > span { white-space: nowrap; font-size: 12px; }
.dashboard-task-list p { color: hsl(var(--muted-foreground)); margin-top: 2px; line-height: 1.4; overflow-wrap: anywhere; }
.dashboard-bars { display: grid; gap: 6px; align-content: center; flex: 1; }
.dashboard-bar { display: grid; grid-template-columns: minmax(60px, 1.2fr) minmax(40px, 1.3fr) auto; gap: 8px; align-items: center; font-size: 14px; line-height: 1.4; }
.dashboard-bar > span { white-space: nowrap; text-overflow: ellipsis; overflow: hidden; }
.dashboard-bar i { height: 8px; background: #08365d; overflow: hidden; border-radius: 2px; }
.dashboard-bar b { display: block; height: 100%; background: linear-gradient(90deg, #087ffa, #16d9ef); }
.dashboard-bar strong { font-weight: 400; font-variant-numeric: tabular-nums; }
.dashboard-chart-tools { display: flex; justify-content: flex-end; flex-shrink: 0; height: 24px; }
.dashboard-chart-tools .el-button { min-height: 22px; height: 22px; font-size: 12px; }
.dashboard-panel .analytics-chart-frame { flex: 1 1 0; min-height: 100px; }
.dashboard-network .panel-section__body { position: relative; }
.dashboard-network__summary { display: flex; gap: 16px; align-items: center; font-size: 12px; flex-shrink: 0; }
.dashboard-network__summary b { color: #00dfff; font-size: 20px; margin-right: 4px; }
.dashboard-network__summary small { margin-left: auto; color: #a3bcd7; }
.dashboard-ranking-heading { display: flex; justify-content: space-between; color: #9db9d5; font-size: 12px; padding-bottom: 5px; border-bottom: 1px solid hsl(var(--border)); }
.dashboard-ranking { font-size: 14px; }
.dashboard-ranking li { display: flex; align-items: center; gap: 9px; padding: 2px 0; line-height: 1.4; border-bottom: 1px solid #123957; }
.dashboard-ranking em { font-style: normal; color: #8fa8c2; width: 18px; text-align: center; background: #0b3157; border: 1px solid #195079; border-radius: 2px; font-size: 12px; }
.dashboard-ranking li:first-child em { color: #ffdf89; background: #4a3a16; border-color: #9e7b29; }
.dashboard-ranking a { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dashboard-ranking a span { color: #688aa9; }
.dashboard-ranking strong { color: #b5eaff; font-weight: 500; }
.dashboard-audits { font-size: 12px; }
.dashboard-audits li { display: flex; gap: 8px; padding: 6px 0; align-items: center; border-bottom: 1px solid #123957; }
.dashboard-audits a { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dashboard-audits time,.dashboard-read-time { color: #a3bcd7; font-size: 11px; }
.dashboard-read-time { margin-top: 8px; }
.dashboard-data-list { font-size: 14px; display: grid; gap: 8px; }
@media (max-width: 1599px) { .dashboard-grid { gap: 10px; flex-basis: 620px; min-height: 620px; } .dashboard-panel .panel-section__title { font-size: 14px; letter-spacing: .03em; } .dashboard-panel .panel-section__body { padding: 8px 10px; } .dashboard-metric__icon { width: 40px; height: 40px; } .research-core__modules { column-gap: 30%; padding-inline: 0; } .research-core__modules a { padding: 7px; gap: 6px; } .dashboard-kpis strong { font-size: 23px; } }
@media (max-width: 1279px) { .dashboard-update { display: none; } .research-core__modules { column-gap: 20%; } .research-core__modules svg { width: 18px; } .dashboard-metric__icon { width: 32px; height: 32px; } .dashboard-metric__icon svg { width: 19px; } .dashboard-metric { font-size: 11px; } }
@media (max-width: 1023px) { .dashboard-grid { display: flex; flex-direction: column; flex: none; min-height: 0; } .dashboard-column--center { order: -1; grid-template-rows: 96px 340px 280px; } .dashboard-column--left,.dashboard-column--right { grid-template-columns: repeat(2, minmax(0,1fr)); grid-template-rows: repeat(2, 230px); } .dashboard-toolbar { flex-wrap: wrap; gap: 8px; } .dashboard-filters { margin-left: auto; } .dashboard-panel__link { min-height: 28px; min-width: 28px; } }
@media (max-width: 639px) { .dashboard-page { padding: 12px; } .dashboard-toolbar h1 { font-size: 15px; } .dashboard-filters { width: 100%; } .dashboard-filters .el-select { width: 110px; } .dashboard-column--left,.dashboard-column--right { display: flex; flex-direction: column; } .dashboard-column--left > .dashboard-panel,.dashboard-column--right > .dashboard-panel { min-height: 220px; max-height: 300px; } .dashboard-panel .panel-section__body { min-height: 150px; } .dashboard-kpis { gap: 5px; } .dashboard-kpis strong { font-size: 21px; } .dashboard-kpis span { font-size: 12px; } .research-core h2 { font-size: 16px; } .research-core__modules a { padding: 8px 6px; gap: 6px; } .research-core__modules strong { font-size: 13px; } .research-core__modules { column-gap: 14%; } .dashboard-network__summary { gap: 10px; } .dashboard-network__summary small { font-size: 10px; } }
</style>
