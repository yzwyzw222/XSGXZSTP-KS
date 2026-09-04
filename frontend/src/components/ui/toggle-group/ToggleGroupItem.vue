<script setup lang="ts">
import { cn } from '@/lib/utils'
import { ToggleGroupItem, type ToggleGroupItemProps } from 'reka-ui'
import { computed } from 'vue'
const props = defineProps<ToggleGroupItemProps & { class?: string }>()
const delegated = computed(() => { const { class: _c, ...rest } = props; return rest })
</script>
<template>
  <ToggleGroupItem
    v-bind="delegated"
    :class="cn('inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors hover:bg-muted hover:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50 data-[state=on]:bg-accent data-[state=on]:text-accent-foreground h-8 px-3', props.class)"
  >
    <slot />
  </ToggleGroupItem>
</template>
