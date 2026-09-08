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
    if (activeChild(item)) expanded.value[item.to] = true
  }
}, { immediate: true })

function isActive(to: string): boolean {
  return route.path === to || (to !== '/' && route.path.startsWith(`${to}/`))
}

function activeChild(item: NavItem): string | undefined {
  const children = item.children ?? []
  const exact = children.find(child => (child.activePaths ?? [child.to]).includes(route.path))
  return exact?.to ?? children.filter(child => isActive(child.to)).sort((a, b) => b.to.length - a.to.length)[0]?.to
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
    <nav class="sidebar-nav min-h-0 flex-1 overflow-y-auto px-3" aria-label="业务导航">
      <section
        v-for="group in groups"
        :key="group.id"
        :aria-label="group.label"
        class="sidebar-group"
        :class="collapsed ? 'border-b border-sidebar-border pb-3 last:border-0' : ''"
      >
        <h2
          class="sidebar-group__label px-3 font-medium text-muted-foreground"
          :class="collapsed ? 'sr-only' : ''"
        >{{ group.label }}</h2>
        <ul class="grid gap-0.5">
        <li v-for="item in group.items" :key="item.to">
          <button
            v-if="item.children?.length && !collapsed"
            type="button"
            class="graph-menu-toggle flex w-full items-center gap-3 rounded-md px-3 text-left text-foreground"
            :aria-label="item.label"
            :class="activeChild(item) ? 'is-active' : ''"
            :aria-expanded="Boolean(expanded[item.to])"
            @click="expanded[item.to] = !expanded[item.to]"
          >
            <component :is="item.icon" class="size-4 shrink-0" aria-hidden="true" />
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
              'sidebar-link group relative flex items-center gap-3 rounded-md px-3',
              collapsed ? 'justify-center' : '',
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
            <component :is="item.icon" class="size-4 shrink-0" aria-hidden="true" />
            <span v-show="!collapsed" class="min-w-0 flex-1">
              <span class="block truncate">{{ item.label }}</span>
            </span>
          </RouterLink>
          <!-- 子项共享紧凑行高，选中项沿用参考配色的靛蓝衬底。 -->
          <ul
            v-if="item.children?.length && !collapsed && expanded[item.to]"
            class="graph-submenu grid gap-0.5"
          >
            <li v-for="child in item.children" :key="child.to">
              <RouterLink
                :to="child.to"
                :aria-current="activeChild(item) === child.to ? 'page' : undefined"
                class="graph-submenu-link flex items-center text-foreground transition-colors"
                :class="activeChild(item) === child.to
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
.sidebar-nav { padding-block: 10px; }
.sidebar-group + .sidebar-group { margin-top: 12px; }
.sidebar-group__label { margin-bottom: 4px; font-size: 11px; line-height: 18px; }
.sidebar-link, .graph-menu-toggle { min-height: var(--nav-item-height); height: var(--nav-item-height); font-size: 13px; }
.graph-menu-toggle.is-active { color: hsl(var(--primary)); }
.graph-submenu-link { position: relative; min-height: 28px; padding-inline: 40px 12px; border-radius: var(--radius-sm); font-size: 12px; }
.graph-menu-toggle:hover, .graph-submenu-link:hover { background: hsl(var(--sidebar-accent)); }
.graph-submenu-link.is-current { background: hsl(var(--sidebar-accent)); color: hsl(var(--sidebar-accent-foreground)); font-weight: var(--font-medium); }
.graph-submenu-link.is-current::before { content: ''; position: absolute; left: 0; width: 2px; height: 16px; border-radius: 2px; background: hsl(var(--primary)); }
@media (min-width: 1024px) and (max-height: 800px) {
  .sidebar-nav { padding-block: 8px; }
  .sidebar-group + .sidebar-group { margin-top: 8px; }
  .sidebar-group__label { margin-bottom: 2px; line-height: 16px; }
  .sidebar-link, .graph-menu-toggle { min-height: 26px; height: 26px; }
  .graph-submenu-link { min-height: 24px; }
}
@media (max-width: 1023px) {
  .sidebar-link, .graph-menu-toggle, .graph-submenu-link { min-height: 44px; height: auto; font-size: 14px; }
}
.graph-menu-toggle:focus-visible, .graph-submenu-link:focus-visible { outline: 2px solid hsl(var(--ring)); outline-offset: -2px; }
@keyframes nav-select { from { opacity: .3; scale: 1 .4; } to { opacity: 1; scale: 1 1; } }
</style>
