<script setup lang="ts">
import { cn } from '@/lib/utils'
const props = withDefaults(defineProps<{
  title?: string
  subtitle?: string
  class?: string
  bodyClass?: string
  padded?: boolean
}>(), { padded: true })
</script>
<template>
  <section :class="cn('rounded-xl border border-border bg-card shadow-sm overflow-hidden', props.class)">
    <header
      v-if="title || $slots.actions || $slots.title"
      class="flex min-h-14 items-center justify-between gap-3 border-b border-border px-4 py-3"
    >
      <div class="min-w-0 space-y-0.5">
        <slot name="title">
          <h2 class="truncate text-sm font-semibold text-foreground">{{ title }}</h2>
        </slot>
        <p v-if="subtitle" class="truncate text-xs text-muted-foreground">{{ subtitle }}</p>
      </div>
      <div v-if="$slots.actions" class="flex shrink-0 items-center gap-2"><slot name="actions" /></div>
    </header>
    <div :class="cn(props.padded && 'p-4', props.bodyClass)"><slot /></div>
  </section>
</template>
