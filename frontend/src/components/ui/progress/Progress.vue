<script setup lang="ts">
import { cn } from '@/lib/utils'
import { ProgressIndicator, ProgressRoot, type ProgressRootProps, useForwardProps } from 'reka-ui'
import { computed } from 'vue'
const props = withDefaults(defineProps<ProgressRootProps & { class?: string }>(), { modelValue: 0 })
const delegated = computed(() => { const { class: _c, ...rest } = props; return rest })
const forwarded = useForwardProps(delegated)
</script>
<template>
  <ProgressRoot v-bind="forwarded" :class="cn('relative h-2 w-full overflow-hidden rounded-full bg-secondary', props.class)">
    <ProgressIndicator
      class="h-full w-full flex-1 bg-primary transition-transform duration-500 ease-out"
      :style="{ transform: `translateX(-${100 - (props.modelValue ?? 0)}%)` }"
    />
  </ProgressRoot>
</template>
