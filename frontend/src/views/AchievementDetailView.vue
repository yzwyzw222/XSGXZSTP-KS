<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  ElAlert,
  ElDescriptions,
  ElDescriptionsItem,
  ElTable,
  ElTableColumn,
  ElTag,
  vLoading,
} from 'element-plus'

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

function organizationNames(authorship: AchievementDetail['authorships'][number]): string {
  return authorship.organizations.map((item) => item.displayName).join('；') || '—'
}

onMounted(load)
</script>

<template>
  <section v-loading="loading" class="page-stack">
    <header class="page-heading">
      <div>
        <RouterLink class="eyebrow text-link" to="/catalog">← 返回成果目录</RouterLink>
        <h1>{{ detail?.summary.title || '成果详情' }}</h1>
        <p>成果编号 #{{ achievementId }} · 核对规范记录、作者署名、来源轨迹与字段状态。</p>
      </div>
    </header>

    <ElAlert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />

    <template v-if="detail">
      <div class="content-panel">
        <div class="section-title"><span class="eyebrow">NORMALIZED RECORD</span><h2>规范记录</h2></div>
        <ElDescriptions :column="3" border>
          <ElDescriptionsItem label="DOI">{{ detail.summary.doi || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="成果类型">{{ detail.summary.achievementType || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="发表日期">{{ detail.summary.publicationDate || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="主要期刊">{{ detail.summary.primaryVenue || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="语言">{{ detail.language || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="署名完整性">
            <ElTag :type="detail.authorshipsMayBeIncomplete ? 'warning' : 'success'" effect="plain">
              {{ detail.authorshipsMayBeIncomplete ? '可能不完整' : '已完整解析' }}
            </ElTag>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="主题" :span="3">
            <div v-if="detail.summary.topics.length" class="tag-list">
              <ElTag v-for="topic in detail.summary.topics" :key="topic" effect="plain">{{ topic }}</ElTag>
            </div>
            <span v-else>暂无主题</span>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="摘要" :span="3">{{ detail.abstractText || '暂无摘要' }}</ElDescriptionsItem>
        </ElDescriptions>
      </div>

      <div class="content-panel">
        <div class="section-title"><span class="eyebrow">REFERENCES</span><h2>引用标识</h2></div>
        <div v-if="detail.referencedWorkIds.length" class="tag-list">
          <ElTag v-for="workId in detail.referencedWorkIds" :key="workId" type="info" effect="plain">
            {{ workId }}
          </ElTag>
        </div>
        <ElAlert v-else title="暂无引用标识" type="info" :closable="false" />
      </div>

      <div class="content-panel">
        <div class="section-title"><span class="eyebrow">AUTHORSHIPS</span><h2>作者署名</h2></div>
        <ElTable :data="detail.authorships" empty-text="暂无作者信息">
          <ElTableColumn prop="position" label="顺序" width="80" />
          <ElTableColumn prop="displayName" label="作者" min-width="180" />
          <ElTableColumn prop="orcid" label="ORCID" min-width="160" />
          <ElTableColumn label="所属机构" min-width="260">
            <template #default="{ row }">
              {{ organizationNames(row as AchievementDetail['authorships'][number]) }}
            </template>
          </ElTableColumn>
        </ElTable>
      </div>

      <div class="two-column-panels">
        <div class="content-panel">
          <div class="section-title"><span class="eyebrow">PROVENANCE</span><h2>来源轨迹</h2></div>
          <ElTable :data="detail.sources" empty-text="暂无来源轨迹">
            <ElTableColumn prop="sourceCode" label="来源" width="110" />
            <ElTableColumn prop="externalRecordId" label="外部记录" min-width="180" />
            <ElTableColumn label="最后发现" width="170">
              <template #default="{ row }">{{ formatDateTime(row.lastSeenAt) }}</template>
            </ElTableColumn>
          </ElTable>
        </div>
        <div class="content-panel">
          <div class="section-title"><span class="eyebrow">FIELD LINEAGE</span><h2>字段状态</h2></div>
          <ElTable :data="detail.fields" empty-text="暂无字段状态">
            <ElTableColumn prop="fieldName" label="字段" min-width="130" />
            <ElTableColumn prop="sourceCode" label="来源" width="100" />
            <ElTableColumn label="覆盖" width="90">
              <template #default="{ row }">
                <ElTag :type="row.manualOverride ? 'warning' : 'info'" size="small" effect="plain">
                  {{ row.manualOverride ? '人工' : '自动' }}
                </ElTag>
              </template>
            </ElTableColumn>
          </ElTable>
        </div>
      </div>
    </template>
  </section>
</template>
