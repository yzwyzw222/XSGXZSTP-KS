<script setup lang="ts">
import { Separator, type SeparatorProps } from 'reka-ui'
import { cn } from '@/lib/utils'
import { computed } from 'vue'

const props = withDefaults(defineProps<SeparatorProps & { class?: string }>(), {
  orientation: 'horizontal',
  decorative: true,
})
const delegated = computed(() => {
  const { class: _c, ...rest } = props
  return rest
})
</script>

<template>
  <Separator
    v-bind="delegated"
    :class="cn('bg-border shrink-0', props.orientation === 'horizontal' ? 'h-px w-full' : 'h-full w-px', props.class)"
  />
</template>
