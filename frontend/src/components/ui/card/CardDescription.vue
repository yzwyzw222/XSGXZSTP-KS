<script setup lang="ts">
import { cn } from '@/lib/utils'
const props = defineProps<{ class?: string }>()
</script>
<template>
  <div data-slot="card-description" :class="cn('text-sm text-muted-foreground', props.class)">
    <slot />
  </div>
</template>
