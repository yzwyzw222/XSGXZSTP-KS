<script setup lang="ts">
import { cn } from '@/lib/utils'
import { PopoverContent, type PopoverContentProps, PopoverPortal, useForwardProps } from 'reka-ui'
import { computed } from 'vue'
const props = withDefaults(defineProps<PopoverContentProps & { class?: string }>(), { align: 'center', sideOffset: 4 })
const delegated = computed(() => { const { class: _c, ...rest } = props; return rest })
const forwarded = useForwardProps(delegated)
</script>
<template>
  <PopoverPortal>
    <PopoverContent v-bind="forwarded" :class="cn('z-50 w-72 rounded-md border border-border bg-popover p-4 text-popover-foreground shadow-md outline-none data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0', props.class)">
      <slot />
    </PopoverContent>
  </PopoverPortal>
</template>
