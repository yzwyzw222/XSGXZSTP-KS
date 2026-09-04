<script setup lang="ts">
import { cn } from '@/lib/utils'
import type { EntityLinkItem } from './types'
const props = defineProps<{ items: EntityLinkItem[]; activeId?: string | number; class?: string }>()
const emit = defineEmits<{ (e: 'select', item: EntityLinkItem): void }>()
</script>
<template>
  <div :class="cn('flex flex-wrap gap-2', props.class)">
    <component
      :is="item.to ? 'RouterLink' : 'button'"
      v-for="item in items"
      :key="item.id"
      :to="item.to"
      :type="item.to ? undefined : 'button'"
      :aria-current="activeId === item.id ? 'true' : undefined"
      class="rounded-md border px-2.5 py-1 text-xs transition-colors"
      :class="activeId === item.id
        ? 'border-primary bg-primary/12 text-primary'
        : 'border-border bg-card text-muted-foreground hover:border-primary/50 hover:text-foreground'"
      @click="!item.to && emit('select', item)"
    >
      {{ item.label }}
    </component>
  </div>
</template>
