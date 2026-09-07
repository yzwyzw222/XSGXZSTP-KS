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
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { useChartTheme, type ChartPalette } from '@/composables/useChartTheme'
import { useTheme } from '@/composables/useTheme'
import { useMotion } from '@/composables/useMotion'

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
const { reducedMotion, duration } = useMotion()
let chart: ECharts | null = null
let resizeObserver: ResizeObserver | null = null
let resizeFrame: number | undefined
let mounted = false

/** 把主题色板注入到调用方 option 的通用位置，避免每个视图重复配置深色样式。 */
function withTheme(option: EChartsCoreOption, p: ChartPalette): EChartsCoreOption {
  const merged: Record<string, unknown> = { ...option }
  if (!merged.color) merged.color = p.series
  const fontFamily = getComputedStyle(document.documentElement).getPropertyValue('--font-sans').trim()
  merged.textStyle = { color: p.text, fontFamily, ...(merged.textStyle as object ?? {}) }
  const tooltip = (merged.tooltip ?? {}) as Record<string, unknown>
  merged.tooltip = {
    backgroundColor: p.tooltipBg,
    borderColor: p.tooltipBorder,
    textStyle: { color: p.tooltipFg, fontSize: 12 },
    confine: true,
    ...tooltip,
  }
  return merged as EChartsCoreOption
}

function render(): void {
  if (!mounted || !container.value || !container.value.clientWidth || !container.value.clientHeight) return
  chart ??= init(container.value, undefined, { renderer: 'canvas' })
  const base = withTheme(props.option, palette.value)
  const option: EChartsCoreOption = {
    ...base,
    animation: !reducedMotion.value && base.animation !== false,
    animationDuration: duration('chart'),
    animationDurationUpdate: duration('chart'),
    animationEasing: 'cubicOut',
    animationEasingUpdate: 'cubicOut',
  }
  // 合并保留同一系列的图形对象；移除的系列必须同时清除，避免筛选后残留旧数据。
  chart.setOption(option, { notMerge: false, replaceMerge: ['series'], lazyUpdate: false })
}

function resize(): void {
  if (resizeFrame !== undefined) cancelAnimationFrame(resizeFrame)
  resizeFrame = requestAnimationFrame(() => {
    resizeFrame = undefined
    if (!mounted || !container.value?.clientWidth || !container.value?.clientHeight) return
    chart?.resize({ animation: { duration: 0 } })
    render()
  })
}

watch([() => props.option, isDark, reducedMotion], render, { deep: true, flush: 'post' })

onMounted(() => {
  mounted = true
  render()
  if (typeof ResizeObserver === 'function' && container.value) {
    resizeObserver = new ResizeObserver(resize)
    resizeObserver.observe(container.value)
  } else {
    window.addEventListener('resize', resize)
  }
})

onBeforeUnmount(() => {
  mounted = false
  if (resizeFrame !== undefined) cancelAnimationFrame(resizeFrame)
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
