<script setup lang="ts">
import { cn } from '@/lib/utils'
const props = defineProps<{ class?: string }>()
</script>
<template>
  <div data-slot="form-item" :class="cn('grid gap-1.5', props.class)"><slot /></div>
</template>
