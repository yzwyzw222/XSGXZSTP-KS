<script setup lang="ts">
import { ElAlert, ElTag, ElTabs, ElTabPane } from 'element-plus'
import { ArrowLeft, FileText } from 'lucide-vue-next'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { DataTable, LoadingSkeleton, PageHeader, PanelSection, StatusPill } from '@/components/business'
import ScholarlySourcePanel from '@/components/business/ScholarlySourcePanel.vue'
import ImportEvidencePanel from '@/components/business/ImportEvidencePanel.vue'
import { achievementTypeLabel } from '@/utils/filter-options'
import type { DataTableColumn } from '@/components/business/types'
import { toErrorMessage } from '@/services/api'
import { catalogApi } from '@/services/business'
import { useSessionStore } from '@/stores/session'
import type { AchievementDetail } from '@/types/api'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const sessionStore = useSessionStore()
const { hasPermission } = sessionStore
const loading = ref(false)
const errorMessage = ref('')
const detail = ref<AchievementDetail | null>(null)
const section = ref('record')
const achievementId = computed(() => Number(route.params.id))
let requestSequence = 0

async function load(): Promise<void> {
  const sequence = ++requestSequence
  detail.value = null
  loading.value = false
  if (!Number.isSafeInteger(achievementId.value) || achievementId.value < 1) {
    errorMessage.value = '成果编号无效'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await catalogApi.achievement(achievementId.value)
    if (sequence === requestSequence) detail.value = response
  } catch (error) {
    if (sequence === requestSequence) errorMessage.value = toErrorMessage(error)
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

type Authorship = AchievementDetail['authorships'][number]
type SourceRow = AchievementDetail['sources'][number]
type FieldRow = AchievementDetail['fields'][number]

function organizationNames(authorship: Authorship): string {
  return authorship.organizations.map((item) => item.displayName).join('；') || '—'
}

const authorColumns: DataTableColumn<Authorship>[] = [
  { accessorKey: 'position', header: '顺序', enableSorting: false, meta: { width: '70px' } },
  { accessorKey: 'displayName', header: '作者', enableSorting: false },
  { accessorKey: 'orcid', header: 'ORCID', enableSorting: false },
  { id: 'organizations', accessorFn: (row) => organizationNames(row), header: '所属机构', enableSorting: false },
]
const sourceColumns: DataTableColumn<SourceRow>[] = [
  { accessorKey: 'sourceCode', header: '来源', enableSorting: false, meta: { width: '100px' } },
  { accessorKey: 'externalRecordId', header: '外部记录', enableSorting: false },
  { id: 'lastSeen', accessorFn: (row) => formatDateTime(row.lastSeenAt), header: '最后发现', enableSorting: false },
]
const fieldColumns: DataTableColumn<FieldRow>[] = [
  { accessorKey: 'fieldName', header: '字段', enableSorting: false },
  { accessorKey: 'sourceCode', header: '来源', enableSorting: false, meta: { width: '90px' } },
  { id: 'override', accessorFn: (row) => (row.manualOverride ? '人工' : '自动'), header: '覆盖', enableSorting: false, meta: { width: '80px' } },
]

watch(achievementId, load, { immediate: true })
onBeforeUnmount(() => { ++requestSequence })
</script>

<template>
  <section class="page-stack">
    <PageHeader
      :title="detail?.summary.title || '成果详情'"
      :description="`成果编号 #${achievementId} · 核对规范记录、作者署名、来源轨迹与字段状态。`"
    >
      <template #actions>
        <RouterLink
          v-if="detail && hasPermission('GRAPH_READ')"
          :to="{ name: 'graph', query: { centerType: 'ACHIEVEMENT', centerId: String(detail.summary.id) } }"
          class="inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline"
        >查看关系图谱</RouterLink>
        <RouterLink
          :to="{ path: '/catalog', query: route.query }"
          class="inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline"
        >
          <ArrowLeft class="size-4" aria-hidden="true" />返回成果目录
        </RouterLink>
      </template>
    </PageHeader>

    <ElAlert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" show-icon />

    <LoadingSkeleton v-if="loading && !detail" variant="text" :rows="6" />

    <ElTabs v-if="detail" v-model="section" aria-label="成果详情内容">
      <ElTabPane name="record" label="规范记录" />
      <ElTabPane name="authors" label="署名与引用" />
      <ElTabPane name="sources" label="来源指标" />
      <ElTabPane name="provenance" label="来源与字段" />
      <ElTabPane name="import" label="知网导入记录" />
    </ElTabs>
    <div v-if="detail" class="achievement-layout workspace-fill">
      <PanelSection v-if="section === 'import'" title="信息表原始字段" subtitle="保留上传时的表头及内容，包含基金、卷期、页码、中图分类号等。" class="workspace-panel"><ImportEvidencePanel :achievement-id="achievementId" /></PanelSection>
      <!-- 规范记录 -->
      <PanelSection v-if="section === 'record'" title="规范记录" class="achievement-record workspace-panel">
        <template #actions><FileText class="size-4 text-muted-foreground" aria-hidden="true" /></template>
        <dl class="grid grid-cols-1 gap-x-6 gap-y-4 sm:grid-cols-2 lg:grid-cols-3">
          <div class="space-y-1"><dt class="text-xs text-muted-foreground">DOI</dt><dd class="mono-evidence text-sm">{{ detail.summary.doi || '—' }}</dd></div>
          <div class="space-y-1"><dt class="text-xs text-muted-foreground">成果类型</dt><dd class="text-sm">{{ achievementTypeLabel(detail.summary.achievementType ?? '') || '—' }}</dd></div>
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
              <ElTag v-for="topic in detail.summary.topics" :key="topic" size="small" type="info" effect="plain">{{ topic }}</ElTag>
              <span v-if="!detail.summary.topics.length" class="text-sm text-muted-foreground">暂无主题</span>
            </dd>
          </div>
          <div class="space-y-1.5 sm:col-span-2 lg:col-span-3">
            <dt class="text-xs text-muted-foreground">摘要</dt>
            <dd class="achievement-abstract">{{ detail.abstractText || '暂无摘要' }}</dd>
          </div>
        </dl>
      </PanelSection>

      <PanelSection v-if="section === 'sources'" title="来源学术指标与版本" subtitle="逐来源核对观测值与版本关系" class="achievement-sources workspace-panel">
        <ScholarlySourcePanel :sources="detail.sources" />
      </PanelSection>

      <!-- 引用标识 -->
      <PanelSection v-if="section === 'authors'" title="引用标识" class="achievement-references workspace-panel">
        <div v-if="detail.referencedWorkIds.length" class="flex flex-wrap gap-1.5">
          <ElTag v-for="workId in detail.referencedWorkIds" :key="workId" size="small" type="info" effect="plain" class="mono-evidence">{{ workId }}</ElTag>
        </div>
        <p v-else class="text-sm text-muted-foreground">暂无引用标识</p>
      </PanelSection>

      <!-- 作者署名 -->
      <PanelSection v-if="section === 'authors'" title="作者署名" class="achievement-authors workspace-panel">
        <DataTable fill
          :columns="authorColumns"
          :data="detail.authorships"
          :get-row-id="(row) => `${row.authorId}-${row.position}`"
          empty-text="暂无作者信息"
          dense
        />
      </PanelSection>

      <!-- 来源轨迹 + 字段状态 -->
      <div v-if="section === 'provenance'" class="achievement-provenance workspace-grid grid-cols-1 xl:grid-cols-2">
        <PanelSection title="来源轨迹" class="workspace-panel">
          <DataTable fill
            :columns="sourceColumns"
            :data="detail.sources"
            :get-row-id="(row) => String(row.sourceRecordId)"
            empty-text="暂无来源轨迹"
            dense
          />
        </PanelSection>
        <PanelSection title="字段状态" class="workspace-panel">
          <DataTable fill
            :columns="fieldColumns"
            :data="detail.fields"
            :get-row-id="(row) => row.fieldName"
            empty-text="暂无字段状态"
            dense
          >
            <template #cell-override="{ row }">
              <ElTag size="small" :type="row.manualOverride ? 'warning' : 'info'" effect="plain">
                {{ row.manualOverride ? '人工' : '自动' }}
              </ElTag>
            </template>
          </DataTable>
        </PanelSection>
      </div>
    </div>
  </section>
</template>

<style scoped>
.achievement-layout { display: flex; flex-direction: column; gap: var(--space-3); }
.achievement-abstract { max-width: 65ch; font-size: var(--font-size-md); line-height: 1.85; padding-top: var(--space-1); }
@media (min-width: 1280px) {
  .achievement-layout { flex-direction: row; }
}
</style>
