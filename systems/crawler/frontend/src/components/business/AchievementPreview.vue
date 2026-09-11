<script setup lang="ts">
import { ElAlert, ElButton } from 'element-plus'
import { onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, type LocationQueryRaw } from 'vue-router'
import { catalogApi } from '@/services/business'
import { toErrorMessage } from '@/services/api'
import type { AchievementDetail } from '@/types/api'
import { formatDateTime } from '@/utils/format'
import { achievementTypeLabel } from '@/utils/filter-options'

const props = defineProps<{ id: number; returnQuery?: LocationQueryRaw }>()
const detail = ref<AchievementDetail | null>(null)
const loading = ref(false)
const error = ref('')
let sequence = 0
async function load(): Promise<void> {
  const current = ++sequence
  loading.value = true
  error.value = ''
  detail.value = null
  try {
    const data = await catalogApi.achievement(props.id)
    if (current === sequence) detail.value = data
  } catch (cause) { if (current === sequence) error.value = toErrorMessage(cause) }
  finally { if (current === sequence) loading.value = false }
}
watch(() => props.id, load, { immediate: true })
onBeforeUnmount(() => { sequence++ })
</script>
<template>
  <div class="achievement-preview" :aria-busy="loading">
    <p v-if="loading" role="status" class="text-muted-foreground">正在读取成果…</p>
    <template v-else-if="error"><ElAlert :title="error" type="error" :closable="false" /><ElButton class="mt-3" @click="load">重新加载</ElButton></template>
    <template v-else-if="detail">
      <span class="achievement-preview__eyebrow">{{ achievementTypeLabel(detail.summary.achievementType || '', '成果') }} · {{ detail.summary.publicationDate || '日期未知' }}</span>
      <h3>{{ detail.summary.title }}</h3>
      <p class="text-muted-foreground">{{ detail.summary.authors.join('；') || '暂无作者信息' }}</p>
      <dl><dt>DOI</dt><dd class="mono-evidence">{{ detail.summary.doi || '未提供' }}</dd><dt>期刊 / 来源</dt><dd>{{ detail.summary.primaryVenue || '未提供' }}</dd></dl>
      <h4>摘要</h4><p class="achievement-preview__abstract">{{ detail.abstractText || '当前成果尚无摘要。' }}</p>
      <h4>来源证据</h4>
      <p v-if="!detail.sources.length" class="text-muted-foreground">尚无来源证据</p>
      <article v-for="source in detail.sources" :key="source.sourceRecordId" class="achievement-preview__source">
        <strong>{{ source.sourceCode }}</strong><p class="mono-evidence">{{ source.externalRecordId }}</p>
        <p>最近观测 {{ formatDateTime(source.scholarlyMetadata?.observedAt ?? source.lastSeenAt) }}</p>
        <p>引用计数：{{ source.scholarlyMetadata?.citedByCount ?? '未知' }} · 开放获取：{{ source.scholarlyMetadata?.openAccess === true ? '是' : source.scholarlyMetadata?.openAccess === false ? '否' : '未知' }}</p>
      </article>
      <RouterLink :to="{ path: `/catalog/achievements/${id}`, query: returnQuery }" class="achievement-preview__full">查看完整详情与字段溯源 →</RouterLink>
    </template>
  </div>
</template>
<style scoped>
.achievement-preview { font-size: 14px; line-height: 1.7; overflow-wrap: anywhere; }
.achievement-preview__eyebrow { color: hsl(var(--primary)); font-size: 12px; }
.achievement-preview h3 { margin: 10px 0; font-size: 18px; font-weight: 600; line-height: 1.55; }
.achievement-preview h4 { border-top: 1px solid hsl(var(--border)); padding-top: 16px; margin: 20px 0 10px; font-weight: 600; color: #bceaff; }
.achievement-preview dl { display: grid; gap: 4px; margin-top: 20px; }
.achievement-preview dt { color: hsl(var(--muted-foreground)); font-size: 12px; }
.achievement-preview dd + dt { margin-top: 10px; }
.achievement-preview__abstract { white-space: pre-wrap; }
.achievement-preview__source { padding: 12px; margin-bottom: 10px; border: 1px solid hsl(var(--border)); background: hsl(var(--background) / .5); border-radius: 4px; }
.achievement-preview__source p { font-size: 12px; color: hsl(var(--muted-foreground)); margin-top: 5px; }
.achievement-preview__full { display: block; text-align: center; border: 1px solid hsl(var(--primary)); border-radius: 4px; padding: 10px; margin-top: 18px; color: hsl(var(--primary)); }
</style>
