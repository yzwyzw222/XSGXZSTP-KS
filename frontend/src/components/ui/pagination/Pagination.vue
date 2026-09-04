<script setup lang="ts">
import { cn } from '@/lib/utils'
const props = defineProps<{ class?: string }>()
</script>
<template>
  <nav role="navigation" aria-label="分页" :class="cn('flex items-center justify-between gap-3 flex-wrap', props.class)"><slot /></nav>
</template>
