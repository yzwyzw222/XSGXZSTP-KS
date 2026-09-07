import { defineStore } from 'pinia'
import { useStorage } from '@vueuse/core'

import { useTheme } from '@/composables/useTheme'

/**
 * 应用级非敏感偏好：主题模式与侧栏折叠状态。
 *
 * - 主题不新建第二份状态，直接复用 `composables/useTheme` 的 `colorMode`
 *   （由 VueUse 持久化到 `localStorage['aacv-theme']`），保证单一权威来源；
 * - 这里只保存偏好，绝不保存认证信息或权限快照。
 */
export const usePreferencesStore = defineStore('preferences', () => {
  const sidebarCollapsed = useStorage('aacv-sidebar-collapsed', false)

  const { theme, resolvedTheme, isDark, setTheme, cycleTheme } = useTheme()

  function toggleSidebar(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  return {
    sidebarCollapsed,
    theme,
    resolvedTheme,
    isDark,
    setTheme,
    cycleTheme,
    toggleSidebar,
  }
})
