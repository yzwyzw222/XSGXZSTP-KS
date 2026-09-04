<script setup lang="ts">
import { cn } from '@/lib/utils'
import { DropdownMenuItem, type DropdownMenuItemProps } from 'reka-ui'
import { computed } from 'vue'
const props = defineProps<DropdownMenuItemProps & { class?: string; inset?: boolean; variant?: 'default' | 'destructive' }>()
const delegated = computed(() => { const { class: _c, inset, variant, ...rest } = props; return rest })
</script>
<template>
  <DropdownMenuItem
    v-bind="delegated"
    :data-inset="inset ? '' : undefined"
    :class="cn('relative flex cursor-default select-none items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50 data-[inset]:pl-8 [&_svg]:size-4 [&_svg]:shrink-0', variant === 'destructive' && 'text-destructive focus:text-destructive', props.class)"
  >
    <slot />
  </DropdownMenuItem>
</template>
