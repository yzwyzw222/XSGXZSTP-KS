<script setup lang="ts">
import { cn } from '@/lib/utils'
import { Check, Minus } from 'lucide-vue-next'
import { CheckboxIndicator, CheckboxRoot, type CheckboxRootEmits, type CheckboxRootProps, useForwardPropsEmits } from 'reka-ui'
import { computed } from 'vue'
const props = defineProps<CheckboxRootProps & { class?: string }>()
const emit = defineEmits<CheckboxRootEmits>()
const delegated = computed(() => { const { class: _c, ...rest } = props; return rest })
const forwarded = useForwardPropsEmits(delegated, emit)
</script>
<template>
  <CheckboxRoot
    v-bind="forwarded"
    :class="cn('peer size-4 shrink-0 rounded-sm border border-input shadow focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 data-[state=checked]:bg-primary data-[state=checked]:text-primary-foreground data-[state=checked]:border-primary data-[state=indeterminate]:bg-primary data-[state=indeterminate]:text-primary-foreground', props.class)"
  >
    <CheckboxIndicator class="flex h-full w-full items-center justify-center text-current">
      <slot name="icon" :state="props.modelValue">
        <Minus v-if="props.modelValue === 'indeterminate'" class="size-3.5" />
        <Check v-else class="size-3.5" />
      </slot>
    </CheckboxIndicator>
  </CheckboxRoot>
</template>
