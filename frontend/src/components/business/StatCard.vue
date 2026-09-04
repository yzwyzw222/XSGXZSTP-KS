<script setup lang="ts">
import { cn } from '@/lib/utils'
import CountUpNumber from '@/components/CountUpNumber.vue'
import type { Component } from 'vue'
const barClass: Record<string, string> = {
  blue: 'bg-primary', cyan: 'bg-info', green: 'bg-success',
  violet: 'bg-chart-4', amber: 'bg-warning', rose: 'bg-destructive',
}
const iconClass: Record<string, string> = {
  blue: 'text-primary', cyan: 'text-info', green: 'text-success',
  violet: 'text-chart-4', amber: 'text-warning', rose: 'text-destructive',
}
const props = withDefaults(defineProps<{
  label: string
  value: number
  note?: string
  icon?: Component
  tone?: keyof typeof barClass
  suffix?: string
  class?: string
}>(), { tone: 'blue' })
</script>
<template>
  <article :class="cn('relative overflow-hidden rounded-xl border border-border bg-card p-4 pl-5 shadow-sm', props.class)">
    <span class="absolute inset-y-0 left-0 w-1" :class="barClass[props.tone]" aria-hidden="true" />
    <div class="flex items-start justify-between gap-3">
      <div class="min-w-0 space-y-1">
        <span class="block truncate text-xs font-medium uppercase tracking-wide text-muted-foreground">{{ label }}</span>
        <strong class="block text-3xl font-semibold tracking-tight text-foreground">
          <CountUpNumber :value="value" :suffix="suffix" />
        </strong>
        <small v-if="note" class="block truncate text-xs text-muted-foreground">{{ note }}</small>
      </div>
      <component :is="icon" v-if="icon" class="size-8 shrink-0 opacity-80" :class="iconClass[props.tone]" aria-hidden="true" />
    </div>
  </article>
</template>
