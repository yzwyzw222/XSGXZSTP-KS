<script setup lang="ts">
import { cn } from '@/lib/utils'
import { Primitive } from 'reka-ui'
import { computed } from 'vue'
import { buttonVariants } from './variants'

const props = withDefaults(
  defineProps<{
    variant?: 'default' | 'destructive' | 'success' | 'outline' | 'secondary' | 'ghost' | 'link' | 'subtle'
    size?: 'default' | 'sm' | 'lg' | 'icon' | 'icon-sm'
    as?: string | object
    asChild?: boolean
    class?: string
    disabled?: boolean
    type?: 'button' | 'submit' | 'reset'
    loading?: boolean
  }>(),
  {
    variant: 'default',
    size: 'default',
    as: 'button',
    asChild: false,
    type: 'button',
    loading: false,
    disabled: false,
  },
)

const emit = defineEmits<{ (e: 'click', event: MouseEvent): void }>()

const delegated = computed(() => ({
  disabled: props.disabled || props.loading,
}))

function onClick(event: MouseEvent) {
  if (props.disabled || props.loading) {
    event.preventDefault()
    event.stopPropagation()
    return
  }
  emit('click', event)
}
</script>

<template>
  <Primitive
    :as="as"
    :as-child="asChild"
    :type="as === 'button' && !asChild ? type : undefined"
    :class="cn(buttonVariants({ variant, size }), props.class)"
    v-bind="delegated"
    :data-loading="loading || undefined"
    @click="onClick"
  >
    <span
      v-if="loading"
      class="size-4 animate-spin rounded-full border-2 border-current border-t-transparent"
      aria-hidden="true"
    />
    <slot />
  </Primitive>
</template>
