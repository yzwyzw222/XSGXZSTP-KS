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
    <dl class="coverage-list">
      <div v-for="item in items" :key="item.label" class="coverage-list__row">
        <dt class="text-xs text-muted-foreground">{{ item.label }}覆盖率</dt>
        <dd class="coverage-list__value"><span class="text-xs text-muted-foreground">{{ item.count }} / {{ total }}</span><strong class="text-sm font-semibold tabular-nums">{{ percent(item.count) }}</strong></dd>
      </div>
    </dl>
    <p class="text-xs leading-relaxed text-muted-foreground">分母为当前范围内的 {{ total }} 项规范成果。来源指标只要任一来源提供有效值即计入；零和明确的否定状态也算已提供。未知值不计入，不表示全球学术成果的采集覆盖率。</p>
    <p class="text-xs text-muted-foreground">{{ coverage.authorshipsMayBeIncompleteCount }} 项成果的作者署名可能不完整。观测值以来源最近一次采集为准，需历史复查保持更新。</p>
  </div>
</template>

<style scoped>
.coverage-list { display: grid; }
.coverage-list__row { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); padding-block: 9px; border-bottom: 1px solid hsl(var(--border) / .6); }
.coverage-list__row:first-child { padding-top: 0; }
.coverage-list__value { display: flex; align-items: baseline; gap: var(--space-3); }
.coverage-list__value strong { min-width: 3.5rem; text-align: right; }
</style>
