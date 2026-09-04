<script setup lang="ts">
import { BarChart, GraphChart, LineChart, PieChart, TreemapChart } from 'echarts/charts'
import {
  AriaComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from 'echarts/components'
import { init, use, type ECharts, type EChartsCoreOption } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { useChartTheme, type ChartPalette } from '@/composables/useChartTheme'
import { useTheme } from '@/composables/useTheme'
import { prefersReducedMotion } from '@/utils/motion'

const props = defineProps<{
  option: EChartsCoreOption
  label: string
  height?: string
}>()

use([
  BarChart,
  GraphChart,
  LineChart,
  PieChart,
  TreemapChart,
  AriaComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
])

const container = ref<HTMLDivElement | null>(null)
const { isDark } = useTheme()
const { palette } = useChartTheme()
let chart: ECharts | null = null
let resizeObserver: ResizeObserver | null = null

/** 把主题色板注入到调用方 option 的通用位置，避免每个视图重复配置深色样式。 */
function withTheme(option: EChartsCoreOption, p: ChartPalette): EChartsCoreOption {
  const merged: Record<string, unknown> = { ...option }
  if (!merged.color) merged.color = p.series
  if (!merged.textStyle) merged.textStyle = { color: p.text, fontFamily: 'var(--font-sans)' }
  const tooltip = (merged.tooltip ?? {}) as Record<string, unknown>
  merged.tooltip = {
    backgroundColor: p.tooltipBg,
    borderColor: p.tooltipBorder,
    textStyle: { color: p.tooltipFg, fontSize: 12 },
    ...tooltip,
  }
  return merged as EChartsCoreOption
}

function render(): void {
  if (!container.value) return
  chart ??= init(container.value, undefined, { renderer: 'canvas' })
  const base = withTheme(props.option, palette.value)
  const option = prefersReducedMotion()
    ? { ...base, animation: false, animationDuration: 0, animationDurationUpdate: 0 }
    : base
  chart.setOption(option, true)
}

function resize(): void {
  chart?.resize()
}

watch(() => props.option, () => void nextTick(render), { deep: true })
watch(isDark, () => void nextTick(render))

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
  <div
    class="analytics-chart-frame w-full"
    :style="{ height: props.height ?? '300px' }"
    role="img"
    :aria-label="label"
  >
    <div ref="container" class="analytics-chart size-full" aria-hidden="true" />
  </div>
</template>
