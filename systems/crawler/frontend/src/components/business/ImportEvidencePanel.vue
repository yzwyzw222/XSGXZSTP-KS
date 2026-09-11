<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElAlert, ElButton } from 'element-plus'
import { authorImportApi, importModeLabel, type ImportEvidence } from '@/services/author-import'
import { toErrorMessage } from '@/services/api'

const props = defineProps<{ achievementId: number }>()
const records = ref<ImportEvidence[]>([])
const error = ref('')
const loading = ref(false)
let sequence = 0
async function load() {
  const current = ++sequence
  records.value = []; error.value = ''; loading.value = true
  try { const result = await authorImportApi.evidence(props.achievementId); if (current === sequence) records.value = result }
  catch (cause) { if (current === sequence) error.value = toErrorMessage(cause) }
  finally { if (current === sequence) loading.value = false }
}
watch(() => props.achievementId, load, { immediate: true })
onBeforeUnmount(() => { sequence++ })
</script>
<template>
  <div class="import-evidence" :aria-busy="loading">
    <ElAlert v-if="error" :title="error" type="error" :closable="false"><ElButton link @click="load">重试</ElButton></ElAlert>
    <p v-else-if="loading" role="status">正在读取知网导入记录…</p><p v-else-if="!records.length">该成果没有作者信息表导入记录。</p>
    <article v-for="record in records" :key="`${record.batchId}:${record.rowNumber}`"><h3>{{ record.fileName }} · {{ record.sheetName }} · 第 {{ record.rowNumber }} 行</h3><p>{{ record.scholarName }} · {{ importModeLabel(record.importMode) }}</p><dl><div v-for="(value, field) in record.originalColumns" :key="field"><dt>{{ field }}</dt><dd>{{ value || '未提供' }}</dd></div></dl></article>
  </div>
</template>
<style scoped>
.import-evidence { padding: 22px; overflow: auto; }.import-evidence > p { color: hsl(var(--muted-foreground)); font-size: 14px; }.import-evidence article + article { border-top: 1px solid hsl(var(--border)); padding-top: 24px; margin-top: 24px; }.import-evidence h3 { font-weight: 600; overflow-wrap: anywhere; }.import-evidence article > p { color: hsl(var(--primary)); margin: 10px 0 20px; font-size: 13px; }.import-evidence dl { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 18px; }.import-evidence dt { color: hsl(var(--muted-foreground)); font-size: 12px; }.import-evidence dd { font-size: 14px; margin-top: 6px; white-space: pre-wrap; overflow-wrap: anywhere; }@media(max-width:700px){.import-evidence dl{grid-template-columns:1fr;}}
</style>
