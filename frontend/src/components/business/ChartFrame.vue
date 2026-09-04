<script setup lang="ts">
import EChartCanvas from '@/components/EChartCanvas.vue'
import type { EChartsCoreOption } from 'echarts/core'
const props = withDefaults(defineProps<{ option: EChartsCoreOption; label: string; height?: string }>(), { height: '300px' })
</script>
<template>
  <EChartCanvas :option="option" :label="label" :height="height" />
</template>
