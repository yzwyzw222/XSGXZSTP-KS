<script setup lang="ts">
import { cn } from '@/lib/utils'
import { useVModel } from '@vueuse/core'

const props = withDefaults(defineProps<{
  modelValue?: string | number | null
  defaultValue?: string | number
  class?: string
  type?: string
  disabled?: boolean
  id?: string
  placeholder?: string
  autocomplete?: string
  maxlength?: number
  min?: number | string
  max?: number | string
  step?: number | string
  ariaLabel?: string
  invalid?: boolean
}>(), { modelValue: '', type: 'text' })

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | number | null): void
  (e: 'blur', ev: FocusEvent): void
  (e: 'keydown', ev: KeyboardEvent): void
}>()

const model = useVModel(props, 'modelValue', emit, { passive: true, defaultValue: props.defaultValue ?? '' })

/**
 * 始终向上抛出字符串：绕过 Vue 对 <input type="number"> 的自动数字转换
 * （vModelText 在 type==='number' 时会 looseToNumber），保证 v-model 契约可预测，
 * 避免调用方对数值字段执行 .trim() 等字符串方法时抛错。数值转换由调用方显式处理。
 */
function onInput(event: Event): void {
  model.value = (event.target as HTMLInputElement).value
}
</script>
<template>
  <input
    :value="model ?? ''"
    @input="onInput"
    :type="type"
    :disabled="disabled"
    :id="id"
    :placeholder="placeholder"
    :autocomplete="autocomplete"
    :maxlength="maxlength"
    :min="min"
    :max="max"
    :step="step"
    :aria-label="ariaLabel"
    :aria-invalid="invalid || undefined"
    data-slot="input"
    :class="cn(
      'flex h-9 w-full min-w-0 rounded-md border border-input bg-input-background px-3 py-1 text-sm shadow-sm transition-colors',
      'placeholder:text-muted-foreground selection:bg-primary selection:text-primary-foreground',
      'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/60 focus-visible:border-ring',
      'disabled:cursor-not-allowed disabled:opacity-50',
      'aria-[invalid=true]:border-destructive aria-[invalid=true]:ring-destructive/30',
      'file:inline-flex file:h-7 file:border-0 file:bg-transparent file:text-sm file:font-medium',
      props.class,
    )"
    @blur="emit('blur', $event)"
    @keydown="emit('keydown', $event)"
  />
</template>
