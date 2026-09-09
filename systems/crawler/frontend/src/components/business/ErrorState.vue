<script setup lang="ts">
import { ElButton } from 'element-plus'
import { AlertTriangle } from 'lucide-vue-next'

import { cn } from '@/lib/utils'

const props = defineProps<{ message: string; traceId?: string; class?: string; retryable?: boolean }>()
const emit = defineEmits<{ (e: 'retry'): void }>()
</script>

<template>
  <!-- role="alert" 保证错误不仅靠颜色传达，读屏软件也会即时播报 -->
  <div :class="cn('feedback-error', props.class)" role="alert">
    <AlertTriangle class="size-5 shrink-0 text-destructive" aria-hidden="true" />
    <strong class="text-base font-semibold text-foreground">{{ message }}</strong>
    <code v-if="traceId" class="mono-evidence rounded bg-muted px-2 py-1 text-xs text-muted-foreground">Trace {{ traceId }}</code>
    <ElButton v-if="retryable" size="small" plain @click="emit('retry')">重试</ElButton>
  </div>
</template>
