<script setup lang="ts">
import { cva } from 'class-variance-authority'
import { cn } from '@/lib/utils'

const alertVariants = cva(
  'relative w-full rounded-lg border px-4 py-3 text-sm grid has-[>svg]:grid-cols-[calc(var(--space)*5)_1fr] grid-cols-[0_1fr] has-[>svg]:gap-x-3 gap-y-0.5 items-start [&>svg]:size-4 [&>svg]:translate-y-0.5 [&>svg]:text-current',
  {
    variants: {
      variant: {
        default: 'bg-card text-card-foreground',
        destructive: 'text-destructive bg-destructive/8 border-destructive/40 [&>svg]:text-current *:data-[slot=alert-description]:text-destructive/90',
        success: 'text-success bg-success/8 border-success/40 [&>svg]:text-current *:data-[slot=alert-description]:text-success/90',
        warning: 'text-warning bg-warning/8 border-warning/40 [&>svg]:text-current *:data-[slot=alert-description]:text-warning/90',
        info: 'text-info bg-info/8 border-info/40 [&>svg]:text-current *:data-[slot=alert-description]:text-info/90',
      },
    },
    defaultVariants: { variant: 'default' },
  },
)

const props = withDefaults(defineProps<{
  variant?: 'default' | 'destructive' | 'success' | 'warning' | 'info'
  class?: string
}>(), { variant: 'default' })
</script>

<template>
  <div role="alert" :class="cn(alertVariants({ variant: props.variant }), props.class)">
    <slot />
  </div>
</template>
