<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { activeNavigation, navItems } from '@/config/nav'
import { useSessionStore } from '@/stores/session'

const props = defineProps<{ dock?: boolean }>()
const route = useRoute()
const session = useSessionStore()
const items = computed(() => navItems.filter(item => item.to !== (props.dock ? '/dashboard' : '/') && session.hasPermission(item.permission)))
const active = computed(() => activeNavigation(route.path, items.value))
const pages = computed(() => !['/graph', '/analytics'].includes(active.value?.to ?? '') ? [] : active.value?.children ?? [])
</script>

<template>
  <nav :class="['module-navigation', { 'module-navigation--dock': dock }]" :aria-label="dock ? '大屏模块导航' : '模块导航'">
    <RouterLink v-for="item in items" :key="item.to" :to="item.to" :class="{ 'is-active': active?.to === item.to }" :aria-current="active?.to === item.to ? 'page' : undefined">
      <component :is="item.icon" :size="dock ? 20 : 17" aria-hidden="true" />
      <span>{{ item.label }}</span>
    </RouterLink>
  </nav>
  <nav v-if="!dock && pages.length" class="module-subnavigation" aria-label="模块页面">
    <RouterLink v-for="page in pages" :key="page.to" :to="page.to" :aria-current="(page.activePaths ?? [page.to]).includes(route.path) ? 'page' : undefined">{{ page.label }}</RouterLink>
  </nav>
</template>
