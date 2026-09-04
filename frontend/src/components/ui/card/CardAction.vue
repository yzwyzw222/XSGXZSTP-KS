<script setup lang="ts">
import { cn } from '@/lib/utils'
const props = defineProps<{ class?: string }>()
</script>
<template>
  <div data-slot="card-action" :class="cn('absolute top-5 right-5 flex items-center gap-2', props.class)">
    <slot />
  </div>
</template>
