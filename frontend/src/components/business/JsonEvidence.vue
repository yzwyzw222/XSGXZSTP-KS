<script setup lang="ts">
import { cn } from '@/lib/utils'
import { computed } from 'vue'
const props = withDefaults(defineProps<{ data: unknown; maxHeight?: string; class?: string; label?: string }>(), { maxHeight: '240px', label: '原始证据 JSON' })
const text = computed(() => {
  try { return JSON.stringify(props.data, null, 2) } catch { return String(props.data) }
})
</script>
<template>
  <pre
    :class="cn('mono-evidence overflow-auto rounded-md border border-border bg-muted/40 p-3 text-xs leading-relaxed text-muted-foreground', props.class)"
    :style="{ maxHeight: props.maxHeight }"
    :aria-label="label"
    tabindex="0"
  >{{ text }}</pre>
</template>
