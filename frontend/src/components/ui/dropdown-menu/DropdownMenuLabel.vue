<script setup lang="ts">
import { cn } from '@/lib/utils'
import { DropdownMenuLabel, type DropdownMenuLabelProps } from 'reka-ui'
import { computed } from 'vue'
const props = defineProps<DropdownMenuLabelProps & { class?: string; inset?: boolean }>()
const delegated = computed(() => { const { class: _c, inset, ...rest } = props; return rest })
</script>
<template>
  <DropdownMenuLabel v-bind="delegated" :class="cn('px-2 py-1.5 text-sm font-semibold', inset && 'pl-8', props.class)">
    <slot />
  </DropdownMenuLabel>
</template>
