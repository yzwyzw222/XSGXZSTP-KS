<template>
  <div ref="chartRef" class="citation-chart" />
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  data: { type: Array, default: () => [] }
})

const chartRef = ref(null)
let chart = null

function cssVar(name) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

function renderChart() {
  if (!chart || !props.data.length) return
  const muted = cssVar('--text-secondary')
  const border = cssVar('--border-color')
  const accent = cssVar('--accent')
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: props.data.map(d => d.label),
      axisLabel: { color: muted },
      axisLine: { lineStyle: { color: border } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: muted },
      axisLine: { lineStyle: { color: border } },
      splitLine: { lineStyle: { color: cssVar('--paper-deep') } }
    },
    series: [{
      data: props.data.map(d => d.value),
      type: 'bar',
      itemStyle: { color: accent, borderRadius: [4, 4, 0, 0] }
    }],
    grid: { left: 40, right: 16, top: 16, bottom: 30 }
  })
}

onMounted(() => {
  chart = echarts.init(chartRef.value, 'dark')
  renderChart()
  window.addEventListener('resize', () => chart?.resize())
})

watch(() => props.data, renderChart, { deep: true })

onBeforeUnmount(() => {
  chart?.dispose()
})
</script>

<style scoped>
.citation-chart {
  width: 100%;
  height: 300px;
}
</style>
