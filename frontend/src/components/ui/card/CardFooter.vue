<script setup lang="ts">
import { cn } from '@/lib/utils'
const props = defineProps<{ class?: string }>()
</script>
<template>
  <div data-slot="card-footer" :class="cn('flex items-center px-5', props.class)">
    <slot />
  </div>
</template>
