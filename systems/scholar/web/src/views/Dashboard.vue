<template>
  <div class="dashboard">
    <h2 class="page-title">总览</h2>

    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card" v-for="c in cards" :key="c.label">
        <span class="stat-num">{{ c.value }}</span>
        <span class="stat-label">{{ c.label }}</span>
      </div>
    </div>

    <!-- 图表区 -->
    <div class="chart-grid">
      <div class="chart-card wide">
        <h3>论文发表时间趋势</h3>
        <div ref="trendRef" class="chart"></div>
      </div>
      <div class="chart-card">
        <h3>论文类型分布</h3>
        <div ref="typeRef" class="chart"></div>
      </div>
      <div class="chart-card wide">
        <h3>期刊分布 Top 15</h3>
        <div ref="venueRef" class="chart"></div>
      </div>
      <div class="chart-card">
        <h3>被引分析</h3>
        <div class="citation-panel">
          <template v-if="citation && citation.totalCitations > 0">
            <div class="cite-row">
              <span>总被引</span><b>{{ citation.totalCitations }}</b>
            </div>
            <div class="cite-row">
              <span>篇均被引</span><b>{{ citation.avgPerPaper }}</b>
            </div>
            <div class="cite-row">
              <span>被引论文数</span><b>{{ citation.citedPapers }}</b>
            </div>
          </template>
          <el-empty v-else description="暂无引用数据（导入参考文献后自动生效）" :image-size="60" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { fetchGraphStats } from '../api/graph'
import {
  fetchPublicationTrend,
  fetchVenueDistribution,
  fetchPaperTypeDistribution,
  fetchCitationStats,
} from '../api/analytics'

const trendRef = ref(null)
const typeRef = ref(null)
const venueRef = ref(null)

const totals = ref({ totalPapers: 0, totalAuthors: 0, totalInstitutions: 0, totalVenues: 0, totalTopics: 0 })
const citation = ref(null)
let charts = []

const cards = computed(() => [
  { label: '论文', value: totals.value.totalPapers },
  { label: '作者', value: totals.value.totalAuthors },
  { label: '机构', value: totals.value.totalInstitutions },
  { label: '期刊', value: totals.value.totalVenues },
  { label: '主题', value: totals.value.totalTopics },
])

const AXIS_COLOR = '#8296ae'
const SPLIT_LINE = '#1e3a5e'

function baseOption() {
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    textStyle: { color: '#edf6ff' },
  }
}

function renderTrend(data) {
  const chart = echarts.init(trendRef.value)
  charts.push(chart)
  chart.setOption({
    ...baseOption(),
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
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
      symbolSize: 9,
      itemStyle: { color: '#2d9df5' },
      lineStyle: { width: 3 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#2d9df566' },
          { offset: 1, color: '#2d9df505' },
        ]),
      },
    }],
  })
}

function renderType(data) {
  const chart = echarts.init(typeRef.value)
  charts.push(chart)
  const palette = ['#2d9df5', '#35c98c', '#f5a04b', '#a77af2', '#ff6670', '#57bcff']
  chart.setOption({
    ...baseOption(),
    tooltip: { trigger: 'item', formatter: '{b}: {c} 篇 ({d}%)' },
    legend: { bottom: 0, textStyle: { color: AXIS_COLOR }, type: 'scroll' },
    color: palette,
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '45%'],
      data: data.map((d) => ({ name: d.name, value: d.count })),
      label: { color: AXIS_COLOR, formatter: '{b} {c}' },
      labelLine: { lineStyle: { color: SPLIT_LINE } },
    }],
  })
}

function renderVenue(data) {
  const chart = echarts.init(venueRef.value)
  charts.push(chart)
  const sorted = [...data].reverse()
  chart.setOption({
    ...baseOption(),
    grid: { left: 160, right: 40, top: 10, bottom: 30 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}: {c} 篇' },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: AXIS_COLOR },
      splitLine: { lineStyle: { color: SPLIT_LINE } },
    },
    yAxis: {
      type: 'category',
      data: sorted.map((d) => d.name),
      axisLabel: { color: AXIS_COLOR, width: 145, overflow: 'truncate' },
      axisLine: { lineStyle: { color: SPLIT_LINE } },
    },
    series: [{
      type: 'bar',
      data: sorted.map((d) => d.count),
      barMaxWidth: 18,
      itemStyle: {
        borderRadius: [0, 4, 4, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#1579ca' },
          { offset: 1, color: '#2d9df5' },
        ]),
      },
      label: { show: true, position: 'right', color: '#57bcff' },
    }],
  })
}

function handleResize() {
  charts.forEach((c) => c.resize())
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  const jobs = [
    fetchGraphStats().then((r) => (totals.value = r)).catch(() => {}),
    fetchPublicationTrend().then(renderTrend).catch((e) => ElMessage.error('趋势加载失败')),
    fetchPaperTypeDistribution().then(renderType).catch(() => {}),
    fetchVenueDistribution(15).then(renderVenue).catch(() => {}),
    fetchCitationStats(10).then((r) => (citation.value = r)).catch(() => {}),
  ]
  await Promise.allSettled(jobs)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  charts.forEach((c) => c.dispose())
  charts = []
})
</script>

<style scoped>
.dashboard {
  max-width: 1400px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--ink);
  margin-bottom: 20px;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}

.stat-card {
  background-color: var(--paper);
  border: 1px solid #1e3a5e;
  border-radius: 8px;
  padding: 18px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-num {
  font-size: 30px;
  font-weight: 700;
  color: var(--accent);
}

.stat-label {
  font-size: 13px;
  color: var(--muted);
}

.chart-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 14px;
}

.chart-card {
  background-color: var(--paper);
  border: 1px solid #1e3a5e;
  border-radius: 8px;
  padding: 16px;
}

.chart-card h3 {
  font-size: 15px;
  color: var(--ink);
  margin-bottom: 8px;
}

.chart {
  width: 100%;
  height: 300px;
}

.citation-panel {
  height: 300px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
  padding: 0 12px;
}

.cite-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background-color: var(--paper-deep);
  border: 1px solid #1e3a5e;
  border-radius: 6px;
  color: var(--muted);
  font-size: 13px;
}

.cite-row b {
  color: var(--accent);
  font-size: 18px;
}
</style>
