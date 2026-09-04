<script setup lang="ts">
import { cn } from '@/lib/utils'

const props = defineProps<{ class?: string }>()
</script>

<template>
  <div data-slot="card-title" :class="cn('leading-none font-semibold text-base', props.class)">
    <slot />
  </div>
</template>
