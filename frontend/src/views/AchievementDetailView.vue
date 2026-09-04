<script setup lang="ts">
import type { ColumnDef } from '@tanstack/vue-table'
import { ArrowLeft, FileText } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { DataTable, LoadingSkeleton, PageHeader, PanelSection, StatusPill } from '@/components/business'
import { Alert, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { toErrorMessage } from '@/services/api'
import { catalogApi } from '@/services/business'
import type { AchievementDetail } from '@/types/api'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const loading = ref(false)
const errorMessage = ref('')
const detail = ref<AchievementDetail | null>(null)
const achievementId = computed(() => Number(route.params.id))

async function load(): Promise<void> {
  if (!Number.isInteger(achievementId.value) || achievementId.value < 1) {
    errorMessage.value = '成果编号无效'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    detail.value = await catalogApi.achievement(achievementId.value)
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    loading.value = false
  }
}

type Authorship = AchievementDetail['authorships'][number]
type SourceRow = AchievementDetail['sources'][number]
type FieldRow = AchievementDetail['fields'][number]

function organizationNames(authorship: Authorship): string {
  return authorship.organizations.map((item) => item.displayName).join('；') || '—'
}

const authorColumns: ColumnDef<Authorship, any>[] = [
  { accessorKey: 'position', header: '顺序', enableSorting: false, meta: { width: '70px' } },
  { accessorKey: 'displayName', header: '作者', enableSorting: false },
  { accessorKey: 'orcid', header: 'ORCID', enableSorting: false },
  { id: 'organizations', accessorFn: (row) => organizationNames(row), header: '所属机构', enableSorting: false },
]
const sourceColumns: ColumnDef<SourceRow, any>[] = [
  { accessorKey: 'sourceCode', header: '来源', enableSorting: false, meta: { width: '100px' } },
  { accessorKey: 'externalRecordId', header: '外部记录', enableSorting: false },
  { id: 'lastSeen', accessorFn: (row) => formatDateTime(row.lastSeenAt), header: '最后发现', enableSorting: false },
]
const fieldColumns: ColumnDef<FieldRow, any>[] = [
  { accessorKey: 'fieldName', header: '字段', enableSorting: false },
  { accessorKey: 'sourceCode', header: '来源', enableSorting: false, meta: { width: '90px' } },
  { id: 'override', accessorFn: (row) => (row.manualOverride ? '人工' : '自动'), header: '覆盖', enableSorting: false, meta: { width: '80px' } },
]

onMounted(load)
</script>

<template>
  <section class="page-stack">
    <PageHeader
      :title="detail?.summary.title || '成果详情'"
      :description="`成果编号 #${achievementId} · 核对规范记录、作者署名、来源轨迹与字段状态。`"
    >
      <template #actions>
        <RouterLink
          to="/catalog"
          class="inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline"
        >
          <ArrowLeft class="size-4" />返回成果目录
        </RouterLink>
      </template>
    </PageHeader>

    <Alert v-if="errorMessage" variant="destructive"><AlertTitle>{{ errorMessage }}</AlertTitle></Alert>

    <LoadingSkeleton v-if="loading && !detail" variant="text" :rows="6" />

    <template v-if="detail">
      <!-- 规范记录 -->
      <PanelSection title="规范记录">
        <template #actions><FileText class="size-4 text-muted-foreground" aria-hidden="true" /></template>
        <dl class="grid grid-cols-1 gap-x-6 gap-y-4 sm:grid-cols-2 lg:grid-cols-3">
          <div class="space-y-1"><dt class="text-xs text-muted-foreground">DOI</dt><dd class="mono-evidence text-sm">{{ detail.summary.doi || '—' }}</dd></div>
          <div class="space-y-1"><dt class="text-xs text-muted-foreground">成果类型</dt><dd class="text-sm">{{ detail.summary.achievementType || '—' }}</dd></div>
          <div class="space-y-1"><dt class="text-xs text-muted-foreground">发表日期</dt><dd class="text-sm">{{ detail.summary.publicationDate || '—' }}</dd></div>
          <div class="space-y-1"><dt class="text-xs text-muted-foreground">主要期刊</dt><dd class="text-sm">{{ detail.summary.primaryVenue || '—' }}</dd></div>
          <div class="space-y-1"><dt class="text-xs text-muted-foreground">语言</dt><dd class="text-sm">{{ detail.language || '—' }}</dd></div>
          <div class="space-y-1">
            <dt class="text-xs text-muted-foreground">署名完整性</dt>
            <dd><StatusPill :status="detail.authorshipsMayBeIncomplete ? 'WARNING' : 'UP'" :label="detail.authorshipsMayBeIncomplete ? '可能不完整' : '已完整解析'" /></dd>
          </div>
          <div class="space-y-1.5 sm:col-span-2 lg:col-span-3">
            <dt class="text-xs text-muted-foreground">主题</dt>
            <dd class="flex flex-wrap gap-1.5">
              <Badge v-for="topic in detail.summary.topics" :key="topic" variant="subtle">{{ topic }}</Badge>
              <span v-if="!detail.summary.topics.length" class="text-sm text-muted-foreground">暂无主题</span>
            </dd>
          </div>
          <div class="space-y-1.5 sm:col-span-2 lg:col-span-3">
            <dt class="text-xs text-muted-foreground">摘要</dt>
            <dd class="rounded-lg bg-muted/40 p-3 text-sm leading-relaxed text-foreground/90">{{ detail.abstractText || '暂无摘要' }}</dd>
          </div>
        </dl>
      </PanelSection>

      <!-- 引用标识 -->
      <PanelSection title="引用标识">
        <div v-if="detail.referencedWorkIds.length" class="flex flex-wrap gap-1.5">
          <Badge v-for="workId in detail.referencedWorkIds" :key="workId" variant="info" class="mono-evidence">{{ workId }}</Badge>
        </div>
        <p v-else class="text-sm text-muted-foreground">暂无引用标识</p>
      </PanelSection>

      <!-- 作者署名 -->
      <PanelSection title="作者署名">
        <DataTable
          :columns="authorColumns"
          :data="detail.authorships"
          :get-row-id="(row) => `${row.authorId}-${row.position}`"
          empty-text="暂无作者信息"
          dense
        />
      </PanelSection>

      <!-- 来源轨迹 + 字段状态 -->
      <div class="grid grid-cols-1 gap-4 xl:grid-cols-2">
        <PanelSection title="来源轨迹">
          <DataTable
            :columns="sourceColumns"
            :data="detail.sources"
            :get-row-id="(row) => String(row.sourceRecordId)"
            empty-text="暂无来源轨迹"
            dense
          />
        </PanelSection>
        <PanelSection title="字段状态">
          <DataTable
            :columns="fieldColumns"
            :data="detail.fields"
            :get-row-id="(row) => row.fieldName"
            empty-text="暂无字段状态"
            dense
          >
            <template #cell-override="{ row }">
              <Badge :variant="row.manualOverride ? 'warning' : 'info'">{{ row.manualOverride ? '人工' : '自动' }}</Badge>
            </template>
          </DataTable>
        </PanelSection>
      </div>
    </template>
  </section>
</template>
