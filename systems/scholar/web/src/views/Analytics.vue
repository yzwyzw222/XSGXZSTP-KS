<template>
  <div class="analytics">
    <h2 class="page-title">科研分析</h2>
    <ScholarSearch @select="onScopeChange" @clear="onScopeChange" />

    <el-tabs v-model="activeTab" class="analytics-tabs" @tab-change="onTabChange">
      <el-tab-pane label="发表趋势" name="trendBar">
        <p class="tab-desc">每年论文数量，用于观察科研节奏与高产 / 低谷周期。</p>
      </el-tab-pane>
      <el-tab-pane label="产出角色" name="role">
        <p class="tab-desc">堆叠柱 = 每年总论文（第一作者 / 非第一作者构成），折线 = 通讯作者论文数。</p>
      </el-tab-pane>
      <el-tab-pane label="研究方向" name="topics">
        <p class="tab-desc">论文数量最多的研究方向 Top 10，体现核心研究主线是否稳定。</p>
      </el-tab-pane>
      <el-tab-pane label="论文类型" name="types">
        <p class="tab-desc">成果类型构成（期刊 / 硕士论文 / 报纸等）。</p>
      </el-tab-pane>
      <el-tab-pane label="高被引 TOP" name="cited">
        <p class="tab-desc">高被引论文排名，反映哪些方向真正被学界认可。</p>
      </el-tab-pane>
      <el-tab-pane label="主题演化" name="evolution">
        <p class="tab-desc">各研究主题的论文数量随年份变化，反映研究方向的演进。</p>
      </el-tab-pane>
      <el-tab-pane label="关键词共现" name="keyword">
        <p class="tab-desc">节点 = 研究主题/关键词（大小 = 出现次数）；连线 = 两关键词在同一篇论文中同时出现（越粗共现越多）。点击节点可查看该关键词下的论文列表。</p>
      </el-tab-pane>
      <el-tab-pane label="合作作者" name="coauthor">
        <p class="tab-desc">节点 = 作者（大小 = 论文数）；连线 = 合作发表论文（越粗合作越多）。点击节点可查看该作者名下的论文列表。</p>
      </el-tab-pane>
      <el-tab-pane label="机构合作" name="institution">
        <p class="tab-desc">节点 = 机构（大小 = 参与论文数）；连线 = 两机构出现在同一篇论文署名中。点击节点可查看该机构参与的论文列表。</p>
      </el-tab-pane>
      <el-tab-pane label="论文相似" name="similarity">
        <p class="tab-desc">节点 = 论文（大小 = 主题数）；连线 = 两篇论文共享 ≥ 2 个研究主题（越粗越相似），聚在一起即同一方向。点击节点可查看该论文包含的主题。</p>
      </el-tab-pane>
    </el-tabs>

    <div class="chart-card">
      <div v-loading="loading" class="chart-wrap">
        <div ref="chartRef" class="chart"></div>
        <el-empty
          v-if="!loading && isEmpty"
          :description="emptyText"
          class="chart-empty"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import ScholarSearch from '../components/ScholarSearch.vue'
import {
  fetchPublicationTrend,
  fetchAuthorRoleTrend,
  fetchTopicDistribution,
  fetchPaperTypeDistribution,
  fetchCitationStats,
  fetchKeywordCooccurrence,
  fetchCoauthorNetwork,
  fetchInstitutionNetwork,
  fetchPaperSimilarity,
  fetchTopicEvolution,
} from '../api/analytics'

const authorId = ref(null)
const router = useRouter()

function onScopeChange(scholar) {
  authorId.value = scholar ? scholar.id : null
  loadTab(activeTab.value)
}

const TAB_TITLE = {
  trendBar: '发表趋势',
  role: '产出角色统计',
  topics: '研究方向分布',
  types: '论文类型分布',
  cited: '高被引论文排名',
  keyword: '关键词共现网络',
  coauthor: '合作作者网络',
  institution: '机构合作网络',
  similarity: '论文相似网络',
  evolution: '主题演化',
}

const CATEGORY_COLOR = {
  主题: '#a77af2',
  作者: '#f5a04b',
  机构: '#35c98c',
  论文: '#2d9df5',
}

const activeTab = ref('trendBar')
const chartRef = ref(null)
const loading = ref(false)
const isEmpty = ref(false)
const emptyText = ref('暂无数据')

let chart = null

