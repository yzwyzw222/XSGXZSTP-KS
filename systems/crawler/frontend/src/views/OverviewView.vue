<script setup lang="ts">
import { ElButton, ElOption, ElSelect } from 'element-plus'
import type { EChartsCoreOption } from 'echarts/core'
import { Building2, FileText, RefreshCw, Users } from 'lucide-vue-next'
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import DashboardPanel from '@/components/business/DashboardPanel.vue'
import ChartFrame from '@/components/business/ChartFrame.vue'
import { navItems } from '@/config/nav'
import { useChartTheme } from '@/composables/useChartTheme'
import { useDashboard } from '@/composables/useDashboard'
import { importModeLabel } from '@/services/author-import'
import { useSessionStore } from '@/stores/session'
import { auditActionLabel } from '@/utils/audit'
import { achievementTypeLabel } from '@/utils/filter-options'
import { formatDateTime } from '@/utils/format'

const { state, loading, yearRange, topicId, refresh } = useDashboard()
const session = useSessionStore()
const { palette } = useChartTheme()
const showingTrendData = ref(false)
const campusBanner = `${import.meta.env.BASE_URL}images/campus-banner.png`
const overview = computed(() => state.overview.data)
const achievementTypes = computed(() => (state.distributions.data?.achievementTypes ?? []).map(item => ({ ...item, label: achievementTypeLabel(item.key, item.label) })))
const topics = computed(() => state.distributions.data?.topics.slice(0, 6) ?? [])
const topicOptions = computed(() => (state.distributions.data?.topics ?? []).filter(item => Number.isSafeInteger(Number(item.key)) && Number(item.key) > 0))
const coreModules = computed(() => ['/author-import', '/catalog', '/catalog/authors', '/analytics'].flatMap(path => navItems.filter(item => item.to === path && session.hasPermission(item.permission))))
const metricCards = computed(() => [
  { label: '成果总量', value: overview.value?.achievementCount, icon: FileText, to: '/catalog', permission: 'CATALOG_READ' as const },
  { label: '作者总量', value: overview.value?.authorCount, icon: Users, to: '/catalog/authors', permission: 'CATALOG_READ' as const },
  { label: '机构总量', value: overview.value?.organizationCount, icon: Building2, to: '/catalog/organizations', permission: 'CATALOG_READ' as const },
  { label: '包含摘要', value: overview.value?.coverage?.withAbstractCount, icon: FileText, to: '/catalog', permission: 'CATALOG_READ' as const },
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
const authorNetworkPairs = computed(() => state.collaboration.data?.authors.slice(0, 20) ?? [])
const authorNetworkNodes = computed(() => {
  const nodes = new Map<number, { id: string; name: string; value: number }>()
  authorNetworkPairs.value.forEach(pair => {
    for (const [id, name] of [[pair.leftId, pair.leftLabel], [pair.rightId, pair.rightLabel]] as const) {
      const node = nodes.get(id) ?? { id: String(id), name, value: 0 }
      node.value += pair.sharedAchievementCount
      nodes.set(id, node)
    }
  })
  return [...nodes.values()]
})
const authorNetworkOption = computed<EChartsCoreOption>(() => ({
  aria: { enabled: true, description: '前二十组作者共同署名关系，节点表示作者，连线表示共同成果' },
  tooltip: { trigger: 'item' },
  series: [{ id: 'author-collaboration', type: 'graph', layout: 'circular', left: 74, right: 74, top: 42, bottom: 42, roam: true,
    circular: { rotateLabel: false }, emphasis: { focus: 'adjacency' },
    labelLayout: { hideOverlap: true },
    label: { show: true, position: 'bottom', color: palette.value.text, fontSize: 12, distance: 7, width: 90, overflow: 'truncate' },
    itemStyle: { borderColor: palette.value.cardBg, borderWidth: 3 },
    lineStyle: { color: palette.value.series[0], opacity: .4, curveness: .15, width: 1.5 },
    data: authorNetworkNodes.value.map((node, index) => ({ ...node, symbolSize: 18 + Math.min(26, Math.log2(node.value + 1) * 4), itemStyle: { color: palette.value.series[index % 3] } })),
    links: authorNetworkPairs.value.map(pair => ({ source: String(pair.leftId), target: String(pair.rightId), value: pair.sharedAchievementCount })),
  }],
}))
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
  legend: { orient: 'vertical', right: 0, top: 'middle', textStyle: { color: palette.value.text, fontSize: 12 }, itemWidth: 9, itemHeight: 9, type: 'scroll', formatter: (name: string) => `${name}  ${number(achievementTypes.value.find(item => item.label === name)?.achievementCount)}` },
  title: { text: number(overview.value?.achievementCount), subtext: '规范成果', left: '32%', top: '39%', textAlign: 'center', itemGap: 3, textStyle: { color: palette.value.text, fontSize: 18, fontWeight: 600 }, subtextStyle: { color: palette.value.textMuted, fontSize: 11 } },
  series: [{ id: 'achievement-types', type: 'pie', radius: ['48%', '76%'], center: ['32%', '50%'], label: { show: false }, itemStyle: { borderColor: palette.value.cardBg, borderWidth: 2 }, data: achievementTypes.value.map(item => ({ name: item.label, value: item.achievementCount })) }],
}))
const networkOption = computed<EChartsCoreOption>(() => ({
  aria: { enabled: true, description: '前二十组机构合作关系网络，连线数值为共同成果数，不代表地理位置' },
  tooltip: { trigger: 'item' },
  series: [{ id: 'institution-collaboration', type: 'graph', layout: 'circular', left: 74, right: 74, top: 30, bottom: 42, roam: true,
    circular: { rotateLabel: false }, symbolSize: 15, emphasis: { focus: 'adjacency' },
    labelLayout: { hideOverlap: true },
    label: { show: true, position: 'bottom', color: palette.value.text, fontSize: 12, distance: 7, width: 100, overflow: 'truncate' },
    itemStyle: { color: palette.value.series[0], borderColor: palette.value.cardBg, borderWidth: 2 },
    lineStyle: { color: palette.value.series[0], opacity: .45, curveness: .16, width: 1.5 },
    data: networkNodes.value.map((node, i) => ({ ...node, label: { show: networkNodes.value.length <= 14 }, emphasis: { label: { show: true } }, symbolSize: 13 + Math.min(12, Math.log2(node.value + 1) * 2), itemStyle: { color: palette.value.series[i % 3] } })),
    links: institutionPairs.value.map(pair => ({ source: String(pair.leftId), target: String(pair.rightId), value: pair.sharedAchievementCount })),
  }],
}))
function number(value: number | undefined): string { return value === undefined ? '—' : value.toLocaleString('zh-CN') }
</script>

