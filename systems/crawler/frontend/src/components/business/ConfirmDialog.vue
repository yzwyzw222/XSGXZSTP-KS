<script setup lang="ts">
import { ElButton, ElDialog } from 'element-plus'
import { useVModel } from '@vueuse/core'

import { cn } from '@/lib/utils'

const props = withDefaults(defineProps<{
  open?: boolean
  title: string
  description?: string
  confirmText?: string
  cancelText?: string
  destructive?: boolean
  loading?: boolean
  class?: string
}>(), { open: false, confirmText: '确认', cancelText: '取消', destructive: false, loading: false })

const emit = defineEmits<{ (e: 'update:open', v: boolean): void; (e: 'confirm'): void }>()
const isOpen = useVModel(props, 'open', emit)
</script>

<template>
  <ElDialog
    v-model="isOpen"
    :title="title"
    width="min(520px, calc(100vw - 32px))"
    align-center
    :close-on-click-modal="!loading"
    :close-on-press-escape="!loading"
    :show-close="!loading"
    append-to-body
    :class="cn('aacv-confirm-dialog', props.class)"
  >
    <div class="space-y-3">
      <p v-if="description" class="text-sm leading-relaxed text-muted-foreground">{{ description }}</p>
      <slot />
    </div>
    <template #footer>
      <div class="flex flex-wrap justify-end gap-2">
        <ElButton plain :disabled="loading" @click="isOpen = false">{{ cancelText }}</ElButton>
        <ElButton :type="destructive ? 'danger' : 'primary'" :loading="loading" @click="emit('confirm')">
          {{ confirmText }}
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>
