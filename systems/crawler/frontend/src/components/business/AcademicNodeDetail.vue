<script setup lang="ts">
import { ElAlert, ElButton } from 'element-plus'
import { onBeforeUnmount, ref, watch } from 'vue'
import AchievementPreview from '@/components/business/AchievementPreview.vue'
import CatalogEntityEvidencePanel from '@/components/business/CatalogEntityEvidencePanel.vue'
import { catalogApi } from '@/services/business'
import { toErrorMessage } from '@/services/api'
import { useSessionStore } from '@/stores/session'
import type { CatalogEntityEvidence, GraphNode } from '@/types/api'

const props = defineProps<{ node: GraphNode }>()
const emit = defineEmits<{ 'choose-author': [id: string] }>()
const session = useSessionStore()
const evidence = ref<CatalogEntityEvidence | null>(null)
const error = ref('')
const loading = ref(false)
let sequence = 0

async function load(): Promise<void> {
  const current = ++sequence
  evidence.value = null
  error.value = ''
  loading.value = false
  if (props.node.type !== 'AUTHOR' || !session.hasPermission('CATALOG_READ')) return
  loading.value = true
  try {
    const data = await catalogApi.entityEvidence('authors', Number(props.node.businessId))
    if (sequence === current) evidence.value = data
  } catch (cause) { if (sequence === current) error.value = toErrorMessage(cause) }
  finally { if (sequence === current) loading.value = false }
}
watch(() => props.node.id, load, { immediate: true })
onBeforeUnmount(() => { sequence++ })
</script>

<template>
  <section v-if="node.type === 'AUTHOR'" class="space-y-5 break-words" aria-label="作者详情">
    <h3 class="text-xl font-semibold">{{ node.label }}</h3>
    <dl class="grid grid-cols-[5em_minmax(0,1fr)] gap-3 text-sm">
      <dt class="text-muted-foreground">姓名</dt><dd>{{ node.label }}</dd>
      <dt class="text-muted-foreground">作者编号</dt><dd>{{ node.businessId }}</dd>
      <dt class="text-muted-foreground">ORCID</dt><dd>{{ node.properties.orcid || '未收录' }}</dd>
    </dl>
    <ElButton type="primary" @click="emit('choose-author', node.businessId)">以此作者查看图谱</ElButton>
    <p v-if="loading" role="status">正在读取作者资料…</p>
    <template v-if="error"><ElAlert :title="error" type="error" :closable="false" /><ElButton @click="load">重新加载作者资料</ElButton></template>
    <CatalogEntityEvidencePanel v-if="evidence" :evidence="evidence" />
    <p v-if="!session.hasPermission('CATALOG_READ')" class="text-sm text-muted-foreground">当前账号可查看图谱属性；更多作者资料需要目录读取权限。</p>
  </section>
  <AchievementPreview v-else-if="session.hasPermission('CATALOG_READ')" :id="Number(node.businessId)" />
  <section v-else class="space-y-4 break-words">
    <h3 class="text-lg font-semibold">{{ node.label }}</h3>
    <p>{{ node.properties.publicationDate || '日期未知' }}</p>
    <p class="whitespace-pre-wrap">{{ node.properties.abstractText || '暂未收录摘要' }}</p>
    <p class="text-sm text-muted-foreground">完整目录详情需要目录读取权限。</p>
  </section>
</template>
