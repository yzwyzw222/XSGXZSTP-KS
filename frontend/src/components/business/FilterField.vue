<script setup lang="ts">
import { cn } from '@/lib/utils'
const props = defineProps<{ label: string; hint?: string; class?: string }>()
</script>
<template>
  <label :class="cn('grid gap-1.5 text-sm', props.class)">
    <span class="font-medium text-muted-foreground">{{ label }}</span>
    <slot />
    <span v-if="hint" class="text-xs text-muted-foreground/80">{{ hint }}</span>
  </label>
</template>
