<template>
  <!--
    多维分析页（AnalyticsView）：ECharts 四维度图表
    ─────────────────────────────────────────────────
    四个分析维度对应大创项目的四大研究方向：
      1. 合作网络：作者合作关系统计（力导向图 or 和弦图）
      2. 引用分析：论文施引/被引排行（双柱状图）
      3. 主题演化：关键词随年份出现频次（折线图）
      4. 机构影响力：机构论文数 + 引用热度（堆叠柱状图）
    技术方案：
      - 每个图表独立 ECharts 实例，挂载到对应 ref DOM 节点
      - 统一暗色主题配色（与 style.css 设计令牌一致）
      - 窗口 resize 时调用 chart.resize() 自适应
      - 页面卸载时 dispose() 释放资源
  -->
  <div class="analytics-view">
    <div class="toolbar">
      <el-select v-model="limit" style="width: 140px" @change="loadAll">
        <el-option :value="10" label="TOP 10" />
        <el-option :value="20" label="TOP 20" />
        <el-option :value="50" label="TOP 50" />
      </el-select>
      <el-button type="primary" @click="loadAll">刷新数据</el-button>
    </div>

    <div class="chart-grid">
      <!-- 合作网络 -->
      <div class="chart-card" v-loading="loading.collab">
        <h3>合作网络</h3>
        <p class="chart-desc">作者间共同署名论文数，边越粗合作越紧密</p>
        <div ref="collabChart" class="chart-body" />
      </div>

      <!-- 引用分析 -->
      <div class="chart-card" v-loading="loading.citation">
        <h3>引用分析</h3>
        <p class="chart-desc">论文施引数（引用别人）与被引数（被别人引用）对比</p>
        <div ref="citationChart" class="chart-body" />
      </div>

      <!-- 主题演化 -->
      <div class="chart-card" v-loading="loading.topic">
        <h3>主题演化</h3>
        <p class="chart-desc">各关键词在不同年份的论文出现频次趋势</p>
        <div ref="topicChart" class="chart-body" />
      </div>

      <!-- 机构影响力 -->
      <div class="chart-card" v-loading="loading.inst">
        <h3>机构影响力</h3>
        <p class="chart-desc">各机构的论文产出数与总被引热度</p>
        <div ref="instChart" class="chart-body" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 多维分析脚本：ECharts 四图表
 * ─────────────────────────────────
 * 每个图表的渲染逻辑：
 *   1. 调用 analyticsApi 获取数据
 *   2. 将数据转换为 ECharts option 格式
 *   3. setOption() 渲染（notMerge: true 避免旧数据残留）
 * 配色方案：使用 style.css 中的设计令牌（--accent/--green/--amber/--violet 等）
 */
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { analyticsApi, type CollaborationItem, type CitationItem, type InstitutionImpactItem, type TopicEvolutionItem } from '../api'

// ====================================================================
// 状态
// ====================================================================
const limit = ref(20)
const loading = reactive({ collab: false, citation: false, topic: false, inst: false })

const collabChart = ref<HTMLElement | null>(null)
const citationChart = ref<HTMLElement | null>(null)
const topicChart = ref<HTMLElement | null>(null)
const instChart = ref<HTMLElement | null>(null)

let chartInstances: echarts.ECharts[] = []

// 配色常量（与 style.css 设计令牌一致）
const COLORS = {
  accent: '#2d9df5',
  green: '#35c98c',
  amber: '#f5a04b',
  violet: '#a77af2',
  danger: '#ff6670',
  ink: '#edf6ff',
  muted: '#8296ae',
  border: '#1d3657',
  paper: '#10233d'
}

// ====================================================================
// 数据加载 + 图表渲染
// ====================================================================

/** 并行加载四个维度数据，各自独立 loading 状态 */
async function loadAll() {
  loadCollab()
  loadCitation()
  loadTopic()
  loadInst()
}

/** 合作网络：将边列表转为 ECharts graph 图 */
async function loadCollab() {
  loading.collab = true
  try {
    const data = await analyticsApi.collaborations(limit.value)
    renderCollab(data)
  } catch {
    ElMessage.error('加载合作网络失败')
  } finally {
    loading.collab = false
  }
}

function renderCollab(data: CollaborationItem[]) {
  const chart = getChart(0, collabChart.value)
  if (!chart) return

  // 从边列表中提取唯一节点（作者）
  const nodeSet = new Map<string, { name: string; symbolSize: number }>()
  for (const item of data) {
    if (!nodeSet.has(item.author1)) nodeSet.set(item.author1, { name: item.author1, symbolSize: 20 })
    if (!nodeSet.has(item.author2)) nodeSet.set(item.author2, { name: item.author2, symbolSize: 20 })
    // 合作次数越多节点越大
    nodeSet.get(item.author1)!.symbolSize += item.paperCount * 3
    nodeSet.get(item.author2)!.symbolSize += item.paperCount * 3
  }

  const nodes = Array.from(nodeSet.values()).map(n => ({
    ...n,
    itemStyle: { color: COLORS.accent },
    label: { show: true, color: COLORS.ink, fontSize: 10 }
  }))

  const edges = data.map(d => ({
    source: d.author1,
    target: d.author2,
    lineStyle: { width: Math.max(1, d.paperCount * 2), color: '#3a5070' },
    label: { show: false }
  }))

  chart.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'graph',
      layout: 'force',
      data: nodes,
      links: edges,
      roam: true,
      force: { repulsion: 200, edgeLength: [80, 200] },
      emphasis: { focus: 'adjacency' }
    }]
  }, true)
}

