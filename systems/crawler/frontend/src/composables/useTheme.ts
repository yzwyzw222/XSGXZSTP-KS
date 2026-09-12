import { computed } from 'vue'

/** 全系统固定浅色，图表和图谱共用这一只读状态。 */
export function useTheme() {
  const theme = computed(() => 'light' as const)
  return { theme, resolvedTheme: theme, isDark: computed(() => false) }
}

export function initTheme(): void {
  if (typeof document === 'undefined') return
  document.documentElement.classList.remove('dark')
  document.documentElement.dataset.theme = 'light'
  document.documentElement.style.colorScheme = 'light'
  try { localStorage.setItem('aacv-theme', 'light') } catch {
    // 隐私模式下存储可能不可用，主题由根节点保证。
  }
}
