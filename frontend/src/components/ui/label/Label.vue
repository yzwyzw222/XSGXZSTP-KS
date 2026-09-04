<script setup lang="ts">
import { Label, type LabelProps } from 'reka-ui'
import { cn } from '@/lib/utils'
import { computed } from 'vue'

const props = defineProps<LabelProps & { class?: string }>()
const delegated = computed(() => {
  const { class: _c, ...rest } = props
  return rest
})
</script>

<template>
  <Label
    v-bind="delegated"
    :class="cn('text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70 select-none', props.class)"
  >
    <slot />
  </Label>
</template>
