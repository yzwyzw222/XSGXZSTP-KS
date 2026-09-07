<script setup lang="ts">
import { ElButton, ElDropdown, ElDropdownItem, ElDropdownMenu } from 'element-plus'
import { Check, Monitor, Moon, Sun } from 'lucide-vue-next'

import { usePreferencesStore } from '@/stores/preferences'
import type { ThemeMode } from '@/composables/useTheme'

const preferences = usePreferencesStore()

const options: Array<{ mode: ThemeMode; label: string; icon: typeof Sun }> = [
  { mode: 'light', label: '浅色', icon: Sun },
  { mode: 'dark', label: '深色', icon: Moon },
  { mode: 'auto', label: '跟随系统', icon: Monitor },
]

function choose(mode: ThemeMode): void {
  preferences.setTheme(mode)
}
</script>

<template>
  <!-- Element Plus 的下拉不锁定 body 滚动，避免打开菜单时主区宽度跳动 -->
  <ElDropdown trigger="click" placement="bottom-end" @command="choose">
    <ElButton text circle aria-label="切换主题" style="--el-button-text-color: hsl(var(--foreground))">
      <Sun v-if="preferences.theme === 'light'" class="size-4" aria-hidden="true" />
      <Moon v-else-if="preferences.theme === 'dark'" class="size-4" aria-hidden="true" />
      <Monitor v-else class="size-4" aria-hidden="true" />
    </ElButton>
    <template #dropdown>
      <ElDropdownMenu>
        <ElDropdownItem
          v-for="option in options"
          :key="option.mode"
          :command="option.mode"
          :aria-label="`${option.label}${preferences.theme === option.mode ? '（当前）' : ''}`"
        >
          <span class="flex items-center gap-2">
            <component :is="option.icon" class="size-4" aria-hidden="true" />
            {{ option.label }}
            <Check
              v-if="preferences.theme === option.mode"
              class="ml-auto size-3.5 text-primary"
              aria-hidden="true"
            />
          </span>
        </ElDropdownItem>
      </ElDropdownMenu>
    </template>
  </ElDropdown>
</template>
