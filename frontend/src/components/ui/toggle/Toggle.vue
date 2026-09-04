<script setup lang="ts">
import { cva } from 'class-variance-authority'
import { cn } from '@/lib/utils'
import { Toggle, type ToggleEmits, type ToggleProps, useForwardPropsEmits } from 'reka-ui'
import { computed } from 'vue'

const toggleVariants = cva(
  'inline-flex items-center justify-center gap-2 rounded-md text-sm font-medium transition-colors hover:bg-muted hover:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50 data-[state=on]:bg-accent data-[state=on]:text-accent-foreground [&_svg]:size-4',
  {
    variants: {
      variant: { default: 'bg-transparent', outline: 'border border-input bg-transparent shadow-sm hover:bg-accent hover:text-accent-foreground' },
      size: { default: 'h-9 px-3', sm: 'h-8 px-2', lg: 'h-10 px-3' },
    },
    defaultVariants: { variant: 'default', size: 'default' },
  },
)

const props = withDefaults(defineProps<ToggleProps & {
  variant?: 'default' | 'outline'
  size?: 'default' | 'sm' | 'lg'
  class?: string
}>(), { variant: 'default', size: 'default' })
const emit = defineEmits<ToggleEmits>()
const delegated = computed(() => {
  const { class: _c, variant, size, ...rest } = props
  return rest
})
const forwarded = useForwardPropsEmits(delegated, emit)
</script>

<template>
  <Toggle v-bind="forwarded" :class="cn(toggleVariants({ variant: props.variant, size: props.size }), props.class)"><slot /></Toggle>
</template>
