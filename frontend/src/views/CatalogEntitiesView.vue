<script setup lang="ts">
import { ElAlert, ElButton, ElDrawer, ElInput } from 'element-plus'
import { ArrowLeft, Search } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { DataTable, EmptyState, LoadingSkeleton, PageHeader, PanelSection } from '@/components/business'
import CatalogEntityEvidencePanel from '@/components/business/CatalogEntityEvidencePanel.vue'
import type { DataTableColumn } from '@/components/business/types'
import { toErrorMessage } from '@/services/api'
import { catalogApi } from '@/services/business'
import type { AchievementSummary, CatalogCollection, CatalogEntity, CatalogEntityEvidence, PageResponse } from '@/types/api'

const labels: Record<CatalogCollection, string> = {
  authors: '作者', organizations: '机构', venues: '期刊', topics: '主题',
}
const route = useRoute()
const router = useRouter()
const collection = computed(() => route.params.collection as CatalogCollection)
const name = ref('')
const loading = ref(false)
const errorMessage = ref('')
const result = ref<PageResponse<CatalogEntity>>({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const drawerVisible = ref(false)
const selectedEntity = ref<CatalogEntity | null>(null)
const relatedLoading = ref(false)
const related = ref<AchievementSummary[]>([])
const evidence = ref<CatalogEntityEvidence | null>(null)
const relatedError = ref('')
const relatedTotal = ref(0)
/** 列表与详情各自维护请求序号，切换集合或关闭抽屉后迟到响应作废。 */
let listSequence = 0
let detailSequence = 0

const columns: DataTableColumn<CatalogEntity>[] = [
  { accessorKey: 'displayName', header: '规范名称', enableSorting: false },
  { accessorKey: 'externalId', header: '外部标识', enableSorting: false },
  { accessorKey: 'entityType', header: '类型', enableSorting: false, meta: { width: '120px' } },
  { accessorKey: 'achievementCount', header: '成果数', enableSorting: false, meta: { width: '90px' } },
  { id: 'actions', header: '操作', enableSorting: false, meta: { width: '120px' } },
]

async function load(page = 0): Promise<void> {
  const sequence = ++listSequence
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await catalogApi.entities(collection.value, name.value.trim(), page, result.value.size)
    if (sequence === listSequence) result.value = response
  } catch (error) {
    if (sequence === listSequence) errorMessage.value = toErrorMessage(error)
  } finally {
    if (sequence === listSequence) loading.value = false
  }
}

async function showRelated(entity: CatalogEntity): Promise<void> {
  const sequence = ++detailSequence
  const kind = collection.value
  selectedEntity.value = entity
  related.value = []
  evidence.value = null
  relatedError.value = ''
  relatedTotal.value = 0
  drawerVisible.value = true
  relatedLoading.value = true
  try {
    const [response, observations] = await Promise.all([
      catalogApi.relatedAchievements(kind, entity.id),
      kind === 'authors' || kind === 'organizations' ? catalogApi.entityEvidence(kind, entity.id) : Promise.resolve(null),
    ])
    if (sequence !== detailSequence) return
    related.value = response.items
    relatedTotal.value = response.totalElements
    evidence.value = observations
  } catch (error) {
    if (sequence === detailSequence) relatedError.value = toErrorMessage(error)
  } finally {
    if (sequence === detailSequence) relatedLoading.value = false
  }
}

function changeCollection(value: string): void {
  void router.push('/catalog/' + value)
}

function actionLabel(): string {
  return collection.value === 'authors' || collection.value === 'organizations' ? '成果与证据' : '查看成果'
}

watch(collection, () => {
  drawerVisible.value = false
  ++detailSequence
  name.value = ''
  void load()
})
watch(drawerVisible, (visible) => { if (!visible) ++detailSequence }, { flush: 'sync' })
onBeforeUnmount(() => { ++listSequence; ++detailSequence })
onMounted(() => load())
</script>

<template>
  <section class="page-stack">
    <PageHeader
      :title="`${labels[collection]}编目`"
      description="按规范名称浏览实体，并追溯其关联成果。"
    >
      <template #actions>
        <RouterLink
          to="/catalog"
          class="inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline"
        >
          <ArrowLeft class="size-4" aria-hidden="true" />返回成果目录
        </RouterLink>
      </template>
    </PageHeader>

    <nav class="flex flex-wrap gap-1.5" aria-label="编目集合切换">
      <ElButton
        v-for="(label, key) in labels"
        :key="key"
        size="small"
        :type="key === collection ? 'primary' : 'default'"
        :plain="key !== collection"
        :aria-current="key === collection ? 'true' : undefined"
        @click="changeCollection(key)"
      >
        {{ label }}
      </ElButton>
    </nav>

    <div class="flex flex-col gap-2 sm:flex-row">
      <div class="relative flex-1 sm:max-w-sm">
        <Search
          class="pointer-events-none absolute left-3 top-1/2 z-10 size-4 -translate-y-1/2 text-muted-foreground"
          aria-hidden="true"
        />
        <ElInput
          v-model="name"
          class="aacv-search-input"
          :placeholder="`检索${labels[collection]}名称`"
          :aria-label="`检索${labels[collection]}名称`"
          clearable
          @keydown.enter="load()"
        />
      </div>
      <ElButton type="primary" :loading="loading" @click="load()">查询</ElButton>
    </div>

    <ElAlert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" show-icon />

    <PanelSection
      :title="`${labels[collection]}列表`"
      :subtitle="`共 ${result.totalElements.toLocaleString('zh-CN')} 条`"
    >
      <DataTable
        :columns="columns"
        :data="result.items"
        :loading="loading"
        :page="result.page"
        :size="result.size"
        :total="result.totalElements"
        empty-text="暂无编目实体"
        empty-description="调整名称关键字后重新检索。"
        :get-row-id="(row) => String(row.id)"
        @update:page="load"
      >
        <template #cell-actions="{ row }">
          <ElButton link type="primary" @click="showRelated(row)">{{ actionLabel() }}</ElButton>
        </template>
      </DataTable>
    </PanelSection>

    <!-- 窄屏下侧面板转为抽屉，证据与关联成果仍可完整阅读 -->
    <ElDrawer
      v-model="drawerVisible"
      direction="rtl"
      size="min(512px, 100vw)"
      class="aacv-drawer"
      :aria-label="`${selectedEntity?.displayName ?? '实体'}的关联成果与来源证据`"
    >
      <template #header>
        <div class="min-w-0 pr-6">
          <h2 class="truncate text-base font-semibold text-foreground">{{ selectedEntity?.displayName }}</h2>
          <p class="mt-0.5 truncate text-xs text-muted-foreground">
            {{ labels[collection] }} · 外部标识 {{ selectedEntity?.externalId || '—' }} · 关联成果 {{ selectedEntity?.achievementCount ?? 0 }}
          </p>
        </div>
      </template>

      <LoadingSkeleton v-if="relatedLoading" variant="text" :rows="4" />
      <ElAlert v-if="relatedError" type="error" :closable="false" :title="relatedError" show-icon />
      <CatalogEntityEvidencePanel v-if="evidence" :evidence="evidence" />
      <p v-if="!relatedLoading && relatedTotal > related.length" class="mb-3 text-xs text-muted-foreground">
        共 {{ relatedTotal.toLocaleString('zh-CN') }} 项关联成果，当前展示前 {{ related.length }} 项。
      </p>
      <EmptyState
        v-if="!relatedLoading && !relatedError && !related.length"
        title="暂无关联成果"
        description="该实体目前未关联规范化成果。"
      />
      <ul v-if="!relatedLoading && related.length" class="divide-y divide-border">
        <li v-for="item in related" :key="item.id" class="py-3">
          <RouterLink
            class="text-sm font-medium text-foreground transition-colors hover:text-primary"
            :to="`/catalog/achievements/${item.id}`"
          >
            {{ item.title }}
          </RouterLink>
          <p class="mt-1 text-xs text-muted-foreground">
            {{ item.publicationDate || '日期未知' }} · {{ item.primaryVenue || '期刊未知' }}
          </p>
        </li>
      </ul>
    </ElDrawer>
  </section>
</template>

<style scoped>
/* 前置检索图标占位，只调整内边距。 */
.aacv-search-input :deep(.el-input__wrapper) {
  padding-left: 34px;
}
</style>
