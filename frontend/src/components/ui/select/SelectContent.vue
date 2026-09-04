<script setup lang="ts">
import { cn } from '@/lib/utils'
import { ChevronDown, ChevronUp } from 'lucide-vue-next'
import {
  SelectContent, type SelectContentProps, SelectPortal, SelectScrollDownButton,
  SelectScrollUpButton, SelectViewport,
} from 'reka-ui'
import { computed } from 'vue'
const props = withDefaults(defineProps<SelectContentProps & { class?: string }>(), { position: 'popper', sideOffset: 4 })
const delegated = computed(() => { const { class: _c, ...rest } = props; return rest })
</script>
<template>
  <SelectPortal>
    <SelectContent
      v-bind="delegated"
      :class="cn('relative z-50 max-h-96 min-w-32 overflow-hidden rounded-md border border-border bg-popover text-popover-foreground shadow-md data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 data-[side=bottom]:slide-in-from-top-2 data-[side=top]:slide-in-from-bottom-2', position === 'popper' && 'data-[side=bottom]:translate-y-1 data-[side=top]:-translate-y-1', props.class)"
    >
      <SelectScrollUpButton class="flex cursor-default items-center justify-center py-1"><ChevronUp class="size-4" /></SelectScrollUpButton>
      <SelectViewport :class="cn('p-1', position === 'popper' && 'h-[var(--reka-select-trigger-height)] w-full min-w-[var(--reka-select-trigger-width)]')">
        <slot />
      </SelectViewport>
      <SelectScrollDownButton class="flex cursor-default items-center justify-center py-1"><ChevronDown class="size-4" /></SelectScrollDownButton>
    </SelectContent>
  </SelectPortal>
</template>
