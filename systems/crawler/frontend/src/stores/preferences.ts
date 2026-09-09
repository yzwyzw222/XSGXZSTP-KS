import { defineStore } from 'pinia'
import { useStorage } from '@vueuse/core'

import { useTheme } from '@/composables/useTheme'

/** 非敏感导航偏好；主题只读，认证与权限由会话仓库管理。 */
export const usePreferencesStore = defineStore('preferences', () => {
  const sidebarCollapsed = useStorage('aacv-sidebar-collapsed', false)

  const { theme, resolvedTheme, isDark } = useTheme()

  function toggleSidebar(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  return {
    sidebarCollapsed,
    theme,
    resolvedTheme,
    isDark,
    toggleSidebar,
  }
})
