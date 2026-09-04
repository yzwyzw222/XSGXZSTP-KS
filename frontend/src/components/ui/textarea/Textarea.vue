<script setup lang="ts">
import { cn } from '@/lib/utils'
import { useVModel } from '@vueuse/core'

const props = withDefaults(defineProps<{
  modelValue?: string | null
  defaultValue?: string
  class?: string
  rows?: number
  placeholder?: string
  disabled?: boolean
  id?: string
  ariaLabel?: string
}>(), { modelValue: '', rows: 3 })

const emit = defineEmits<{ (e: 'update:modelValue', value: string | null): void }>()
const model = useVModel(props, 'modelValue', emit, { passive: true, defaultValue: props.defaultValue ?? '' })
</script>
<template>
  <textarea
    v-model="model"
    :rows="rows"
    :placeholder="placeholder"
    :disabled="disabled"
    :id="id"
    :aria-label="ariaLabel"
    data-slot="textarea"
    :class="cn(
      'flex w-full rounded-md border border-input bg-input-background px-3 py-2 text-sm shadow-sm transition-colors',
      'placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/60 focus-visible:border-ring',
      'disabled:cursor-not-allowed disabled:opacity-50',
      props.class,
    )"
  />
</template>
