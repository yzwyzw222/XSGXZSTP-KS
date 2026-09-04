<script setup lang="ts">
import { cn } from '@/lib/utils'
import { RadioGroupRoot, type RadioGroupRootEmits, type RadioGroupRootProps, useForwardPropsEmits } from 'reka-ui'
import { computed } from 'vue'
const props = withDefaults(defineProps<RadioGroupRootProps & { class?: string }>(), { orientation: 'vertical' })
const emit = defineEmits<RadioGroupRootEmits>()
const delegated = computed(() => { const { class: _c, ...rest } = props; return rest })
const forwarded = useForwardPropsEmits(delegated, emit)
</script>
<template>
  <RadioGroupRoot v-bind="forwarded" :class="cn('grid gap-2', props.class)"><slot /></RadioGroupRoot>
</template>