<template>
  <section class="dashboard-page" aria-label="科研可视化大屏">
    <div class="dashboard-toolbar">
      <h1>科研成果概览</h1>
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
            <div class="dashboard-metrics dashboard-kpis">
              <component :is="session.hasPermission(metric.permission) ? RouterLink : 'div'" v-for="metric in metricCards" :key="metric.label" :to="session.hasPermission(metric.permission) ? metric.to : undefined" class="dashboard-metric">
                <span class="dashboard-metric__icon"><component :is="metric.icon" :size="24" /></span><strong>{{ number(metric.value) }}</strong><span>{{ metric.label }}</span>
              </component>
            </div>
          </DashboardPanel>
          <DashboardPanel title="最近作者导入" to="/author-import" :region="state.imports" :empty="!state.imports.data?.length">
            <ul class="dashboard-task-list"><li v-for="batch in state.imports.data?.slice(0, 3)" :key="batch.id">
              <RouterLink to="/author-import"><strong>{{ batch.scholarName }}</strong><span class="text-success">新增 {{ batch.importedCount }} 项</span></RouterLink>
              <p>{{ importModeLabel(batch.importMode) }} · {{ batch.fileName }}</p>
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
          <section class="research-core" aria-label="科研模块入口">
            <h2>汇聚学术资源 · 探索知识关联</h2><p>从数据获取到知识发现</p>
            <img :src="campusBanner" alt="" aria-hidden="true" class="research-core__image" />
            <div class="research-core__modules"><RouterLink v-for="item in coreModules" :key="item.to" :to="item.to"><component :is="item.icon" :size="16" /><span>{{ item.label }}</span></RouterLink></div>
          </section>
          <DashboardPanel title="作者合作网络" to="/analytics/collaboration" :region="state.collaboration" :empty="!authorNetworkPairs.length" class="dashboard-author-network"><div class="dashboard-network__summary"><span><b>{{ authorNetworkNodes.length }}</b> 位作者</span><span><b>{{ authorNetworkPairs.length }}</b> 组合作对</span><small>共同署名成果关系</small></div><ChartFrame :option="authorNetworkOption" label="作者合作关系网络图" height="100%" /></DashboardPanel>
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
          <DashboardPanel title="近期操作" to="/logs" :region="state.audits" :empty="!state.audits.data?.items.length">
            <ul class="dashboard-audits"><li v-for="log in state.audits.data?.items" :key="log.id"><span :class="log.result === 'SUCCESS' ? 'text-success' : 'text-destructive'">{{ log.result === 'SUCCESS' ? '成功' : '失败' }}</span><RouterLink to="/logs">{{ auditActionLabel(log) }}</RouterLink><time :title="formatDateTime(log.createdAt)">{{ new Date(log.createdAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }) }}</time></li></ul>
            <p class="dashboard-read-time">读取于 {{ formatDateTime(state.audits.loadedAt) }}</p>
          </DashboardPanel>
        </div>
      </div>
    </div>
  </section>