const AXIS_COLOR = '#8296ae'
const SPLIT_LINE = '#1e3a5e'

/** 空态：清空画布避免上一个标签的旧图残留在空态提示下面 */
function showEmpty(text) {
  isEmpty.value = true
  emptyText.value = text
  chart && chart.clear()
}

// ---------- 报告类图表 ----------

function renderTrendBar(data) {
  if (data.length === 0) {
    showEmpty('暂无论文年份数据')
    return
  }

  chart.setOption(
    {
      backgroundColor: 'transparent',
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 24, top: 30, bottom: 30 },
      xAxis: {
        type: 'category',
        data: data.map((d) => d.year),
        axisLine: { lineStyle: { color: SPLIT_LINE } },
        axisLabel: { color: AXIS_COLOR },
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        axisLabel: { color: AXIS_COLOR },
        splitLine: { lineStyle: { color: SPLIT_LINE } },
      },
      series: [{
        type: 'line',
        data: data.map((d) => d.count),
        smooth: true,
        symbolSize: 8,
        itemStyle: { color: '#57bcff' },
        lineStyle: { width: 3, color: '#57bcff' },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: '#57bcff55' },
              { offset: 1, color: '#57bcff05' },
            ],
          },
        },
        label: { show: true, position: 'top', color: '#57bcff' },
      }],
    },
    true
  )
}

function renderRoleTrend(data) {
  if (data.length === 0) {
    showEmpty('暂无署名数据')
    return
  }

  chart.setOption(
    {
      backgroundColor: 'transparent',
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { top: 0, textStyle: { color: AXIS_COLOR } },
      grid: { left: 40, right: 24, top: 40, bottom: 30 },
      xAxis: {
        type: 'category',
        data: data.map((d) => d.year),
        axisLine: { lineStyle: { color: SPLIT_LINE } },
        axisLabel: { color: AXIS_COLOR },
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        axisLabel: { color: AXIS_COLOR },
        splitLine: { lineStyle: { color: SPLIT_LINE } },
      },
      series: [
        {
          name: '第一作者',
          type: 'bar',
          stack: 'total',
          data: data.map((d) => Math.min(d.firstAuthor, d.total)),
          barMaxWidth: 46,
          itemStyle: { color: '#2d9df5' },
        },
        {
          name: '非第一作者',
          type: 'bar',
          stack: 'total',
          data: data.map((d) => Math.max(d.total - d.firstAuthor, 0)),
          barMaxWidth: 46,
          itemStyle: { color: '#1e3a5e' },
          label: { show: true, position: 'top', color: '#57bcff', formatter: (p) => data[p.dataIndex].total },
        },
        {
          name: '通讯作者',
          type: 'line',
          data: data.map((d) => d.corresponding),
          symbolSize: 8,
          lineStyle: { width: 2.5, color: '#35c98c' },
          itemStyle: { color: '#35c98c' },
        },
      ],
    },
    true
  )
}

function renderTopicDist(data) {
  if (data.length === 0) {
    showEmpty('暂无研究主题数据')
    return
  }

  const sorted = [...data].reverse()
  chart.setOption(
    {
      backgroundColor: 'transparent',
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}: {c} 篇' },
      grid: { left: 130, right: 40, top: 10, bottom: 30 },
      xAxis: {
        type: 'value',
        minInterval: 1,
        axisLabel: { color: AXIS_COLOR },
        splitLine: { lineStyle: { color: SPLIT_LINE } },
      },
      yAxis: {
        type: 'category',
        data: sorted.map((d) => d.name),
        axisLabel: { color: AXIS_COLOR, width: 115, overflow: 'truncate' },
        axisLine: { lineStyle: { color: SPLIT_LINE } },
      },
      series: [{
        type: 'bar',
        data: sorted.map((d) => d.count),
        barMaxWidth: 16,
        itemStyle: {
          borderRadius: [0, 4, 4, 0],
          color: '#a77af2',
        },
        label: { show: true, position: 'right', color: '#c9a6ff' },
      }],
    },
    true
  )
}

