<script setup lang="ts">
import { ElAlert, ElButton } from 'element-plus'
import { Play, Trash2 } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { PageHeader, PanelSection } from '@/components/business'
import { useSessionStore } from '@/stores/session'
import { useGraphStore } from '@/stores/graph'
import type { GraphNodeType } from '@/types/api'
import { splitValues } from '@/utils/format'
import {
  readSavedQueries, writeSavedQueries, type SavedGraphQuery,
} from '@/utils/graph-query'

const router = useRouter()
const sessionStore = useSessionStore()
const graphStore = useGraphStore()
const userId = computed(() => sessionStore.currentUserId)
const saved = ref<SavedGraphQuery[]>([])
const corrupted = ref(false)

function refresh(): void {
  const state = readSavedQueries(userId.value)
  saved.value = state.queries
  corrupted.value = state.corrupted
}

function summary(item: SavedGraphQuery): string {
  const years = [item.filters.publicationYearFrom, item.filters.publicationYearTo].filter(Boolean)
  const parts = [
    `${nodeTypeLabel(item.filters.centerType)} #${item.filters.centerId}`,
    `深度 ${item.filters.depth} · 上限 ${item.filters.nodeLimit}`,
    years.length ? `年份 ${years.join(' – ')}` : '年份不限',
  ]
  const typeCount = item.filters.nodeTypes.length + item.filters.relationshipTypes.length
    + splitValues(item.filters.achievementTypes).length
  if (typeCount) parts.push(`类型过滤 ${typeCount} 项`)
  return parts.join(' · ')
}

function nodeTypeLabel(type: GraphNodeType): string {
  return {
    ACHIEVEMENT: '成果', AUTHOR: '作者', INSTITUTION: '机构', VENUE: '期刊/载体', TOPIC: '主题',
  }[type] ?? type
}

/** 交接筛选后跳转浏览页自动加载，避免用户重复配置。 */
function load(item: SavedGraphQuery): void {
  graphStore.stageQuery(item.filters)
  void router.push('/graph')
}

function remove(item: SavedGraphQuery): void {
  if (writeSavedQueries(userId.value, saved.value.filter((entry) => entry.name !== item.name))) refresh()
}

onMounted(refresh)
</script>

<template>
  <section class="page-stack">
    <PageHeader
      title="常用查询"
      description="保存在本机浏览器中的子图筛选组合，仅当前账号可见；加载后跳转节点与关系浏览页自动查询。"
    />

    <ElAlert
      v-if="corrupted"
      type="warning"
      :closable="false"
      title="本机常用查询无法读取，可重新保存；不影响图谱查询。"
      show-icon
    />

    <PanelSection title="已保存查询" :subtitle="`共 ${saved.length} 条 · 上限 10 条`">
      <p v-if="!saved.length" class="text-sm text-muted-foreground">
        暂无常用查询。在“节点与关系浏览”页配置筛选后，通过页首“常用查询”面板保存即可在此管理。
      </p>
      <ul v-else class="grid gap-3 lg:grid-cols-2">
        <li
          v-for="item in saved"
          :key="item.name"
          class="flex flex-col gap-3 rounded-lg border border-border bg-muted/20 p-4"
        >
          <div class="min-w-0">
            <h3 class="truncate text-sm font-semibold">{{ item.name }}</h3>
            <p class="mt-1 text-xs leading-relaxed text-muted-foreground">{{ summary(item) }}</p>
          </div>
          <div class="flex items-center gap-2">
            <ElButton type="primary" size="small" @click="load(item)">
              <Play class="mr-1 size-3.5" aria-hidden="true" />加载查询
            </ElButton>
            <ElButton text size="small" :aria-label="`移除${item.name}`" @click="remove(item)">
              <Trash2 class="mr-1 size-3.5" aria-hidden="true" />移除
            </ElButton>
          </div>
        </li>
      </ul>
    </PanelSection>
  </section>
</template>
