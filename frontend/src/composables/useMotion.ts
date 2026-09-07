import { useMediaQuery } from '@vueuse/core'

const fallbackDurations = { fast: 120, normal: 200, slow: 280, chart: 320 } as const

/** CSS 与画布动效共享时长；VueUse 在作用域销毁时释放系统偏好监听。 */
export function useMotion() {
  const reducedMotion = useMediaQuery('(prefers-reduced-motion: reduce)')
  function duration(kind: keyof typeof fallbackDurations = 'normal'): number {
    if (reducedMotion.value) return 0
    if (typeof document === 'undefined') return fallbackDurations[kind]
    const value = Number.parseFloat(getComputedStyle(document.documentElement).getPropertyValue(`--duration-${kind}`))
    return Number.isFinite(value) ? value : fallbackDurations[kind]
  }
  return { reducedMotion, duration }
}
