<script setup lang="ts">
import { cn } from '@/lib/utils'
import { DropdownMenuContent, type DropdownMenuContentProps, DropdownMenuPortal, useForwardProps } from 'reka-ui'
import { computed } from 'vue'
const props = withDefaults(defineProps<DropdownMenuContentProps & { class?: string }>(), { sideOffset: 4 })
const delegated = computed(() => { const { class: _c, ...rest } = props; return rest })
const forwarded = useForwardProps(delegated)
</script>
<template>
  <DropdownMenuPortal>
    <DropdownMenuContent v-bind="forwarded" :class="cn('z-50 min-w-32 overflow-hidden rounded-md border border-border bg-popover p-1 text-popover-foreground shadow-md data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95', props.class)">
      <slot />
    </DropdownMenuContent>
  </DropdownMenuPortal>
</template>
