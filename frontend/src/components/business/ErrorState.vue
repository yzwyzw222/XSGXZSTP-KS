<script setup lang="ts">
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { AlertTriangle } from 'lucide-vue-next'
const props = defineProps<{ message: string; traceId?: string; class?: string; retryable?: boolean }>()
const emit = defineEmits<{ (e: 'retry'): void }>()
</script>
<template>
  <div :class="cn('flex flex-col items-center justify-center gap-3 px-6 py-12 text-center', props.class)" role="alert">
    <AlertTriangle class="size-9 text-destructive" aria-hidden="true" />
    <strong class="text-base font-semibold text-foreground">{{ message }}</strong>
    <code v-if="traceId" class="mono-evidence rounded bg-muted px-2 py-1 text-xs text-muted-foreground">Trace {{ traceId }}</code>
    <Button v-if="retryable" variant="outline" size="sm" @click="emit('retry')">重试</Button>
  </div>
</template>
