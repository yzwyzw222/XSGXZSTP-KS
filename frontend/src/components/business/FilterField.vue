<script setup lang="ts">
import { cn } from '@/lib/utils'
const props = defineProps<{ label: string; hint?: string; class?: string }>()
</script>
<template>
  <label :class="cn('filter-field', props.class)">
    <span class="filter-field__label">{{ label }}</span>
    <slot />
    <span v-if="hint" class="text-xs text-muted-foreground">{{ hint }}</span>
  </label>
</template>