/** 引用分析：双柱状图（施引 vs 被引） */
async function loadCitation() {
  loading.citation = true
  try {
    const data = await analyticsApi.citations(limit.value)
    renderCitation(data)
  } catch {
    ElMessage.error('加载引用分析失败')
  } finally {
    loading.citation = false
  }
}

function renderCitation(data: CitationItem[]) {
  const chart = getChart(1, citationChart.value)
  if (!chart) return

  const titles = data.map(d => d.title.length > 20 ? d.title.slice(0, 20) + '…' : d.title)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['施引数', '被引数'], textStyle: { color: COLORS.muted } },
    grid: { left: 60, right: 20, bottom: 60, top: 40 },
    xAxis: {
      type: 'category',
      data: titles,
      axisLabel: { color: COLORS.muted, rotate: 30, fontSize: 10 },
      axisLine: { lineStyle: { color: COLORS.border } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: COLORS.muted },
      splitLine: { lineStyle: { color: COLORS.border } }
    },
    series: [
      { name: '施引数', type: 'bar', data: data.map(d => d.citesOut), itemStyle: { color: COLORS.amber } },
      { name: '被引数', type: 'bar', data: data.map(d => d.citesIn), itemStyle: { color: COLORS.green } }
    ]
  }, true)
}

/** 主题演化：多折线图（每个关键词一条线） */
async function loadTopic() {
  loading.topic = true
  try {
    const data = await analyticsApi.topicEvolution(limit.value)
    renderTopic(data)
  } catch {
    ElMessage.error('加载主题演化失败')
  } finally {
    loading.topic = false
  }
}

function renderTopic(data: TopicEvolutionItem[]) {
  const chart = getChart(2, topicChart.value)
  if (!chart) return

  // 按关键词分组
  const kwMap = new Map<string, Map<number, number>>()
  const years = new Set<number>()
  for (const item of data) {
    years.add(item.year)
    if (!kwMap.has(item.keyword)) kwMap.set(item.keyword, new Map())
    kwMap.get(item.keyword)!.set(item.year, item.paperCount)
  }

  const sortedYears = Array.from(years).sort((a, b) => a - b)
  const colors = [COLORS.accent, COLORS.green, COLORS.amber, COLORS.violet, COLORS.danger]

  const series = Array.from(kwMap.entries()).map(([kw, yearMap], idx) => ({
    name: kw,
    type: 'line' as const,
    data: sortedYears.map(y => yearMap.get(y) ?? 0),
    lineStyle: { color: colors[idx % colors.length] },
    itemStyle: { color: colors[idx % colors.length] }
  }))

  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { textStyle: { color: COLORS.muted } },
    grid: { left: 50, right: 20, bottom: 40, top: 40 },
    xAxis: {
      type: 'category',
      data: sortedYears.map(String),
      axisLabel: { color: COLORS.muted },
      axisLine: { lineStyle: { color: COLORS.border } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: COLORS.muted },
      splitLine: { lineStyle: { color: COLORS.border } }
    },
    series
  }, true)
}

/** 机构影响力：堆叠柱状图（论文数 + 引用热度） */
async function loadInst() {
  loading.inst = true
  try {
    const data = await analyticsApi.institutionImpact(limit.value)
    renderInst(data)
  } catch {
    ElMessage.error('加载机构影响力失败')
  } finally {
    loading.inst = false
  }
}

function renderInst(data: InstitutionImpactItem[]) {
  const chart = getChart(3, instChart.value)
  if (!chart) return

  const names = data.map(d => d.institution)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['论文数', '总被引'], textStyle: { color: COLORS.muted } },
    grid: { left: 60, right: 20, bottom: 60, top: 40 },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: { color: COLORS.muted, rotate: 30, fontSize: 10 },
      axisLine: { lineStyle: { color: COLORS.border } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: COLORS.muted },
      splitLine: { lineStyle: { color: COLORS.border } }
    },
    series: [
      { name: '论文数', type: 'bar', stack: 'total', data: data.map(d => d.paperCount), itemStyle: { color: COLORS.violet } },
      { name: '总被引', type: 'bar', stack: 'total', data: data.map(d => d.totalCitations), itemStyle: { color: COLORS.accent } }
    ]
  }, true)
}

// ====================================================================
// 工具函数
// ====================================================================

/** 获取或创建 ECharts 实例（避免重复初始化） */
function getChart(index: number, el: HTMLElement | null): echarts.ECharts | null {
  if (!el) return null
  if (!chartInstances[index]) {
    chartInstances[index] = echarts.init(el, undefined, { renderer: 'canvas' })
  }
  return chartInstances[index]
}

/** 窗口 resize 时所有图表自适应 */
function handleResize() {
  chartInstances.forEach(c => c?.resize())
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  loadAll()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chartInstances.forEach(c => c?.dispose())
  chartInstances = []
})
</script>

<style scoped>
.analytics-view {
  padding: var(--space-4);
}

.toolbar {
  display: flex;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

/* 图表网格：2x2 布局，响应式降为单列 */
.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-4);
}

@media (max-width: 900px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
}

/* 单个图表卡片 */
.chart-card {
  background: var(--paper);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-4);
}

.chart-card h3 {
  margin: 0 0 var(--space-1) 0;
  font-size: 16px;
}

.chart-desc {
  color: var(--muted);
  font-size: 12px;
  margin: 0 0 var(--space-3) 0;
}

/* ECharts 挂载容器：固定高度 */
.chart-body {
  width: 100%;
  height: 320px;
}
</style>
