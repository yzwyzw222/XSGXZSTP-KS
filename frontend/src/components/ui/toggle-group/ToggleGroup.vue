<script setup lang="ts">
import { cn } from '@/lib/utils'
import { ToggleGroupRoot, type ToggleGroupRootEmits, type ToggleGroupRootProps, useForwardPropsEmits } from 'reka-ui'
import { computed } from 'vue'
const props = defineProps<ToggleGroupRootProps & { class?: string }>()
const emit = defineEmits<ToggleGroupRootEmits>()
const delegated = computed(() => { const { class: _c, ...rest } = props; return rest })
const forwarded = useForwardPropsEmits(delegated, emit)
</script>
<template>
  <ToggleGroupRoot v-bind="forwarded" :class="cn('inline-flex items-center justify-center gap-1', props.class)"><slot /></ToggleGroupRoot>
</template>
