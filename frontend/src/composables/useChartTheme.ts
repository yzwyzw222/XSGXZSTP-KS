import { computed, watch, type ComputedRef, type Ref } from 'vue'

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

/** 把 "210 40% 96%" 这类 HSL 分量转换为可用的 hsl() 字符串。 */
function readHsl(name: string, fallback: string, alpha?: number): string {
  if (typeof window === 'undefined') return fallback
  const raw = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  if (!raw) return fallback
  // 已经是完整颜色值（如 #hex 或 hsl(...)）
  if (raw.startsWith('#') || raw.startsWith('hsl') || raw.startsWith('rgb')) return raw
  return alpha === undefined ? `hsl(${raw})` : `hsl(${raw} / ${alpha})`
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
        readHsl('--chart-1', '#38a8ff'),
        readHsl('--chart-2', '#35c98c'),
        readHsl('--chart-3', '#f5a04b'),
        readHsl('--chart-4', '#a77af2'),
        readHsl('--chart-5', '#27b9d5'),
        readHsl('--chart-6', '#ff6670'),
      ],
      grid: readHsl('--chart-grid', '#213a57'),
      axis: readHsl('--chart-axis', '#8296ae'),
      tooltipBg: readHsl('--chart-tooltip-bg', '#0a1729'),
      tooltipBorder: readHsl('--chart-tooltip-border', '#2b4668'),
      tooltipFg: readHsl('--chart-tooltip-fg', '#edf6ff'),
      text: readHsl('--foreground', '#edf6ff'),
      textMuted: readHsl('--muted-foreground', '#8296ae'),
      cardBg: readHsl('--card', '#10233d'),
    }
  })

  return { isDark, palette }
}

/** 主题切换时执行回调（用于图表重渲染）。 */
export function onThemeChange(callback: () => void): void {
  const { isDark } = useTheme()
  watch(isDark, () => {
    // 等待 CSS 变量应用完成
    requestAnimationFrame(() => requestAnimationFrame(callback))
  })
}
