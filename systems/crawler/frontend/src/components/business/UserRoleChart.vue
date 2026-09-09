<script setup lang="ts">
import { computed } from 'vue'
import { useChartTheme } from '@/composables/useChartTheme'
import ChartFrame from '@/components/business/ChartFrame.vue'
import type { UserStatistics } from '@/types/api'

const { palette } = useChartTheme()
const props = defineProps<{ statistics: UserStatistics }>()
const groups = computed(() => [
  { name: '管理员', value: props.statistics.admin },
  { name: '数据运营人员', value: props.statistics.dataOperator },
  { name: '科研用户', value: props.statistics.researcher },
])
const option = computed(() => ({
  tooltip: { trigger: 'item', renderMode: 'richText', formatter: '{b}：{c} 人（{d}%）' },
  aria: { enabled: true },
  series: [{ type: 'pie', radius: ['62%', '82%'], center: ['50%', '50%'],
    label: { show: false }, stillShowZeroSum: false, data: groups.value }],
}))
</script>

<template>
  <div class="relative">
    <ChartFrame :option="option" height="220px" label="按最高角色统计的用户人数分布" />
    <div class="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
      <span class="text-4xl font-semibold tabular-nums">{{ statistics.totalUsers }}</span>
      <span class="mt-1 text-xs text-muted-foreground">账号总数</span>
    </div>
  </div>
  <dl class="space-y-3">
    <div v-for="(group, index) in groups" :key="group.name" class="flex items-center gap-2 text-sm">
      <span class="size-2 rounded-full" :style="{ background: palette.series[index] }" aria-hidden="true" />
      <dt class="flex-1">{{ group.name }}</dt>
      <dd class="tabular-nums">{{ group.value }} 人 <span class="ml-2 text-xs text-muted-foreground">{{ statistics.totalUsers ? (group.value / statistics.totalUsers * 100).toFixed(1) + '%' : '--' }}</span></dd>
    </div>
  </dl>
  <p class="mt-4 text-xs leading-relaxed text-muted-foreground">包含所有状态账号。多角色按管理员、数据运营人员、科研用户顺序归类，每人计一次。</p>
</template>