function renderTypeDist(data) {
  if (data.length === 0) {
    showEmpty('暂无论文类型数据')
    return
  }

  const palette = ['#2d9df5', '#35c98c', '#f5a04b', '#a77af2', '#ff6670', '#57bcff']
  chart.setOption(
    {
      backgroundColor: 'transparent',
      tooltip: { trigger: 'item', formatter: '{b}: {c} 篇 ({d}%)' },
      legend: { bottom: 0, textStyle: { color: AXIS_COLOR }, type: 'scroll' },
      color: palette,
      series: [{
        type: 'pie',
        radius: ['40%', '66%'],
        center: ['50%', '44%'],
        data: data.map((d) => ({ name: d.name, value: d.count })),
        label: { color: AXIS_COLOR, formatter: '{b} {d}%' },
        labelLine: { lineStyle: { color: SPLIT_LINE } },
      }],
    },
    true
  )
}

function renderCited(stats) {
  if (!stats || stats.totalCitations <= 0 || stats.topCited.length === 0) {
    showEmpty('引用数据未导入（paper_reference 表为空）。导入参考文献数据后，此处将展示高被引 TOP 排名。')
    return
  }
  isEmpty.value = false
  const sorted = [...stats.topCited].reverse()
  chart.setOption(
    {
      backgroundColor: 'transparent',
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}: 被引 {c} 次' },
      grid: { left: 300, right: 50, top: 10, bottom: 30 },
      xAxis: {
        type: 'value',
        minInterval: 1,
        axisLabel: { color: AXIS_COLOR },
        splitLine: { lineStyle: { color: SPLIT_LINE } },
      },
      yAxis: {
        type: 'category',
        data: sorted.map((d) => d.name),
        axisLabel: { color: AXIS_COLOR, width: 285, overflow: 'truncate' },
        axisLine: { lineStyle: { color: SPLIT_LINE } },
      },
      series: [{
        type: 'bar',
        data: sorted.map((d) => d.count),
        barMaxWidth: 16,
        itemStyle: {
          borderRadius: [0, 4, 4, 0],
          color: '#f5a04b',
        },
        label: { show: true, position: 'right', color: '#f5a04b' },
      }],
    },
    true
  )
}

// ---------- 网络与演化图表 ----------

function renderNetwork(data, categoryLabel) {
  if (data.nodes.length === 0) {
    showEmpty('该网络暂无满足条件的关联数据')
    return
  }

  const color = CATEGORY_COLOR[categoryLabel] || '#2d9df5'
  const maxValue = Math.max(...data.nodes.map((n) => n.value), 1)
  const maxWeight = Math.max(...data.links.map((l) => l.weight), 1)

  chart.setOption(
    {
      backgroundColor: 'transparent',
      tooltip: {
        formatter: (p) =>
          p.dataType === 'edge'
            ? `${p.data.source} — ${p.data.target}<br/>强度: ${p.data.weight}`
            : `${p.data.name}<br/>数量: ${p.data.value}`,
      },
      series: [
        {
          type: 'graph',
          layout: 'force',
          roam: true,
          draggable: true,
          data: data.nodes.map((n) => ({
            id: n.id,
            name: n.name,
            value: n.value,
            symbolSize: 16 + (n.value / maxValue) * 46,
            itemStyle: {
              color,
              borderColor: '#ffffff44',
              borderWidth: 1,
            },
            label: { show: true, fontSize: 11, color: '#edf6ff' },
          })),
          links: data.links.map((l) => ({
            source: l.source,
            target: l.target,
            weight: l.weight,
            lineStyle: {
              width: 1 + (l.weight / maxWeight) * 4,
              color: '#4a5b7599',
              curveness: 0.15,
            },
          })),
          force: {
            repulsion: 320,
            gravity: 0.12,
            edgeLength: [70, 160],
            layoutAnimation: true,
          },
          emphasis: {
            focus: 'adjacency',
            lineStyle: { color: '#57bcff' },
          },
          label: { position: 'right', distance: 4 },
        },
      ],
    },
    true
  )

  // 网络图节点点击：关键词共现 → 关键词检索；合作作者 → 作者检索；机构合作 → 机构检索
  chart.off('click')
  chart.on('click', (params) => {
    if (params.dataType !== 'node' || !params.data?.id) return
    if (activeTab.value === 'keyword') {
      router.push({
        path: '/keyword-search',
        query: { topicId: params.data.id, name: params.data.name },
      })
    } else if (activeTab.value === 'coauthor') {
      router.push({
        path: '/author-search',
        query: { authorId: params.data.id, name: params.data.name },
      })
    } else if (activeTab.value === 'institution') {
      // 机构合作图的节点 id 就是机构名
      router.push({
        path: '/institution-search',
        query: { name: params.data.name },
      })
    } else if (activeTab.value === 'similarity') {
      // 论文相似图的节点 id 就是论文 id
      router.push({
        path: '/paper-search',
        query: { paperId: params.data.id, name: params.data.name },
      })
    }
  })
}

