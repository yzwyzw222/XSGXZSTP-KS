<script setup lang="ts">
import { BarChart, GraphChart, LineChart, TreemapChart } from 'echarts/charts'
import {
  AriaComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from 'echarts/components'
import { init, use, type ECharts, type EChartsCoreOption } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { prefersReducedMotion } from '@/utils/motion'

const props = defineProps<{
  option: EChartsCoreOption
  label: string
}>()

use([
  BarChart,
  GraphChart,
  LineChart,
  TreemapChart,
  AriaComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
])

const container = ref<HTMLDivElement | null>(null)
let chart: ECharts | null = null
let resizeObserver: ResizeObserver | null = null

function render(): void {
  if (!container.value) return
  chart ??= init(container.value, 'dark', { renderer: 'canvas' })
  const option = prefersReducedMotion()
    ? { ...props.option, animation: false, animationDuration: 0, animationDurationUpdate: 0 }
    : props.option
  chart.setOption(option, true)
}

function resize(): void {
  chart?.resize()
}

watch(() => props.option, () => void nextTick(render), { deep: true })
onMounted(() => {
  render()
  if (typeof ResizeObserver === 'function' && container.value) {
    resizeObserver = new ResizeObserver(resize)
    resizeObserver.observe(container.value)
  } else {
    window.addEventListener('resize', resize)
  }
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  resizeObserver?.disconnect()
  resizeObserver = null
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="analytics-chart-frame" role="img" :aria-label="label">
    <div ref="container" class="analytics-chart" aria-hidden="true" />
  </div>
</template>
