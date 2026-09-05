<script setup lang="ts">
import { Badge } from '@/components/ui/badge'
import type { AchievementDetail } from '@/types/api'
import { formatDateTime } from '@/utils/format'

defineProps<{ sources: AchievementDetail['sources'] }>()

const relationLabels: Record<string, string> = {
  'is-preprint-of': '正式发表版本', 'has-preprint': '预印本版本',
  'is-version-of': '所属版本系列', 'has-version': '其他版本',
}

function state(value: boolean | null | undefined, yes: string, no: string): string {
  return value === true ? yes : value === false ? no : '未知'
}
</script>

<template>
  <div class="space-y-4">
    <p class="text-sm text-muted-foreground">被引量按来源独立展示，不相加，也不等同于本地图谱的引用数。状态以采集时的来源标记为准；旧快照需重新采集后补充。</p>
    <div class="grid gap-4 lg:grid-cols-2">
      <article v-for="source in sources" :key="source.sourceRecordId" class="min-w-0 space-y-3 rounded-lg border border-border p-4">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <h3 class="text-sm font-semibold">{{ source.sourceCode }}</h3>
          <Badge v-if="source.scholarlyMetadata?.retracted === true" variant="destructive">来源标记已撤稿</Badge>
        </div>
        <p class="break-all text-xs text-muted-foreground">{{ source.externalRecordId }}</p>
        <dl class="grid grid-cols-2 gap-3 text-sm">
          <div><dt class="text-xs text-muted-foreground">来源被引量</dt><dd class="mt-1 font-semibold tabular-nums">{{ source.scholarlyMetadata?.citedByCount ?? '未知' }}</dd></div>
          <div><dt class="text-xs text-muted-foreground">采集时间</dt><dd class="mt-1">{{ source.scholarlyMetadata ? formatDateTime(source.scholarlyMetadata.observedAt) : '尚未采集' }}</dd></div>
          <div><dt class="text-xs text-muted-foreground">撤稿状态</dt><dd class="mt-1">{{ state(source.scholarlyMetadata?.retracted, '已标记撤稿', '未标记撤稿') }}</dd></div>
          <div><dt class="text-xs text-muted-foreground">开放获取</dt><dd class="mt-1">{{ state(source.scholarlyMetadata?.openAccess, '开放', '非开放') }}<span v-if="source.scholarlyMetadata?.openAccessStatus"> · {{ source.scholarlyMetadata.openAccessStatus }}</span></dd></div>
        </dl>
        <div v-if="source.scholarlyMetadata?.versionRelations.length" class="space-y-2 border-t border-border pt-3">
          <p class="text-xs text-muted-foreground">来源明确标注的版本关系，保留独立成果记录。</p>
          <ul class="space-y-2">
            <li v-for="relation in source.scholarlyMetadata.versionRelations" :key="`${relation.relationType}:${relation.targetDoi}`" class="text-sm">
              <span>{{ relationLabels[relation.relationType] || '相关版本' }}：</span>
              <a :href="`https://doi.org/${encodeURIComponent(relation.targetDoi)}`" target="_blank" rel="noopener noreferrer" class="break-all text-primary underline">{{ relation.targetDoi }}</a>
            </li>
          </ul>
        </div>
      </article>
    </div>
    <p v-if="!sources.length" class="text-sm text-muted-foreground">暂无来源学术指标</p>
  </div>
</template>
