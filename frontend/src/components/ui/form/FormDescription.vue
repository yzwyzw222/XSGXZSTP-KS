<script setup lang="ts">
import { cn } from '@/lib/utils'
const props = defineProps<{ class?: string; id?: string }>()
</script>
<template>
  <p :id="id" data-slot="form-description" :class="cn('text-xs text-muted-foreground', props.class)"><slot /></p>
</template>
