<script setup lang="ts">
import { ElButton } from 'element-plus'
import { Menu, Search } from 'lucide-vue-next'

import Breadcrumb from '@/components/business/Breadcrumb.vue'
import ThemeToggle from '@/components/business/ThemeToggle.vue'
import UserMenu from '@/components/business/UserMenu.vue'

defineProps<{ loggingOut?: boolean }>()
const emit = defineEmits<{
  (e: 'open-sidebar'): void
  (e: 'open-palette'): void
  (e: 'logout'): void
}>()

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
