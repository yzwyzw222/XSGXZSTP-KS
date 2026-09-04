<script setup lang="ts">
import { cn } from '@/lib/utils'
const props = defineProps<{
  title: string
  description?: string
  class?: string
  divided?: boolean
}>()
</script>
<template>
  <header
    :class="cn('flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between', props.divided && 'border-b border-border pb-4', props.class)"
  >
    <div class="min-w-0 space-y-1.5">
      <h1 class="text-2xl sm:text-3xl font-semibold tracking-tight text-foreground">{{ title }}</h1>
      <p v-if="description" class="max-w-3xl text-sm text-muted-foreground leading-relaxed">{{ description }}</p>
    </div>
    <div v-if="$slots.actions || $slots.stamp" class="flex flex-wrap items-center gap-3">
      <div v-if="$slots.stamp"><slot name="stamp" /></div>
      <div v-if="$slots.actions" class="flex flex-wrap items-center gap-2"><slot name="actions" /></div>
    </div>
  </header>
</template>
