import { useColorMode } from '@vueuse/core'
import { computed } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'auto'

/**
 * 全局主题状态。写入 <html data-theme> 与 .dark class，
 * 持久化到 localStorage['aacv-theme']，默认跟随系统。
 */
export const colorMode = useColorMode({
  storageKey: 'aacv-theme',
  attribute: 'data-theme',
  modes: {
    light: 'light',
    dark: 'dark',
    auto: 'auto',
  },
  initialValue: 'auto',
  disableTransition: false,
})

export function useTheme() {
  const theme = computed<ThemeMode>(() => colorMode.value as ThemeMode)
  /** 解析后的实际主题（auto 会解析为 light/dark） */
  const resolvedTheme = computed<'light' | 'dark'>(() => {
    if (colorMode.value === 'dark') return 'dark'
    if (colorMode.value === 'light') return 'light'
    return typeof window !== 'undefined'
      && window.matchMedia?.('(prefers-color-scheme: dark)').matches
      ? 'dark'
      : 'light'
  })
  const isDark = computed(() => resolvedTheme.value === 'dark')

  function setTheme(mode: ThemeMode): void {
    colorMode.value = mode
    syncHtmlClass(resolvedTheme.value)
  }

  function cycleTheme(): void {
    const order: ThemeMode[] = ['light', 'dark', 'auto']
    const next = order[(order.indexOf(theme.value) + 1) % order.length]!
    setTheme(next)
  }

  return { theme, resolvedTheme, isDark, setTheme, cycleTheme }
}

/** useColorMode 的 attribute 只写 data-theme，这里同步 .dark class 供 Tailwind dark: 变体使用。 */
export function syncHtmlClass(resolved: 'light' | 'dark'): void {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  root.classList.toggle('dark', resolved === 'dark')
  root.setAttribute('data-theme', resolved)
  root.style.colorScheme = resolved
}

/** 应用启动时调用，确保 data-theme/.dark 与持久化值一致。 */
export function initTheme(): void {
  const stored = typeof localStorage !== 'undefined' ? localStorage.getItem('aacv-theme') : null
  const resolved = stored === 'dark' || stored === 'light'
    ? stored
    : typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches
      ? 'dark'
      : 'light'
  syncHtmlClass(resolved)
}
