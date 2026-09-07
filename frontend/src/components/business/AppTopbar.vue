<script setup lang="ts">
import { ElButton, ElTooltip } from 'element-plus'
import { Bell, Menu, Search } from 'lucide-vue-next'
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import Breadcrumb from '@/components/business/Breadcrumb.vue'
import ThemeToggle from '@/components/business/ThemeToggle.vue'
import UserMenu from '@/components/business/UserMenu.vue'
import { useSessionStore } from '@/stores/session'

const props = defineProps<{ loggingOut?: boolean; alertCount?: number }>()
const emit = defineEmits<{
  (e: 'open-sidebar'): void
  (e: 'open-palette'): void
  (e: 'logout'): void
}>()

const router = useRouter()
const sessionStore = useSessionStore()

/** 无 OPERATIONS_READ 权限时不渲染通知入口，避免出现没有有效行为的装饰按钮。 */
const canViewOperations = computed(() => sessionStore.hasPermission('OPERATIONS_READ'))

function openOperations(): void {
  void router.push('/operations')
}
</script>

<template>
  <header
    class="app-topbar sticky top-0 flex items-center gap-2 border-b border-border bg-card px-3 sm:px-6"
  >
    <ElButton
      text
      circle
      class="lg:hidden"
      aria-label="打开导航菜单"
      style="--el-button-text-color: hsl(var(--foreground))"
      @click="emit('open-sidebar')"
    >
      <Menu class="size-5" aria-hidden="true" />
    </ElButton>

    <div class="min-w-0 flex-1">
      <Breadcrumb />
    </div>

    <div class="flex shrink-0 items-center gap-1 sm:gap-2">
      <ElButton
        plain
        size="small"
        class="app-topbar__search hidden justify-start gap-2 md:flex"
        style="color: hsl(var(--muted-foreground))"
        @click="emit('open-palette')"
      >
        <Search class="size-4" aria-hidden="true" />
        <span class="text-xs">搜索或跳转…</span>
        <kbd class="ml-auto rounded border border-border bg-muted px-1.5 py-0.5 text-[10px]">Ctrl K</kbd>
      </ElButton>
      <ElButton
        text
        circle
        class="md:hidden"
        aria-label="搜索或跳转"
        style="--el-button-text-color: hsl(var(--foreground))"
        @click="emit('open-palette')"
      >
        <Search class="size-5" aria-hidden="true" />
      </ElButton>

      <ElTooltip v-if="canViewOperations" content="查看运行监控与告警" placement="bottom" :show-after="200">
        <ElButton
          text
          circle
          class="relative"
          :aria-label="props.alertCount && props.alertCount > 0
            ? `系统通知，${props.alertCount} 条未确认告警`
            : '系统通知'"
          style="--el-button-text-color: hsl(var(--foreground))"
          @click="openOperations"
        >
          <Bell class="size-5" aria-hidden="true" />
          <span
            v-if="props.alertCount && props.alertCount > 0"
            class="absolute right-1.5 top-1.5 size-2 rounded-full bg-destructive ring-2 ring-background"
            aria-hidden="true"
          />
        </ElButton>
      </ElTooltip>

      <ThemeToggle />

      <span class="mx-1 hidden h-6 w-px bg-border sm:block" aria-hidden="true" />

      <UserMenu :logging-out="loggingOut" @logout="emit('logout')" />
    </div>
  </header>
</template>

<style scoped>
.app-topbar { height: var(--topbar-height); z-index: var(--z-topbar); }
.app-topbar__search { width: 15rem; height: 34px; --el-button-bg-color: hsl(var(--background)); }
.app-topbar__search :deep(> span) { width: 100%; gap: var(--space-2); }
</style>
