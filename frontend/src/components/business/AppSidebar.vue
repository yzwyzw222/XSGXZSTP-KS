<script setup lang="ts">
import { ChevronDown, ChevronsLeft } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { groupNavigation, type NavItem } from '@/config/nav'
import { cn } from '@/lib/utils'

const props = withDefaults(defineProps<{
  items: NavItem[]
  collapsed?: boolean
  brandLogo?: string
}>(), { collapsed: false, brandLogo: '/favicon.svg' })

const emit = defineEmits<{ (e: 'toggle-collapse'): void }>()
const route = useRoute()
const groups = computed(() => groupNavigation(props.items))
const expanded = ref<Record<string, boolean>>({})

watch(() => route.path, () => {
  for (const item of props.items) {
    if (item.children?.length && isActive(item.to)) expanded.value[item.to] = true
  }
}, { immediate: true })

function isActive(to: string): boolean {
  return route.path === to || (to !== '/' && route.path.startsWith(`${to}/`))
}
</script>

<template>
  <div class="flex h-full flex-col bg-sidebar text-sidebar-foreground">
    <!-- 品牌区 -->
    <div class="sidebar-brand shrink-0">
      <RouterLink to="/" class="flex min-w-0 items-center gap-2.5" aria-label="学术成果爬虫及可视化系统 工作台">
        <span class="grid size-8 shrink-0 place-items-center">
          <img :src="brandLogo" alt="" class="size-8" />
        </span>
        <span v-show="!collapsed" class="min-w-0">
          <strong class="block text-sm font-semibold tracking-wide text-foreground">AACV System</strong>
          <span class="block text-xs text-muted-foreground">学术成果工作台</span>
        </span>
      </RouterLink>
    </div>

    <!-- 导航 -->
    <nav class="sidebar-nav flex-1 overflow-y-auto px-3 py-5" aria-label="业务导航">
      <section
        v-for="group in groups"
        :key="group.id"
        :aria-label="group.label"
        class="mb-6 last:mb-0"
        :class="collapsed ? 'border-b border-sidebar-border pb-3 last:border-0' : ''"
      >
        <h2
          class="mb-2 px-3 text-xs font-medium text-muted-foreground"
          :class="collapsed ? 'sr-only' : ''"
        >{{ group.label }}</h2>
        <ul class="grid gap-1">
        <li v-for="item in group.items" :key="item.to">
          <button
            v-if="item.children?.length && !collapsed"
            type="button"
            class="graph-menu-toggle flex w-full items-center gap-3 rounded-md px-3 text-left text-foreground"
            :aria-label="item.label"
            :aria-expanded="Boolean(expanded[item.to])"
            @click="expanded[item.to] = !expanded[item.to]"
          >
            <component :is="item.icon" class="size-[18px] shrink-0" aria-hidden="true" />
            <span class="min-w-0 flex-1 truncate">{{ item.label }}</span>
            <ChevronDown class="size-4 shrink-0 transition-transform" :class="expanded[item.to] ? 'rotate-180' : ''" aria-hidden="true" />
          </button>
          <RouterLink
            v-else
            :to="item.to"
            :aria-label="item.label"
            :aria-current="isActive(item.to) ? 'page' : undefined"
            :title="collapsed ? item.label : undefined"
            :class="cn(
              'sidebar-link group relative flex items-center gap-3 rounded-md px-3 text-sm',
              collapsed ? 'h-10 justify-center' : 'h-10',
              isActive(item.to)
                ? 'bg-sidebar-accent font-medium text-sidebar-accent-foreground'
                : 'text-sidebar-foreground hover:bg-sidebar-accent/60 hover:text-foreground',
            )"
          >
            <span
              v-if="isActive(item.to)"
              class="sidebar-link__mark absolute left-0 top-1/2 h-4 w-px -translate-y-1/2 bg-primary"
              aria-hidden="true"
            />
            <component :is="item.icon" class="size-[18px] shrink-0" aria-hidden="true" />
            <span v-show="!collapsed" class="min-w-0 flex-1">
              <span class="block truncate">{{ item.label }}</span>
            </span>
          </RouterLink>
          <!-- 子项统一缩进，选中态按参考图使用中性背景。 -->
          <ul
            v-if="item.children?.length && !collapsed && expanded[item.to]"
            class="graph-submenu grid gap-1"
          >
            <li v-for="child in item.children" :key="child.to">
              <RouterLink
                :to="child.to"
                :aria-current="route.path === child.to ? 'page' : undefined"
                class="graph-submenu-link flex items-center text-foreground transition-colors"
                :class="route.path === child.to
                  ? 'is-current'
                  : ''"
              >
                {{ child.label }}
              </RouterLink>
            </li>
          </ul>
        </li>
        </ul>
      </section>
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

<style scoped>
.sidebar-brand { display: flex; align-items: center; height: var(--topbar-height); padding-inline: var(--space-4); border-bottom: 1px solid hsl(var(--sidebar-border)); }
.sidebar-link { transition: color var(--duration-fast), background-color var(--duration-fast); }
.sidebar-link__mark { animation: nav-select var(--duration-normal) var(--ease-emphasized); }
.graph-menu-toggle { min-height: 48px; font-size: 14px; }
.graph-submenu-link { min-height: 52px; padding-inline: 42px 12px; border-radius: 10px; font-size: 14px; }
.graph-menu-toggle:hover, .graph-submenu-link:hover { background: hsl(var(--muted)); }
.graph-submenu-link.is-current { background: #efefef; }
:global(html.dark) .graph-submenu-link.is-current { background: hsl(var(--muted)); }
.graph-menu-toggle:focus-visible, .graph-submenu-link:focus-visible { outline: 2px solid hsl(var(--ring)); outline-offset: -2px; }
@keyframes nav-select { from { opacity: .3; scale: 1 .4; } to { opacity: 1; scale: 1 1; } }
</style>
