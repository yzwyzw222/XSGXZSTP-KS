<script setup lang="ts">
import { cn } from '@/lib/utils'
import { Label } from 'reka-ui'
const props = defineProps<{ class?: string; for?: string; required?: boolean }>()
</script>

<template>
  <Label :for="props.for" :class="cn('text-sm font-medium leading-none flex items-center gap-1', props.class)">
    <slot /><span v-if="required" class="text-destructive" aria-hidden="true">*</span>
  </Label>
</template>
