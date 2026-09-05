<script setup lang="ts">
import { computed } from 'vue'
import type { AnalyticsCoverage } from '@/types/api'
const props = defineProps<{ coverage: AnalyticsCoverage; total: number }>()
const items = computed(() => [
  { label: 'DOI', count: props.coverage.withDoiCount },
  { label: '出版年份', count: props.coverage.withPublicationYearCount },
  { label: '摘要', count: props.coverage.withAbstractCount },
  { label: '来源被引量', count: props.coverage.withCitationCount },
  { label: '开放状态', count: props.coverage.withOpenAccessStatusCount },
  { label: '撤稿状态', count: props.coverage.withRetractionStatusCount },
])
function percent(count: number): string {
  return props.total > 0 ? `${(count / props.total * 100).toFixed(1)}%` : '—'
}
</script>

<template>
  <div class="space-y-4">
    <p class="text-sm leading-relaxed text-muted-foreground">分母为当前范围内的 {{ total }} 项规范成果。来源指标只要任一来源提供有效值即计入；零和明确的否定状态也算已提供。未知值不计入，不表示全球学术成果的采集覆盖率。</p>
    <dl class="grid grid-cols-2 gap-3 lg:grid-cols-3">
      <div v-for="item in items" :key="item.label" class="rounded-lg border border-border p-3">
        <dt class="text-xs text-muted-foreground">{{ item.label }}覆盖率</dt>
        <dd class="mt-2 flex items-baseline justify-between gap-2"><strong class="text-lg tabular-nums">{{ percent(item.count) }}</strong><span class="text-xs text-muted-foreground">{{ item.count }} / {{ total }}</span></dd>
      </div>
    </dl>
    <p class="text-xs text-muted-foreground">{{ coverage.authorshipsMayBeIncompleteCount }} 项成果的作者署名可能不完整。观测值以来源最近一次采集为准，需历史复查保持更新。</p>
  </div>
</template>
