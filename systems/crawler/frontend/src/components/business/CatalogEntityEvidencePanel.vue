<script setup lang="ts">
import type { CatalogEntityEvidence } from '@/types/api'
import { formatDateTime } from '@/utils/format'
defineProps<{ evidence: CatalogEntityEvidence }>()
</script>

<template>
  <section class="mb-5 space-y-3 border-b border-border pb-5" :aria-label="evidence.entityType === 'AUTHOR' ? '机构署名观测' : '机构来源名称'">
    <template v-if="evidence.entityType === 'ORGANIZATION'">
      <h3 class="text-sm font-semibold">机构来源名称</h3>
      <p class="text-xs leading-relaxed text-muted-foreground">按可靠机构标识归属，记录采集时见过的名称。观测时间不代表更名时间；可使用别名检索机构。</p>
      <p v-if="!evidence.names.length" class="text-sm text-muted-foreground">尚无名称证据，后续复查采集将逐步补齐。</p>
      <ul v-else class="space-y-3">
        <li v-for="item in evidence.names" :key="`${item.sourceCode}:${item.displayName}`" class="rounded-md bg-muted/40 p-3">
          <p class="break-words text-sm font-medium">{{ item.displayName }}</p>
          <p class="mt-1 text-xs text-muted-foreground">{{ item.sourceCode }} · 首次 {{ formatDateTime(item.firstObservedAt) }} · 最近 {{ formatDateTime(item.lastObservedAt) }}</p>
        </li>
      </ul>
      <p v-if="evidence.namesTruncated" class="text-xs text-muted-foreground">仅显示最近观测的100项名称证据。</p>
    </template>
    <template v-else>
      <h3 class="text-sm font-semibold">机构署名观测</h3>
      <p class="text-xs leading-relaxed text-muted-foreground">区间取已收录论文的最早、最晚出版年份，不代表连续任职或当前单位；缺失日期不计入区间。</p>
      <p v-if="!evidence.affiliations.length" class="text-sm text-muted-foreground">尚无机构署名证据。</p>
      <ul v-else class="space-y-3">
        <li v-for="item in evidence.affiliations" :key="item.organizationId" class="rounded-md bg-muted/40 p-3">
          <p class="text-sm font-medium">{{ item.displayName || `机构 #${item.organizationId}` }}</p>
          <p class="mt-1 text-sm">署名观测区间：{{ item.firstPublicationYear === null ? '年份未知' : `${item.firstPublicationYear}—${item.lastPublicationYear}` }}</p>
          <p class="mt-1 text-xs text-muted-foreground">{{ item.achievementCount }} 项规范成果 · {{ item.datedAchievementCount }} 项具有出版日期</p>
        </li>
      </ul>
      <p v-if="evidence.affiliationsTruncated" class="text-xs text-muted-foreground">仅显示关联成果数最多的100家机构。</p>
    </template>
  </section>
</template>
