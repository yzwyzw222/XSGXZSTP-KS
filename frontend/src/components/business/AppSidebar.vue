<script setup lang="ts">
import { ChevronsLeft } from 'lucide-vue-next'
import { RouterLink, useRoute } from 'vue-router'

import type { NavItem } from '@/config/nav'
import { cn } from '@/lib/utils'

const props = withDefaults(defineProps<{
  items: NavItem[]
  collapsed?: boolean
  brandLogo?: string
}>(), { collapsed: false, brandLogo: '/favicon.svg' })

const emit = defineEmits<{ (e: 'toggle-collapse'): void }>()
const route = useRoute()

function isActive(to: string): boolean {
  return to === '/' ? route.path === '/' : route.path.startsWith(to)
}
</script>

<template>
  <div class="flex h-full flex-col bg-sidebar text-sidebar-foreground">
    <!-- 品牌区 -->
    <div class="flex h-16 shrink-0 items-center gap-2.5 border-b border-sidebar-border px-4">
      <RouterLink to="/" class="flex min-w-0 items-center gap-2.5" aria-label="学术成果爬虫及可视化系统 工作台">
        <span class="grid size-9 shrink-0 place-items-center rounded-lg border border-primary/40 bg-primary/12 text-lg font-bold text-primary">
          <img :src="brandLogo" alt="" class="size-6" />
        </span>
        <span v-show="!collapsed" class="min-w-0">
          <strong class="block truncate text-[13px] font-semibold tracking-tight text-foreground">学术成果爬虫及可视化系统</strong>
        </span>
      </RouterLink>
    </div>

    <!-- 导航 -->
    <nav class="flex-1 overflow-y-auto px-2 py-3" aria-label="业务导航">
      <ul class="grid gap-1">
        <li v-for="item in items" :key="item.to">
          <RouterLink
            :to="item.to"
            :aria-current="isActive(item.to) ? 'page' : undefined"
            :title="collapsed ? item.label : undefined"
            :class="cn(
              'group relative flex items-center gap-3 rounded-md px-3 text-sm transition-colors',
              collapsed ? 'h-10 justify-center' : 'h-10',
              isActive(item.to)
                ? 'bg-sidebar-accent font-medium text-sidebar-accent-foreground'
                : 'text-sidebar-foreground hover:bg-sidebar-accent/60 hover:text-foreground',
            )"
          >
            <span
              v-if="isActive(item.to)"
              class="absolute left-0 top-1/2 h-5 w-0.5 -translate-y-1/2 rounded-r bg-primary"
              aria-hidden="true"
            />
            <component :is="item.icon" class="size-[18px] shrink-0" aria-hidden="true" />
            <span v-show="!collapsed" class="min-w-0 flex-1">
              <span class="block truncate">{{ item.label }}</span>
            </span>
          </RouterLink>
        </li>
      </ul>
    </nav>

    <!-- 折叠按钮（仅桌面） -->
    <div class="hidden shrink-0 border-t border-sidebar-border p-2 lg:block">
      <button
        type="button"
        class="flex h-9 w-full items-center justify-center gap-2 rounded-md text-xs text-muted-foreground transition-colors hover:bg-sidebar-accent hover:text-foreground"
        :aria-label="collapsed ? '展开侧栏' : '折叠侧栏'"
        :aria-expanded="!collapsed"
        @click="emit('toggle-collapse')"
      >
        <ChevronsLeft class="size-4 transition-transform" :class="collapsed ? 'rotate-180' : ''" />
        <span v-show="!collapsed">折叠侧栏</span>
      </button>
    </div>
  </div>
</template>
