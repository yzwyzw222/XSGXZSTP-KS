<script setup lang="ts">
import { cn } from '@/lib/utils'
import { Circle } from 'lucide-vue-next'
import { RadioGroupIndicator, RadioGroupItem, type RadioGroupItemProps } from 'reka-ui'
import { computed } from 'vue'
const props = defineProps<RadioGroupItemProps & { class?: string }>()
const delegated = computed(() => { const { class: _c, ...rest } = props; return rest })
</script>
<template>
  <RadioGroupItem
    v-bind="delegated"
    :class="cn('aspect-square size-4 rounded-full border border-input text-primary shadow focus:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 data-[state=checked]:border-primary', props.class)"
  >
    <RadioGroupIndicator class="flex items-center justify-center">
      <Circle class="size-2 fill-current" />
    </RadioGroupIndicator>
  </RadioGroupItem>
</template>