</template>

<style>
.dashboard-page { display: flex; flex-direction: column; height: 100%; min-height: 0; padding: 14px 20px 20px; gap: 12px; }
.dashboard-toolbar { display: flex; align-items: center; gap: 16px; min-height: 34px; flex-shrink: 0; }
.dashboard-toolbar h1 { font-size: 19px; font-weight: 600; white-space: nowrap; color: hsl(var(--foreground)); }
.dashboard-update { font-size: 12px; color: hsl(var(--muted-foreground)); }
.dashboard-filters { margin-left: auto; display: flex; align-items: center; gap: 8px; }
.dashboard-filters .el-select { width: 120px; }
.dashboard-scroll { display: flex; flex-direction: column; flex: 1; min-height: 0; overflow: auto; gap: 12px; padding-block: 2px; scroll-padding-block: 4px; }
.dashboard-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.5fr) minmax(0, 1fr); gap: 14px; flex: 1 0 700px; min-height: 700px; }
.dashboard-column { display: grid; gap: 12px; min-width: 0; min-height: 0; }
.dashboard-column--left { grid-template-rows: minmax(158px, 1fr) minmax(218px, 1.15fr) minmax(130px, 1fr) minmax(155px, 1.1fr); }
.dashboard-column--center { grid-template-rows: 160px minmax(250px, 1.3fr) minmax(210px, 1fr); }
.dashboard-column--right { grid-template-rows: minmax(216px, 1.1fr) minmax(150px, 1fr) minmax(164px, 1fr) minmax(130px, 1fr); }
.dashboard-panel { scroll-margin-block: 4px; display: flex; flex-direction: column; min-width: 0; min-height: 0; overflow: hidden; border-color: hsl(var(--border)); border-radius: 12px; background: hsl(var(--card)); box-shadow: 0 2px 8px hsl(var(--primary) / .025); }
.dashboard-panel .panel-section__header { flex-shrink: 0; min-height: 40px; padding: 10px 14px 7px; border-bottom: 0; }
.dashboard-panel .panel-section__title { position: relative; padding-left: 12px; font-size: 16px; letter-spacing: 0; font-weight: 600; }
.dashboard-panel .panel-section__title::before { content: ''; position: absolute; top: 3px; bottom: 3px; left: 0; width: 3px; border-radius: 2px; background: hsl(var(--primary)); }
.dashboard-panel .panel-section__body { display: flex; flex-direction: column; flex: 1; min-height: 0; padding: 6px 14px 8px; overflow: auto; }
.dashboard-panel__link { display: grid; place-items: center; min-width: 24px; min-height: 24px; border-radius: 5px; color: hsl(var(--primary)); }
.dashboard-panel__link:hover { background: hsl(var(--primary) / .07); }
.dashboard-state { display: grid; place-content: center; flex: 1; color: hsl(var(--muted-foreground)); font-size: 13px; padding: 10px 0; }
.dashboard-state--compact { flex: 0; padding: 0 0 4px; font-size: 12px; }
.dashboard-state--error { color: hsl(var(--destructive)); flex: 0; }
.dashboard-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); align-items: stretch; flex: 1; gap: 6px; }
.dashboard-metric { --metric-color: #227ce5; --metric-wash: #edf6ff; display: flex; flex-direction: column; justify-content: center; align-items: center; gap: 4px; padding: 6px 3px; min-width: 0; border-radius: 8px; background: var(--metric-wash); font-size: 14px; line-height: 1.4; text-align: center; }
.dashboard-metric:nth-child(2) { --metric-color: #149674; --metric-wash: #eefaf6; }
.dashboard-metric:nth-child(3) { --metric-color: #c18a2d; --metric-wash: #fff9ef; }
.dashboard-metric:nth-child(4) { --metric-color: #8061b3; --metric-wash: #f7f4fd; }
.dashboard-metric strong { font-size: clamp(18px, 1.4vw, 27px); font-variant-numeric: tabular-nums; color: var(--metric-color); line-height: 1.2; }
.dashboard-metric__icon { display: grid; place-items: center; flex-shrink: 0; width: 32px; height: 32px; border-radius: 50%; color: var(--metric-color); background: color-mix(in srgb, var(--metric-color) 9%, white); }
.dashboard-metric__icon svg { width: 21px; height: 21px; }
.dashboard-metric > span:last-child { color: hsl(var(--muted-foreground)); }
.research-core { position: relative; display: flex; flex-direction: column; justify-content: center; align-items: center; min-height: 0; padding: 18px 12px; overflow: hidden; border: 1px solid hsl(var(--border)); border-radius: 12px; background: #edf6ff; }
.research-core h2 { z-index: 1; font-size: clamp(19px, 1.55vw, 28px); font-weight: 600; color: hsl(var(--foreground)); text-align: center; }
.research-core > p { z-index: 1; font-size: 13px; color: hsl(var(--muted-foreground)); letter-spacing: .12em; margin-top: 8px; }
.research-core__image { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: center 65%; opacity: .24; pointer-events: none; }
.research-core__modules { position: relative; display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; margin-top: 16px; }
.research-core__modules a { display: flex; align-items: center; gap: 6px; padding: 6px 10px; border: 1px solid hsl(var(--primary) / .13); border-radius: 6px; background: hsl(var(--card) / .85); color: hsl(var(--primary)); font-size: 12px; }
.research-core__modules a:hover { border-color: hsl(var(--primary) / .4); background: hsl(var(--card)); }
.research-core__modules svg { flex-shrink: 0; }
.dashboard-task-list { display: grid; gap: 6px; font-size: 12px; }
.dashboard-task-list li { padding: 4px 10px; background: hsl(var(--primary) / .035); border-radius: 7px; }
.dashboard-task-list a { display: flex; justify-content: space-between; gap: 12px; font-size: 14px; line-height: 1.4; }
.dashboard-task-list strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 500; }
.dashboard-task-list a > span { white-space: nowrap; font-size: 12px; }
.dashboard-task-list p { color: hsl(var(--muted-foreground)); margin-top: 4px; line-height: 1.4; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dashboard-bars { display: grid; gap: 6px; align-content: center; flex: 1; }
.dashboard-bar { display: grid; grid-template-columns: minmax(60px, 1.2fr) minmax(40px, 1.3fr) auto; gap: 10px; align-items: center; font-size: 14px; line-height: 1.4; }
.dashboard-bar > span { white-space: nowrap; text-overflow: ellipsis; overflow: hidden; }
.dashboard-bar i { height: 9px; background: #edf2f8; overflow: hidden; border-radius: 4px; }
.dashboard-bar b { display: block; height: 100%; border-radius: inherit; background: #4b9bf2; }
.dashboard-bar:nth-child(2) b { background: #4abac7; }
.dashboard-bar:nth-child(3) b { background: #65c8ac; }
.dashboard-bar:nth-child(4) b { background: #e9ba65; }
.dashboard-bar:nth-child(5) b { background: #ae94d9; }
.dashboard-bar strong { font-weight: 400; font-variant-numeric: tabular-nums; }
.dashboard-chart-tools { display: flex; justify-content: flex-end; flex-shrink: 0; height: 24px; }
.dashboard-chart-tools .el-button { min-height: 22px; height: 22px; font-size: 12px; }
.dashboard-panel .analytics-chart-frame { flex: 1 1 0; min-height: 100px; }
.dashboard-network .panel-section__body { position: relative; }
.dashboard-network__summary { display: flex; gap: 16px; align-items: center; font-size: 12px; flex-shrink: 0; color: hsl(var(--muted-foreground)); }
.dashboard-network__summary b { color: hsl(var(--primary)); font-size: 16px; font-weight: 600; margin-right: 3px; }
.dashboard-network__summary small { margin-left: auto; color: hsl(var(--muted-foreground)); }
.dashboard-ranking-heading { display: flex; justify-content: space-between; color: hsl(var(--muted-foreground)); font-size: 12px; padding: 5px 9px; background: hsl(var(--table-header-bg)); border-radius: 5px; }
.dashboard-ranking { font-size: 13px; }
.dashboard-ranking li { display: flex; align-items: center; gap: 9px; padding: 4px 3px; line-height: 1.4; border-bottom: 1px solid hsl(var(--border) / .6); }
.dashboard-ranking li:last-child { border-bottom: 0; }
.dashboard-ranking em { font-style: normal; color: #62758e; width: 18px; text-align: center; background: #edf3fa; border-radius: 4px; font-size: 12px; }
.dashboard-ranking li:first-child em { color: #9e6b16; background: #fff0cc; }
.dashboard-ranking li:nth-child(2) em { color: #62758e; background: #e8eef5; }
.dashboard-ranking li:nth-child(3) em { color: #a77852; background: #f7ede3; }
.dashboard-ranking a { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dashboard-ranking a span { color: hsl(var(--muted-foreground)); }
.dashboard-ranking strong { color: hsl(var(--foreground)); font-weight: 500; }
.dashboard-audits { font-size: 12px; }
.dashboard-audits li { display: flex; gap: 8px; padding: 6px 0; align-items: center; border-bottom: 1px solid hsl(var(--border) / .6); }
.dashboard-audits li:last-child { border-bottom: 0; }
.dashboard-audits li > span { padding: 1px 6px; border-radius: 5px; white-space: nowrap; }
.dashboard-audits .text-success { background: hsl(var(--success) / .08); }
.dashboard-audits .text-destructive { background: hsl(var(--destructive) / .07); }
.dashboard-audits a { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dashboard-audits time,.dashboard-read-time { color: hsl(var(--muted-foreground)); font-size: 11px; }
.dashboard-read-time { margin-top: 8px; }
.dashboard-data-list { font-size: 13px; display: grid; gap: 8px; }
@media (max-width: 1599px) { .dashboard-grid { gap: 12px; flex-basis: 680px; min-height: 680px; } .dashboard-panel .panel-section__title { font-size: 14px; } .dashboard-panel .panel-section__body { padding: 6px 10px 8px; } .dashboard-panel .panel-section__header { padding-inline: 10px; } .dashboard-metric__icon { width: 32px; height: 32px; } .dashboard-metric__icon svg { width: 21px; } .research-core__modules { gap: 5px; } .research-core__modules a { padding: 5px 7px; } .dashboard-network__summary { gap: 10px; } }
@media (max-width: 1279px) { .dashboard-update { display: none; } .dashboard-network__summary small { font-size: 10px; } .dashboard-bar { gap: 7px; } }
@media (max-width: 1099px) { .dashboard-grid { display: flex; flex-direction: column; flex: none; min-height: 0; } .dashboard-column--center { order: -1; grid-template-rows: 160px 340px 280px; } .dashboard-column--left,.dashboard-column--right { grid-template-columns: repeat(2, minmax(0,1fr)); grid-template-rows: repeat(2, 250px); } .dashboard-toolbar { flex-wrap: wrap; gap: 8px; } .dashboard-filters { margin-left: auto; } .dashboard-panel__link { min-height: 28px; min-width: 28px; } }
@media (max-width: 639px) { .dashboard-page { padding: 12px; } .dashboard-toolbar h1 { font-size: 18px; } .dashboard-filters { width: 100%; gap: 6px; } .dashboard-filters .el-select { width: 108px; } .dashboard-column--left,.dashboard-column--right { display: flex; flex-direction: column; } .dashboard-column--left > .dashboard-panel,.dashboard-column--right > .dashboard-panel { min-height: 220px; max-height: 300px; } .dashboard-panel .panel-section__body { min-height: 140px; } .dashboard-metric strong { font-size: 22px; } .research-core h2 { font-size: 19px; } .research-core__modules a { font-size: 11px; padding: 6px; } .dashboard-network__summary { gap: 8px; } .dashboard-network__summary small { font-size: 10px; } }
</style>
