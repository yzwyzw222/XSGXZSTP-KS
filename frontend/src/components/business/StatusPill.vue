<script setup lang="ts">
import { cn } from '@/lib/utils'

const map: Record<string, { label: string; cls: string; dot: string }> = {
  UP: { label: '正常', cls: 'text-success bg-success/12 border-success/35', dot: 'bg-status-running' },
  SUCCEEDED: { label: '成功', cls: 'text-success bg-success/12 border-success/35', dot: 'bg-status-running' },
  SUCCESS: { label: '成功', cls: 'text-success bg-success/12 border-success/35', dot: 'bg-status-running' },
  ACTIVE: { label: '启用', cls: 'text-success bg-success/12 border-success/35', dot: 'bg-status-running' },
  RUNNING: { label: '运行中', cls: 'text-info bg-info/12 border-info/35', dot: 'bg-info' },
  PROCESSING: { label: '处理中', cls: 'text-info bg-info/12 border-info/35', dot: 'bg-info' },
  PENDING: { label: '待处理', cls: 'text-muted-foreground bg-muted border-border', dot: 'bg-status-idle' },
  IDLE: { label: '空闲', cls: 'text-muted-foreground bg-muted border-border', dot: 'bg-status-idle' },
  DEGRADED: { label: '降级', cls: 'text-warning bg-warning/12 border-warning/35', dot: 'bg-status-warning' },
  WARNING: { label: '警告', cls: 'text-warning bg-warning/12 border-warning/35', dot: 'bg-status-warning' },
  PAUSED: { label: '已暂停', cls: 'text-warning bg-warning/12 border-warning/35', dot: 'bg-status-warning' },
  UNKNOWN: { label: '未知', cls: 'text-warning bg-warning/12 border-warning/35', dot: 'bg-status-warning' },
  DOWN: { label: '异常', cls: 'text-destructive bg-destructive/12 border-destructive/35', dot: 'bg-status-error' },
  OUT_OF_SERVICE: { label: '停服', cls: 'text-destructive bg-destructive/12 border-destructive/35', dot: 'bg-status-error' },
  FAILED: { label: '失败', cls: 'text-destructive bg-destructive/12 border-destructive/35', dot: 'bg-status-error' },
  FAILURE: { label: '失败', cls: 'text-destructive bg-destructive/12 border-destructive/35', dot: 'bg-status-error' },
  DEAD: { label: '死信', cls: 'text-destructive bg-destructive/12 border-destructive/35', dot: 'bg-status-error' },
  CRITICAL: { label: '严重', cls: 'text-destructive bg-destructive/12 border-destructive/35', dot: 'bg-status-error' },
  DISABLED: { label: '停用', cls: 'text-muted-foreground bg-muted border-border', dot: 'bg-status-idle' },
  EXPIRED: { label: '已过期', cls: 'text-muted-foreground bg-muted border-border', dot: 'bg-status-idle' },
}
const props = withDefaults(defineProps<{
  status?: string | null
  label?: string
  pulse?: boolean
  class?: string
}>(), { status: '' })

function resolve(status?: string | null) {
  return map[(status ?? '').toUpperCase()] ?? { label: status || '—', cls: 'text-muted-foreground bg-muted border-border', dot: 'bg-status-idle' }
}
</script>
<template>
  <span
    :class="cn('inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-xs font-medium whitespace-nowrap', resolve(status).cls, props.class)"
    :aria-label="`${label ?? resolve(status).label}`"
  >
    <span
      :class="cn('size-1.5 rounded-full', resolve(status).dot, pulse && 'animate-status-pulse')"
      aria-hidden="true"
    />
    {{ label ?? resolve(status).label }}
  </span>
</template>
