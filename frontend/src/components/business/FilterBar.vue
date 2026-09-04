<script setup lang="ts">
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { ChevronDown } from 'lucide-vue-next'
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
const gridCols: Record<number, string> = {
  1: 'sm:grid-cols-1', 2: 'sm:grid-cols-2', 3: 'sm:grid-cols-2 lg:grid-cols-3',
  4: 'sm:grid-cols-2 lg:grid-cols-4', 5: 'sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5',
  6: 'sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6',
}
</script>
<template>
  <div :class="cn('rounded-xl border border-border bg-card p-4 shadow-sm', props.class)">
    <div v-if="collapsible" class="mb-3 flex items-center justify-between">
      <span class="text-sm font-medium text-foreground">筛选条件</span>
      <Button variant="ghost" size="sm" :aria-expanded="!collapsed" @click="emit('toggle')">
        {{ collapsed ? '展开' : '收起' }}<ChevronDown class="size-4 transition-transform" :class="collapsed ? '' : 'rotate-180'" />
      </Button>
    </div>
    <div v-show="!collapsed" class="grid grid-cols-1 gap-3" :class="gridCols[props.columns ?? 4]">
      <slot />
    </div>
    <div v-if="$slots.footer || true" class="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-border pt-3">
      <div class="text-xs text-muted-foreground"><slot name="meta" /></div>
      <div class="flex items-center gap-2">
        <slot name="actions" />
        <Button variant="outline" size="sm" :disabled="applying" @click="emit('reset')">{{ resetText }}</Button>
        <Button size="sm" :loading="applying" @click="emit('apply')">{{ applyText }}</Button>
      </div>
    </div>
  </div>
</template>
