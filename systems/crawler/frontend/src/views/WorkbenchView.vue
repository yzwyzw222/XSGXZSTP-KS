<script setup lang="ts">
import { ElAlert, ElButton, ElInput } from 'element-plus'
import { ArrowRight, Search, FileInput } from 'lucide-vue-next'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { PageHeader, PanelSection } from '@/components/business'
import { authorGraphTarget, authorImportApi, importModeLabel, type ImportSummary } from '@/services/author-import'
import { toErrorMessage } from '@/services/api'
import { useSessionStore } from '@/stores/session'
import { formatDateTime } from '@/utils/format'

const { hasPermission } = useSessionStore()
const router = useRouter()
const keyword = ref('')
const imports = ref<ImportSummary[]>([])
const loading = ref(false)
const error = ref('')
let sequence = 0
async function load(): Promise<void> {
  if (!hasPermission('AUTHOR_IMPORT')) return
  const current = ++sequence
  loading.value = true; error.value = ''
  try { const rows = await authorImportApi.recent(); if (current === sequence) imports.value = rows.slice(0, 6) }
  catch (cause) { if (current === sequence) error.value = toErrorMessage(cause) }
  finally { if (current === sequence) loading.value = false }
}
function search(): void { void router.push({ path: '/catalog', query: keyword.value.trim() ? { title: keyword.value.trim() } : {} }) }
onMounted(load)
onBeforeUnmount(() => { sequence++ })
</script>

<template>
  <section class="page-stack workbench">
    <PageHeader title="学者研究工作台" description="导入学者信息表，查阅论文与专利，探索机构合作及硕博指导关系。">
      <template #actions><RouterLink class="workbench-link" to="/dashboard">打开可视化大屏 <ArrowRight :size="16" /></RouterLink></template>
    </PageHeader>
    <div class="workbench-columns">
      <form v-if="hasPermission('CATALOG_READ')" class="workbench-search" @submit.prevent="search">
        <label for="workbench-search">查找研究成果</label>
        <p>检索导入的论文、专利和学位论文，查看摘要及知网原始字段。</p>
        <div><ElInput id="workbench-search" v-model="keyword" :maxlength="200" placeholder="输入成果题名关键词" clearable /><ElButton native-type="submit" type="primary"><Search :size="16" />检索成果</ElButton></div>
      </form>
      <RouterLink v-if="hasPermission('AUTHOR_IMPORT')" to="/author-import" class="workbench-import"><FileInput :size="28" /><h2>导入作者信息表</h2><p>XLSX / XLS / CSV · 知网表头自动识别</p><span>预览记录并导入图谱 →</span></RouterLink>
    </div>
    <ElAlert v-if="error" :title="error" type="error" :closable="false"><ElButton link @click="load">重新读取</ElButton></ElAlert>
    <PanelSection v-if="hasPermission('AUTHOR_IMPORT')" title="最近导入的学者资料" subtitle="展示最近 6 次成功导入，可进入作者的两跳知识图谱。" :aria-busy="loading">
      <template #actions><ElButton link :loading="loading" @click="load">刷新</ElButton><RouterLink to="/author-import" class="text-primary">全部导入 →</RouterLink></template>
      <p v-if="loading && !imports.length" role="status" class="workbench-empty">正在读取导入记录…</p>
      <div v-else-if="!imports.length" class="workbench-empty"><p>还没有导入记录。</p><RouterLink to="/author-import" class="text-primary">从一位学者的信息表开始 →</RouterLink></div>
      <article v-for="batch in imports" :key="batch.id" class="workbench-record">
        <div><RouterLink :to="authorGraphTarget(batch.authorId)">{{ batch.scholarName }}</RouterLink><p>{{ importModeLabel(batch.importMode) }} · {{ batch.fileName }} · {{ formatDateTime(batch.createdAt) }}</p></div>
        <span>新增 {{ batch.importedCount }} 项 · 已存在 {{ batch.skippedCount }} 项</span><RouterLink :to="authorGraphTarget(batch.authorId)" class="text-primary">查看图谱 →</RouterLink>
      </article>
    </PanelSection>
  </section>
</template>

<style scoped>
.workbench { overflow: auto; }.workbench-link { display: inline-flex; align-items: center; gap: 8px; color: hsl(var(--primary)); }.workbench-columns { display: grid; grid-template-columns: minmax(0, 1.7fr) minmax(260px, 1fr); gap: 20px; }
.workbench-search,.workbench-import { padding: 28px; border: 1px solid hsl(var(--border)); background: hsl(var(--card)); border-radius: 8px; }.workbench-search label { font-size: 22px; font-weight: 600; }.workbench-search p,.workbench-import p { margin: 8px 0 20px; color: hsl(var(--muted-foreground)); font-size: 14px; line-height: 1.8; }.workbench-search > div { display: flex; gap: 12px; }.workbench-import { border-color: hsl(var(--primary) / .4); }.workbench-import svg,.workbench-import span { color: hsl(var(--primary)); }.workbench-import h2 { margin-top: 12px; font-size: 20px; }.workbench-import span { font-size: 14px; }
.workbench-record { padding: 18px 20px; border-bottom: 1px solid hsl(var(--border)); display: flex; align-items: center; justify-content: space-between; gap: 16px; }.workbench-record a { color: hsl(var(--primary)); overflow-wrap: anywhere; }.workbench-record p,.workbench-record > span { color: hsl(var(--muted-foreground)); font-size: 12px; margin-top: 6px; }.workbench-empty { padding: 24px; color: hsl(var(--muted-foreground)); font-size: 14px; line-height: 1.8; }
@media (max-width: 900px) { .workbench-columns { grid-template-columns: 1fr; } }@media (max-width: 600px) { .workbench-search { padding: 18px; }.workbench-search > div { flex-direction: column; }.workbench-record { align-items: flex-start; flex-wrap: wrap; } }
</style>
