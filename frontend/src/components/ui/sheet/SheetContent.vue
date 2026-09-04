<script setup lang="ts">
import { cn } from '@/lib/utils'
import { X } from 'lucide-vue-next'
import { DialogClose, DialogContent, type DialogContentEmits, type DialogContentProps, DialogOverlay, DialogPortal, useForwardPropsEmits } from 'reka-ui'
import { computed } from 'vue'

const sideMap = {
  top: 'inset-x-0 top-0 border-b data-[state=closed]:slide-out-to-top data-[state=open]:slide-in-from-top',
  bottom: 'inset-x-0 bottom-0 border-t data-[state=closed]:slide-out-to-bottom data-[state=open]:slide-in-from-bottom',
  left: 'inset-y-0 left-0 h-full w-3/4 border-r data-[state=closed]:slide-out-to-left data-[state=open]:slide-in-from-left sm:max-w-sm',
  right: 'inset-y-0 right-0 h-full w-3/4 border-l data-[state=closed]:slide-out-to-right data-[state=open]:slide-in-from-right sm:max-w-sm',
}

const props = withDefaults(defineProps<DialogContentProps & { class?: string; side?: keyof typeof sideMap }>(), { side: 'right' })
const emit = defineEmits<DialogContentEmits>()
const delegated = computed(() => { const { class: _c, side, ...rest } = props; return rest })
const forwarded = useForwardPropsEmits(delegated, emit)
</script>
<template>
  <DialogPortal>
    <DialogOverlay class="fixed inset-0 z-50 bg-overlay backdrop-blur-sm data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0" />
    <DialogContent
      v-bind="forwarded"
      :class="cn('fixed z-50 flex flex-col gap-4 bg-card shadow-xl transition ease-in-out data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:duration-200 data-[state=open]:duration-300', sideMap[props.side], props.class)"
    >
      <slot />
      <DialogClose class="absolute right-4 top-4 rounded-sm opacity-70 ring-offset-background transition-opacity hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-ring disabled:pointer-events-none">
        <X class="size-4" /><span class="sr-only">关闭</span>
      </DialogClose>
    </DialogContent>
  </DialogPortal>
</template>
