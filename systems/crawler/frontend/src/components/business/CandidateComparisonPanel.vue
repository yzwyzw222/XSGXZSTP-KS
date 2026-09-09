<script setup lang="ts">
import { computed } from 'vue'
import type { CandidateComparison } from '@/types/api'

const props = defineProps<{ comparison: CandidateComparison }>()
const labels: Record<string, string> = {
  displayName: '当前名称', sourceTitle: '来源标题', doi: 'DOI', type: '类型', language: '语言',
  publicationDate: '出版日期', datePrecision: '来源日期精度', version: '数据版本',
  sourceCount: '来源数', authorCount: '署名作者数', openAlexId: 'OpenAlex ID', orcid: 'ORCID',
  achievementCount: '关联成果数', countryCode: '国家或地区', ror: 'ROR', issnL: 'ISSN-L',
}
const fields = computed(() => [...new Set([
  ...Object.keys(props.comparison.left), ...Object.keys(props.comparison.right),
])])
function display(value: unknown): string {
  if (value === null || value === undefined || value === '') return '未提供'
  return typeof value === 'object' ? JSON.stringify(value) : String(value)
}
</script>

<template>
  <div class="overflow-x-auto rounded-lg border border-border">
    <table class="w-full min-w-[480px] table-fixed text-left text-sm" aria-label="候选实体字段对照">
      <thead class="bg-muted/50">
        <tr>
          <th class="w-28 px-3 py-2 font-medium" scope="col">字段</th>
          <th class="px-3 py-2 font-medium" scope="col">左侧 #{{ comparison.leftEntityId }}</th>
          <th class="px-3 py-2 font-medium" scope="col">右侧 #{{ comparison.rightEntityId }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="field in fields" :key="field" class="border-t border-border align-top"
          :class="{ 'bg-warning/8': display(comparison.left[field]) !== display(comparison.right[field]) }">
          <th scope="row" class="px-3 py-2 font-normal text-muted-foreground">{{ labels[field] || field }}</th>
          <td class="break-words px-3 py-2">{{ display(comparison.left[field]) }}</td>
          <td class="break-words px-3 py-2">{{ display(comparison.right[field]) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
