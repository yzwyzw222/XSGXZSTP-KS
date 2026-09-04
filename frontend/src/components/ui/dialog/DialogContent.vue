<script setup lang="ts">
import { cn } from '@/lib/utils'
import { X } from 'lucide-vue-next'
import {
  DialogClose, DialogContent, type DialogContentEmits, type DialogContentProps,
  DialogOverlay, DialogPortal, useForwardPropsEmits,
} from 'reka-ui'
import { computed } from 'vue'

const props = defineProps<DialogContentProps & { class?: string; hideClose?: boolean }>()
const emit = defineEmits<DialogContentEmits>()
const delegated = computed(() => {
  const { class: _c, hideClose, ...rest } = props
  return rest
})
const forwarded = useForwardPropsEmits(delegated, emit)
</script>
<template>
  <DialogPortal>
    <DialogOverlay
      class="fixed inset-0 z-50 bg-overlay backdrop-blur-sm data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0"
    />
    <DialogContent
      v-bind="forwarded"
      :class="cn(
        'fixed left-1/2 top-1/2 z-50 grid w-full max-w-lg -translate-x-1/2 -translate-y-1/2 gap-4 border border-border bg-card p-6 shadow-xl duration-200 sm:rounded-xl',
        'data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95',
        'max-h-[90vh] overflow-y-auto',
        props.class,
      )"
    >
      <slot />
      <DialogClose
        v-if="!hideClose"
        class="absolute right-4 top-4 rounded-sm opacity-70 ring-offset-background transition-opacity hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:pointer-events-none"
      >
        <X class="size-4" />
        <span class="sr-only">关闭</span>
      </DialogClose>
    </DialogContent>
  </DialogPortal>
</template>
