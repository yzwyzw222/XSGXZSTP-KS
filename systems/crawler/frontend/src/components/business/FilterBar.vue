<script setup lang="ts">
import { ElButton } from 'element-plus'
import { ChevronDown } from 'lucide-vue-next'
import { useMediaQuery } from '@vueuse/core'
import { computed, ref } from 'vue'

import { cn } from '@/lib/utils'

const props = withDefaults(defineProps<{
  class?: string
  columns?: number
  collapsible?: boolean
  collapsed?: boolean
  applying?: boolean
  applyText?: string
  resetText?: string
}>(), { applyText: '应用筛选', resetText: '重置', collapsible: false, collapsed: false, applying: false })

const emit = defineEmits<{ (e: 'apply'): void; (e: 'reset'): void; (e: 'toggle'): void }>()
const narrow = useMediaQuery('(max-width: 767px)')
const mobileExpanded = ref(false)
const isCollapsed = computed(() => props.collapsible ? props.collapsed : narrow.value && !mobileExpanded.value)
function toggle(): void {
  if (props.collapsible) emit('toggle')
  else mobileExpanded.value = !mobileExpanded.value
}

/** 单列筛选靠近对应表头，跨字段条件在窄屏退化为单列。 */
const gridCols: Record<number, string> = {
  1: 'sm:grid-cols-1', 2: 'sm:grid-cols-2', 3: 'sm:grid-cols-2 lg:grid-cols-3',
  4: 'sm:grid-cols-2 lg:grid-cols-4', 5: 'sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5',
  6: 'sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6',
}
</script>

<template>
  <section :class="cn('filter-bar', props.class)" aria-label="筛选条件">
    <div v-if="collapsible || narrow" class="mb-3 flex items-center justify-between">
      <h2 class="text-sm font-medium text-foreground">筛选条件</h2>
      <ElButton text size="small" :aria-expanded="!isCollapsed" @click="toggle">
        {{ isCollapsed ? '展开筛选' : '收起筛选' }}
        <ChevronDown class="size-4 transition-transform" :class="isCollapsed ? '' : 'rotate-180'" aria-hidden="true" />
      </ElButton>
    </div>
    <div v-show="!isCollapsed" class="filter-bar__fields grid grid-cols-1 gap-3" :class="gridCols[props.columns ?? 4]">
      <slot />
    </div>
    <div class="filter-bar__footer">
      <div class="text-xs text-muted-foreground"><slot name="meta" /></div>
      <div v-show="!isCollapsed" class="flex flex-wrap items-center gap-2">
        <slot name="actions" />
        <ElButton plain size="small" :disabled="applying" @click="emit('reset')">{{ resetText }}</ElButton>
        <ElButton type="primary" size="small" :loading="applying" @click="emit('apply')">{{ applyText }}</ElButton>
      </div>
    </div>
  </section>
</template>
