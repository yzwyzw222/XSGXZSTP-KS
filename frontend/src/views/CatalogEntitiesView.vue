<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElDrawer,
  ElInput,
  ElPagination,
  ElTable,
  ElTableColumn,
  vLoading,
} from 'element-plus'

import { toErrorMessage } from '@/services/api'
import { catalogApi } from '@/services/business'
import type {
  AchievementSummary,
  CatalogCollection,
  CatalogEntity,
  PageResponse,
} from '@/types/api'

const labels: Record<CatalogCollection, string> = {
  authors: '作者',
  organizations: '机构',
  venues: '期刊',
  topics: '主题',
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
    <header class="page-heading">
      <div>
        <RouterLink class="eyebrow text-link" to="/catalog">← 返回成果目录</RouterLink>
        <h1>{{ labels[collection] }}编目</h1>
        <p>按规范名称浏览实体，并追溯其关联成果。</p>
      </div>
      <div class="entity-links">
        <button
          v-for="(label, key) in labels"
          :key="key"
          :class="{ active: key === collection }"
          type="button"
          @click="changeCollection(key)"
        >{{ label }}</button>
      </div>
    </header>

    <div class="filter-panel toolbar">
      <ElInput v-model="name" class="search-input" :placeholder="'检索' + labels[collection] + '名称'" clearable @keyup.enter="load()" />
      <ElButton type="primary" :loading="loading" @click="load()">查询</ElButton>
    </div>
    <ElAlert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
    <div class="content-panel">
      <div class="toolbar"><strong>{{ labels[collection] }}列表</strong><span class="meta-line">共 {{ result.totalElements }} 条</span></div>
      <ElTable v-loading="loading" :data="result.items" empty-text="暂无编目实体">
        <ElTableColumn prop="displayName" label="规范名称" min-width="260" />
        <ElTableColumn prop="externalId" label="外部标识" min-width="220" />
        <ElTableColumn prop="entityType" label="类型" width="130" />
        <ElTableColumn prop="achievementCount" label="成果数" width="100" />
        <ElTableColumn label="操作" width="120">
          <template #default="{ row }">
            <ElButton link type="primary" @click="showRelated(row as CatalogEntity)">查看成果</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <div v-if="result.totalPages > 1" class="pagination-row">
        <ElPagination
          :current-page="result.page + 1"
          :page-size="result.size"
          :total="result.totalElements"
          layout="prev, pager, next"
          @current-change="(page: number) => load(page - 1)"
        />
      </div>
    </div>

    <ElDrawer v-model="drawerVisible" :title="selectedEntity?.displayName" size="520px">
      <div v-loading="relatedLoading">
        <div v-if="!relatedLoading && related.length === 0" class="empty-panel">
          <strong>暂无关联成果</strong><p>该实体目前未关联规范化成果。</p>
        </div>
        <article v-for="item in related" :key="item.id" class="related-record">
          <RouterLink :to="'/catalog/achievements/' + item.id">{{ item.title }}</RouterLink>
          <small>{{ item.publicationDate || '日期未知' }} · {{ item.primaryVenue || '期刊未知' }}</small>
        </article>
      </div>
    </ElDrawer>
  </section>
</template>
