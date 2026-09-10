import { useMediaQuery } from '@vueuse/core'

const fallbackDurations = { fast: 120, normal: 200, slow: 280, chart: 320 } as const

/** CSS 与画布动效共享时长；VueUse 在作用域销毁时释放系统偏好监听。 */
export function useMotion() {
  const reducedMotion = useMediaQuery('(prefers-reduced-motion: reduce)')
  function duration(kind: keyof typeof fallbackDurations = 'normal'): number {
    if (reducedMotion.value) return 0
    if (typeof document === 'undefined') return fallbackDurations[kind]
    const cssTime = getComputedStyle(document.documentElement).getPropertyValue(`--duration-${kind}`).trim()
    if (cssTime === '0') return 0
    // CSS 压缩会将 280ms 改写为 .28s；画布引擎的时长必须统一换算为毫秒。
    const match = /^(\d*\.?\d+)(ms|s)$/i.exec(cssTime)
    if (!match) return fallbackDurations[kind]
    const value = Number(match[1]) * (match[2]!.toLowerCase() === 's' ? 1000 : 1)
    return Number.isFinite(value) ? value : fallbackDurations[kind]
  }
  return { reducedMotion, duration }
}
