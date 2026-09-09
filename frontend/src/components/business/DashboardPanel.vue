<script setup lang="ts">
import { ArrowUpRight } from 'lucide-vue-next'
import { RouterLink } from 'vue-router'
import PanelSection from './PanelSection.vue'
import type { DashboardRegion } from '@/composables/useDashboard'

defineProps<{ title: string; to: string; region: DashboardRegion<unknown>; empty?: boolean }>()
</script>
<template>
  <PanelSection :title="title" class="dashboard-panel" :aria-busy="region.loading">
    <template #actions><slot v-if="region.allowed && region.data" name="actions" /><RouterLink v-if="region.allowed" :to="to" :aria-label="`查看${title}详情`" class="dashboard-panel__link"><ArrowUpRight :size="16" /></RouterLink></template>
    <p v-if="!region.allowed" class="dashboard-state">当前账号无此模块权限</p>
    <template v-else>
      <p v-if="region.error" role="status" class="dashboard-state dashboard-state--error">{{ region.data ? '更新失败，保留上次结果。' : '加载失败。' }}{{ region.error }}</p>
      <p v-if="region.loading" role="status" class="dashboard-state" :class="{ 'dashboard-state--compact': region.data }">{{ region.data ? '正在更新…' : '正在加载…' }}</p>
      <template v-if="region.data">
        <p v-if="empty" class="dashboard-state">当前范围暂无数据</p>
        <slot v-else />
      </template>
    </template>
  </PanelSection>
</template>
