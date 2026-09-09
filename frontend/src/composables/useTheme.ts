import { computed } from 'vue'

/** 全系统固定深蓝，图表和图谱共用这一只读状态。 */
export function useTheme() {
  const theme = computed(() => 'dark' as const)
  return { theme, resolvedTheme: theme, isDark: computed(() => true) }
}

export function initTheme(): void {
  if (typeof document === 'undefined') return
  document.documentElement.classList.add('dark')
  document.documentElement.dataset.theme = 'dark'
  document.documentElement.style.colorScheme = 'dark'
  try { localStorage.setItem('aacv-theme', 'dark') } catch {
    // 隐私模式下存储可能不可用，主题由根节点保证。
  }
}
