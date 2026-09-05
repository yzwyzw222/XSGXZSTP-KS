<script setup lang="ts">
import type { ColumnDef } from '@tanstack/vue-table'
import { ArrowLeft, Search } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { DataTable, EmptyState, LoadingSkeleton, PageHeader, PanelSection } from '@/components/business'
import CatalogEntityEvidencePanel from '@/components/business/CatalogEntityEvidencePanel.vue'
import { Alert, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
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
let listSequence = 0
let detailSequence = 0

const columns: ColumnDef<CatalogEntity, any>[] = [
  { accessorKey: 'displayName', header: '规范名称', enableSorting: false },
  { accessorKey: 'externalId', header: '外部标识', enableSorting: false },
  { accessorKey: 'entityType', header: '类型', enableSorting: false, meta: { width: '120px' } },
  { accessorKey: 'achievementCount', header: '成果数', enableSorting: false, meta: { width: '90px' } },
  { id: 'actions', header: '操作', enableSorting: false, meta: { width: '110px' } },
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
          <ArrowLeft class="size-4" />返回成果目录
        </RouterLink>
      </template>
    </PageHeader>

    <nav class="flex flex-wrap gap-1.5" aria-label="编目集合切换">
      <Button
        v-for="(label, key) in labels"
        :key="key"
        :variant="key === collection ? 'default' : 'outline'"
        size="sm"
        :aria-current="key === collection ? 'true' : undefined"
        @click="changeCollection(key)"
      >
        {{ label }}
      </Button>
    </nav>

    <div class="flex flex-col gap-2 sm:flex-row">
      <div class="relative flex-1 sm:max-w-sm">
        <Search class="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
        <Input
          v-model="name"
          class="pl-9"
          :placeholder="`检索${labels[collection]}名称`"
          :aria-label="`检索${labels[collection]}名称`"
          @keydown.enter="load()"
        />
      </div>
      <Button :loading="loading" @click="load()">查询</Button>
    </div>

    <Alert v-if="errorMessage" variant="destructive"><AlertTitle>{{ errorMessage }}</AlertTitle></Alert>

    <PanelSection :title="`${labels[collection]}列表`" :subtitle="`共 ${result.totalElements} 条`">
      <DataTable
        :columns="columns"
        :data="result.items"
        :loading="loading"
        :page="result.page"
        :size="result.size"
        :total="result.totalElements"
        empty-text="暂无编目实体"
        :get-row-id="(row) => String(row.id)"
        @update:page="load"
      >
        <template #cell-actions="{ row }">
          <Button variant="link" size="sm" class="h-auto p-0" @click="showRelated(row)">{{ collection === 'authors' || collection === 'organizations' ? '成果与证据' : '查看成果' }}</Button>
        </template>
      </DataTable>
    </PanelSection>

    <Sheet v-model:open="drawerVisible">
      <SheetContent side="right" class="w-full gap-0 overflow-y-auto p-0 sm:max-w-lg">
        <SheetHeader class="border-b border-border">
          <SheetTitle class="truncate pr-8">{{ selectedEntity?.displayName }}</SheetTitle>
          <SheetDescription class="sr-only">查看实体的关联成果与已采集的来源证据。</SheetDescription>
          <p class="text-xs text-muted-foreground">
            {{ labels[collection] }} · 外部标识 {{ selectedEntity?.externalId || '—' }} · 关联成果 {{ selectedEntity?.achievementCount ?? 0 }}
          </p>
        </SheetHeader>
        <div class="p-5">
          <LoadingSkeleton v-if="relatedLoading" variant="text" :rows="4" />
          <Alert v-if="relatedError" variant="destructive"><AlertTitle>{{ relatedError }}</AlertTitle></Alert>
          <CatalogEntityEvidencePanel v-if="evidence" :evidence="evidence" />
          <p v-if="!relatedLoading && relatedTotal > related.length" class="mb-3 text-xs text-muted-foreground">共 {{ relatedTotal }} 项关联成果，当前展示前 {{ related.length }} 项。</p>
          <EmptyState
            v-if="!relatedLoading && !relatedError && !related.length"
            title="暂无关联成果"
            description="该实体目前未关联规范化成果。"
          />
          <ul v-if="!relatedLoading && related.length" class="divide-y divide-border">
            <li v-for="item in related" :key="item.id" class="py-3">
              <RouterLink class="text-sm font-medium text-foreground hover:text-primary" :to="`/catalog/achievements/${item.id}`">
                {{ item.title }}
              </RouterLink>
              <p class="mt-1 text-xs text-muted-foreground">
                {{ item.publicationDate || '日期未知' }} · {{ item.primaryVenue || '期刊未知' }}
              </p>
            </li>
          </ul>
        </div>
      </SheetContent>
    </Sheet>
  </section>
</template>
