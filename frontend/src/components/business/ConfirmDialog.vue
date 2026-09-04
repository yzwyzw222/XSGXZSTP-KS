<script setup lang="ts">
import { Button } from '@/components/ui/button'
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import { cn } from '@/lib/utils'
import { useVModel } from '@vueuse/core'

const props = withDefaults(defineProps<{
  open?: boolean
  title: string
  description?: string
  confirmText?: string
  cancelText?: string
  destructive?: boolean
  loading?: boolean
  requirePhrase?: string
  class?: string
}>(), { open: false, confirmText: '确认', cancelText: '取消', destructive: false, loading: false })
const emit = defineEmits<{ (e: 'update:open', v: boolean): void; (e: 'confirm'): void }>()
const isOpen = useVModel(props, 'open', emit)
</script>
<template>
  <AlertDialog v-model:open="isOpen">
    <AlertDialogTrigger v-if="$slots.trigger" as-child><slot name="trigger" /></AlertDialogTrigger>
    <AlertDialogContent :class="cn(props.class)">
      <AlertDialogHeader>
        <AlertDialogTitle>{{ title }}</AlertDialogTitle>
        <AlertDialogDescription v-if="description">{{ description }}</AlertDialogDescription>
        <slot />
      </AlertDialogHeader>
      <AlertDialogFooter>
        <AlertDialogCancel as-child><Button variant="outline" :disabled="loading">{{ cancelText }}</Button></AlertDialogCancel>
        <AlertDialogAction as-child>
          <Button :variant="destructive ? 'destructive' : 'default'" :loading="loading" @click.prevent="emit('confirm')">
            {{ confirmText }}
          </Button>
        </AlertDialogAction>
      </AlertDialogFooter>
    </AlertDialogContent>
  </AlertDialog>
</template>
