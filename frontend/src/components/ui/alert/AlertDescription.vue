<script setup lang="ts">
import { cn } from '@/lib/utils'
const props = defineProps<{ class?: string }>()
</script>
<template>
  <div data-slot="alert-description" :class="cn('col-start-2 grid justify-items-start gap-1 text-sm [&_p]:leading-relaxed', props.class)">
    <slot />
  </div>
</template>
