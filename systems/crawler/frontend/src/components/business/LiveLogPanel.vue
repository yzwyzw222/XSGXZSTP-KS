<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'

import { cn } from '@/lib/utils'
import type { LogEntry } from './types'

const levelDot: Record<string, string> = {
  info: 'bg-primary',
  success: 'bg-status-running',
  warning: 'bg-status-warning',
  error: 'bg-status-error',
}

const props = withDefaults(defineProps<{
  entries: LogEntry[]
  maxHeight?: string
  class?: string
  emptyText?: string
  live?: boolean
  autoScroll?: boolean
}>(), { maxHeight: '260px', emptyText: '暂无日志', live: true, autoScroll: true })

const scrollEl = ref<HTMLElement | null>(null)

watch(() => props.entries.length, async () => {
  const current = scrollEl.value
  if (!props.autoScroll || !current || current.scrollHeight - current.scrollTop - current.clientHeight > 32) return
  await nextTick()
  const el = scrollEl.value
  // 只跟随正在阅读的末尾，不用滚动动画覆盖用户查看历史日志的位置。
  if (el) el.scrollTo({ top: el.scrollHeight, behavior: 'instant' })
})
</script>

<template>
  <div :class="cn('overflow-hidden rounded-lg border border-border bg-muted/30', props.class)">
    <div
      ref="scrollEl"
      :class="cn('overflow-y-auto p-2 font-mono text-xs')"
      :style="{ maxHeight: props.maxHeight }"
      :aria-live="live ? 'polite' : 'off'"
      aria-relevant="additions"
    >
      <p v-if="!entries.length" class="px-2 py-6 text-center text-muted-foreground">{{ emptyText }}</p>
      <div
        v-for="entry in entries"
        :key="entry.id"
        class="grid grid-cols-[auto_auto_minmax(0,1fr)] items-start gap-2 rounded px-2 py-1 hover:bg-accent/40"
      >
        <time v-if="entry.time" class="text-muted-foreground/70 tabular-nums">{{ entry.time }}</time>
        <span :class="cn('mt-1 size-1.5 shrink-0 rounded-full', levelDot[entry.level ?? 'info'])" aria-hidden="true" />
        <span class="break-words text-foreground/90">{{ entry.message }}</span>
      </div>
    </div>
  </div>
</template>
