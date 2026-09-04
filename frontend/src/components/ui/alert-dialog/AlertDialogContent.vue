<script setup lang="ts">
import { cn } from '@/lib/utils'
import {
  AlertDialogContent, type AlertDialogContentEmits, type AlertDialogContentProps,
  AlertDialogOverlay, AlertDialogPortal, useForwardPropsEmits,
} from 'reka-ui'
import { computed } from 'vue'
const props = defineProps<AlertDialogContentProps & { class?: string }>()
const emit = defineEmits<AlertDialogContentEmits>()
const delegated = computed(() => { const { class: _c, ...rest } = props; return rest })
const forwarded = useForwardPropsEmits(delegated, emit)
</script>
<template>
  <AlertDialogPortal>
    <AlertDialogOverlay class="fixed inset-0 z-50 bg-overlay backdrop-blur-sm data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0" />
    <AlertDialogContent
      v-bind="forwarded"
      :class="cn('fixed left-1/2 top-1/2 z-50 grid w-full max-w-lg -translate-x-1/2 -translate-y-1/2 gap-4 border border-border bg-card p-6 shadow-xl duration-200 sm:rounded-xl data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95', props.class)"
    >
      <slot />
    </AlertDialogContent>
  </AlertDialogPortal>
</template>
