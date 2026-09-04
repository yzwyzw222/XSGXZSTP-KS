<script setup lang="ts">
import type { ColumnDef } from '@tanstack/vue-table'
import { ArrowLeft, Search } from 'lucide-vue-next'
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { DataTable, EmptyState, LoadingSkeleton, PageHeader, PanelSection } from '@/components/business'
import { Alert, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { toErrorMessage } from '@/services/api'
import { catalogApi } from '@/services/business'
import type { AchievementSummary, CatalogCollection, CatalogEntity, PageResponse } from '@/types/api'

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

const columns: ColumnDef<CatalogEntity, any>[] = [
  { accessorKey: 'displayName', header: '规范名称', enableSorting: false },
  { accessorKey: 'externalId', header: '外部标识', enableSorting: false },
  { accessorKey: 'entityType', header: '类型', enableSorting: false, meta: { width: '120px' } },
  { accessorKey: 'achievementCount', header: '成果数', enableSorting: false, meta: { width: '90px' } },
  { id: 'actions', header: '操作', enableSorting: false, meta: { width: '110px' } },
]

async function load(page = 0): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    result.value = await catalogApi.entities(collection.value, name.value.trim(), page, result.value.size)
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function showRelated(entity: CatalogEntity): Promise<void> {
  selectedEntity.value = entity
  drawerVisible.value = true
  relatedLoading.value = true
  try {
    related.value = (await catalogApi.relatedAchievements(collection.value, entity.id)).items
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    relatedLoading.value = false
  }
}

function changeCollection(value: string): void {
  void router.push('/catalog/' + value)
}

watch(collection, () => {
  name.value = ''
  void load()
})
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
          <Button variant="link" size="sm" class="h-auto p-0" @click="showRelated(row)">查看成果</Button>
        </template>
      </DataTable>
    </PanelSection>

    <Sheet v-model:open="drawerVisible">
      <SheetContent side="right" class="w-full gap-0 overflow-y-auto p-0 sm:max-w-lg">
        <SheetHeader class="border-b border-border">
          <SheetTitle class="truncate pr-8">{{ selectedEntity?.displayName }}</SheetTitle>
          <p class="text-xs text-muted-foreground">
            {{ labels[collection] }} · 外部标识 {{ selectedEntity?.externalId || '—' }} · 关联成果 {{ selectedEntity?.achievementCount ?? 0 }}
          </p>
        </SheetHeader>
        <div class="p-5">
          <LoadingSkeleton v-if="relatedLoading" variant="text" :rows="4" />
          <EmptyState
            v-else-if="!related.length"
            title="暂无关联成果"
            description="该实体目前未关联规范化成果。"
          />
          <ul v-else class="divide-y divide-border">
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