function renderEvolution(data) {
  if (data.years.length === 0) {
    showEmpty('暂无带年份的主题数据')
    return
  }
  isEmpty.value = false

  const palette = ['#2d9df5', '#35c98c', '#f5a04b', '#a77af2', '#ff6670', '#57bcff', '#e06c9f', '#7bb661']
  chart.setOption(
    {
      backgroundColor: 'transparent',
      color: palette,
      tooltip: { trigger: 'axis' },
      legend: {
        top: 0,
        type: 'scroll',
        textStyle: { color: AXIS_COLOR },
      },
      grid: { left: 40, right: 24, top: 44, bottom: 30 },
      xAxis: {
        type: 'category',
        data: data.years,
        axisLine: { lineStyle: { color: SPLIT_LINE } },
        axisLabel: { color: AXIS_COLOR },
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        axisLabel: { color: AXIS_COLOR },
        splitLine: { lineStyle: { color: SPLIT_LINE } },
      },
      series: data.topics.map((t) => ({
        name: t.name,
        type: 'line',
        data: t.counts,
        smooth: true,
        symbolSize: 8,
        lineStyle: { width: 2.5 },
        emphasis: { focus: 'series' },
      })),
    },
    true
  )
}

async function loadTab(tab) {
  if (!chart) return
  loading.value = true
  isEmpty.value = false // 复位空态：避免上一个标签的空态提示叠在有数据的图表上
  try {
    switch (tab) {
      case 'trendBar': {
        const data = await fetchPublicationTrend(authorId.value)
        renderTrendBar(data)
        break
      }
      case 'role': {
        const data = await fetchAuthorRoleTrend(authorId.value)
        renderRoleTrend(data)
        break
      }
      case 'topics': {
        const data = await fetchTopicDistribution(10, authorId.value)
        renderTopicDist(data)
        break
      }
      case 'types': {
        const data = await fetchPaperTypeDistribution(authorId.value)
        renderTypeDist(data)
        break
      }
      case 'cited': {
        const data = await fetchCitationStats(10, authorId.value)
        renderCited(data)
        break
      }
      case 'keyword': {
        const data = await fetchKeywordCooccurrence(30, 2, authorId.value)
        renderNetwork(data, '主题')
        break
      }
      case 'coauthor': {
        const data = await fetchCoauthorNetwork(30, 1, authorId.value)
        renderNetwork(data, '作者')
        break
      }
      case 'institution': {
        const data = await fetchInstitutionNetwork(15, 1, authorId.value)
        renderNetwork(data, '机构')
        break
      }
      case 'similarity': {
        const data = await fetchPaperSimilarity(30, 2, authorId.value)
        renderNetwork(data, '论文')
        break
      }
      case 'evolution': {
        const data = await fetchTopicEvolution(8, authorId.value)
        renderEvolution(data)
        break
      }
    }
  } catch (e) {
    ElMessage.error(`${TAB_TITLE[tab]}加载失败：${e.message || '未知错误'}`)
  } finally {
    loading.value = false
  }
}

function onTabChange(tab) {
  loadTab(tab)
}

function handleResize() {
  chart && chart.resize()
}

onMounted(() => {
  chart = echarts.init(chartRef.value)
  window.addEventListener('resize', handleResize)
  loadTab(activeTab.value)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>

<style scoped>
.analytics {
  max-width: 1400px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--ink);
  margin-bottom: 12px;
}

.analytics-tabs :deep(.el-tabs__item) {
  color: var(--muted);
}

.analytics-tabs :deep(.el-tabs__item.is-active) {
  color: var(--accent);
}

.analytics-tabs :deep(.el-tabs__active-bar) {
  background-color: var(--accent);
}

.tab-desc {
  font-size: 13px;
  color: var(--muted);
  margin-bottom: 12px;
}

.chart-card {
  background-color: var(--paper);
  border: 1px solid #1e3a5e;
  border-radius: 8px;
  padding: 8px;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.chart-wrap {
  position: relative;
  flex: 1;
  min-height: 0;
}

.chart {
  width: 100%;
  height: 100%;
  min-height: 420px;
}

.chart-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
