<script setup lang="ts">
import { cn } from '@/lib/utils'
import { TooltipContent, type TooltipContentProps, TooltipPortal, useForwardProps } from 'reka-ui'
import { computed } from 'vue'
const props = withDefaults(defineProps<TooltipContentProps & { class?: string }>(), { sideOffset: 6 })
const delegated = computed(() => { const { class: _c, ...rest } = props; return rest })
const forwarded = useForwardProps(delegated)
</script>
<template>
  <TooltipPortal>
    <TooltipContent v-bind="forwarded" :class="cn('z-50 overflow-hidden rounded-md bg-foreground px-3 py-1.5 text-xs text-background shadow-md animate-in fade-in-0 zoom-in-95 max-w-xs', props.class)">
      <slot />
    </TooltipContent>
  </TooltipPortal>
</template>
