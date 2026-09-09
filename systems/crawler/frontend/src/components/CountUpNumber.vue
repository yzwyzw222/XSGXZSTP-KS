<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  value: number
  duration?: number
  suffix?: string
}>(), {
  duration: 600,
  suffix: '',
})

// 保留原组件入口，但指标始终展示接口终值，避免轮询时生成不存在的中间计数。
const formattedValue = computed(() =>
  Number.isFinite(props.value) ? `${Math.round(props.value).toLocaleString('zh-CN')}${props.suffix}` : '--',
)
</script>

<template>
  <span class="tabular-nums">{{ formattedValue }}</span>
</template>
