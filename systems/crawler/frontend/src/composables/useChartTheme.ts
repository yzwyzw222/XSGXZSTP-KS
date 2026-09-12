import { computed, type ComputedRef, type Ref } from 'vue'

import { useTheme } from '@/composables/useTheme'

export interface ChartPalette {
  series: string[]
  grid: string
  axis: string
  tooltipBg: string
  tooltipBorder: string
  tooltipFg: string
  text: string
  textMuted: string
  cardBg: string
}

/** 使用逗号格式兼容图表引擎的颜色解析器，避免 CSS Color 4 空格格式回退为黑色。 */
function readHsl(name: string, fallback: string, alpha?: number): string {
  if (typeof window === 'undefined') return fallback
  const raw = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  if (!raw) return fallback
  // 已经是完整颜色值（如 #hex 或 hsl(...)）
  if (raw.startsWith('#') || raw.startsWith('hsl') || raw.startsWith('rgb')) return raw
  const components = raw.split(/\s+/).join(', ')
  return alpha === undefined ? `hsl(${components})` : `hsla(${components}, ${alpha})`
}

/** 订阅主题变化，实时解析 CSS token 为图表色板。 */
export function useChartTheme(): {
  isDark: ComputedRef<boolean> | Ref<boolean>
  palette: ComputedRef<ChartPalette>
} {
  const { isDark } = useTheme()

  const palette = computed<ChartPalette>(() => {
    // 触发依赖收集：isDark 变化时重新读取 CSS 变量
    void isDark.value
    return {
      series: [
        readHsl('--chart-1', '#1677ef'),
        readHsl('--chart-2', '#16856d'),
        readHsl('--chart-3', '#bd7b32'),
        readHsl('--chart-4', '#9067b5'),
        readHsl('--chart-5', '#287f9a'),
        readHsl('--chart-6', '#c45567'),
      ],
      grid: readHsl('--chart-grid', '#e2eaf4'),
      axis: readHsl('--chart-axis', '#62758e'),
      tooltipBg: readHsl('--chart-tooltip-bg', '#ffffff'),
      tooltipBorder: readHsl('--chart-tooltip-border', '#d7e3f1'),
      tooltipFg: readHsl('--chart-tooltip-fg', '#203854'),
      text: readHsl('--foreground', '#203854'),
      textMuted: readHsl('--muted-foreground', '#62758e'),
      cardBg: readHsl('--card', '#ffffff'),
    }
  })

  return { isDark, palette }
}
