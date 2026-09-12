<script setup lang="ts">
import { ElAlert, ElButton, ElInput } from 'element-plus'
import { ArrowRight, BarChart3, BookOpen, Building2, FileInput, FileText, Network, RefreshCw, Search, UploadCloud, Users } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { PageHeader, PanelSection } from '@/components/business'
import { authorGraphTarget, authorImportApi, importModeLabel, type ImportSummary } from '@/services/author-import'
import { toErrorMessage } from '@/services/api'
import { analyticsApi } from '@/services/business'
import { useSessionStore } from '@/stores/session'
import type { AnalyticsOverview } from '@/types/api'
import { formatDateTime } from '@/utils/format'

const { hasPermission } = useSessionStore()
const router = useRouter()
const keyword = ref('')
const imports = ref<ImportSummary[]>([])
const loading = ref(false)
const error = ref('')
const overview = ref<AnalyticsOverview | null>(null)
const overviewLoading = ref(false)
const overviewError = ref('')
const campusBanner = `${import.meta.env.BASE_URL}images/campus-banner.png`
const metrics = computed(() => [
  { label: '成果总数', value: overview.value?.achievementCount, icon: FileText },
  { label: '作者总数', value: overview.value?.authorCount, icon: Users },
  { label: '机构总数', value: overview.value?.organizationCount, icon: Building2 },
  { label: '包含摘要', value: overview.value?.coverage?.withAbstractCount, icon: BookOpen },
])
let sequence = 0
let overviewSequence = 0
async function load(): Promise<void> {
  if (!hasPermission('AUTHOR_IMPORT')) return
  const current = ++sequence
  loading.value = true; error.value = ''
  try { const rows = await authorImportApi.recent(); if (current === sequence) imports.value = rows.slice(0, 6) }
  catch (cause) { if (current === sequence) error.value = toErrorMessage(cause) }
  finally { if (current === sequence) loading.value = false }
}
async function loadOverview(): Promise<void> {
  if (!hasPermission('ANALYTICS_READ')) return
  const current = ++overviewSequence
  overviewLoading.value = true
  overviewError.value = ''
  try {
    const result = await analyticsApi.overview({})
    if (current === overviewSequence) overview.value = result
  } catch (cause) {
    if (current === overviewSequence) overviewError.value = toErrorMessage(cause)
  } finally {
    if (current === overviewSequence) overviewLoading.value = false
  }
}
function search(): void { void router.push({ path: '/catalog', query: keyword.value.trim() ? { title: keyword.value.trim() } : {} }) }
onMounted(() => { void Promise.allSettled([load(), loadOverview()]) })
onBeforeUnmount(() => { sequence++; overviewSequence++ })
</script>

<template>
  <section class="page-stack workbench">
    <PageHeader title="学者研究工作台" description="导入学者信息表，查阅论文与专利，探索机构合作及硕博指导关系。">
      <template #actions><RouterLink class="workbench-link" to="/dashboard"><BarChart3 :size="18" />打开可视化大屏 <ArrowRight :size="16" /></RouterLink></template>
    </PageHeader>
    <div class="workbench-columns">
      <form v-if="hasPermission('CATALOG_READ')" class="workbench-search" @submit.prevent="search">
        <label for="workbench-search"><Search :size="28" />查找研究成果</label>
        <p>检索导入的论文、专利和学位论文，查看摘要及知网原始字段。</p>
        <div class="workbench-search__fields"><ElInput id="workbench-search" v-model="keyword" :maxlength="200" placeholder="输入成果题名关键词" clearable /><ElButton native-type="submit" type="primary"><Search :size="16" />检索成果</ElButton></div>
        <span class="workbench-search__hint">从题名开始检索，在成果目录中继续筛选作者、机构与主题。</span>
      </form>
      <RouterLink v-if="hasPermission('AUTHOR_IMPORT')" to="/author-import" class="workbench-import"><h2><FileInput :size="28" />导入作者信息表</h2><p>XLSX / XLS / CSV · 知网表头自动识别</p><div class="workbench-import__zone"><UploadCloud :size="28" /><span>选择学者文件<br /><small>核对识别结果后确认导入</small></span></div><span>预览记录并导入图谱 <ArrowRight :size="15" /></span></RouterLink>
      <section v-if="hasPermission('ANALYTICS_READ')" class="workbench-overview" :aria-busy="overviewLoading" aria-label="平台数据概览">
        <header><h2><BarChart3 :size="21" />平台数据概览</h2><RouterLink to="/analytics" aria-label="查看平台统计详情"><ArrowRight :size="18" /></RouterLink></header>
        <p v-if="overviewError" role="status" class="workbench-overview__error">{{ overviewError }}<ElButton link @click="loadOverview">重试</ElButton></p>
        <p v-if="overviewLoading && !overview" role="status" class="workbench-overview__loading">正在读取平台统计…</p>
        <div class="workbench-overview__metrics"><div v-for="metric in metrics" :key="metric.label"><component :is="metric.icon" :size="24" /><span>{{ metric.label }}<strong>{{ metric.value === undefined ? '—' : metric.value.toLocaleString('zh-CN') }}</strong></span></div></div>
      </section>
    </div>
    <section v-if="hasPermission('GRAPH_READ')" class="workbench-explore" aria-label="学术图谱入口">
      <img :src="campusBanner" alt="" aria-hidden="true" />
      <div><span>学术研究 · 从成果到关联</span><h2>沿着学者的研究成果，探索知识之间的联系。</h2><p>以真实署名、共同成果与导师关系为依据，查看学术关系与成果时间线。</p></div>
      <div class="workbench-explore__links"><RouterLink to="/academic-relations"><Network :size="20" />学术关系图谱<ArrowRight :size="15" /></RouterLink><RouterLink to="/academic-achievements"><BookOpen :size="20" />学术成果图谱<ArrowRight :size="15" /></RouterLink></div>
    </section>
    <ElAlert v-if="error" :title="error" type="error" :closable="false"><ElButton link @click="load">重新读取</ElButton></ElAlert>
    <PanelSection v-if="hasPermission('AUTHOR_IMPORT')" class="workbench-recent" title="最近导入的学者资料" subtitle="展示最近 6 次成功导入，可进入作者图谱查看论文、专利与硕博指导成果。" :aria-busy="loading">
      <template #actions><ElButton link :loading="loading" @click="load"><RefreshCw :size="15" />刷新</ElButton><RouterLink to="/author-import" class="text-primary">全部导入 →</RouterLink></template>
      <p v-if="loading && !imports.length" role="status" class="workbench-empty">正在读取导入记录…</p>
      <div v-else-if="!imports.length" class="workbench-empty"><p>还没有导入记录。</p><RouterLink to="/author-import" class="text-primary">从一位学者的信息表开始 →</RouterLink></div>
      <div v-else class="workbench-table-wrap"><table class="workbench-table"><thead><tr><th scope="col">序号</th><th scope="col">作者姓名</th><th scope="col">导入文件</th><th scope="col">导入类型</th><th scope="col">导入时间</th><th scope="col">成果统计</th><th scope="col">操作</th></tr></thead><tbody>
        <tr v-for="(batch, index) in imports" :key="batch.id" class="workbench-record"><td>{{ index + 1 }}</td><td><RouterLink :to="authorGraphTarget(batch.authorId)" class="workbench-author"><span aria-hidden="true"><Users :size="16" /></span>{{ batch.scholarName }}</RouterLink></td><td class="workbench-file">{{ batch.fileName }}</td><td>{{ importModeLabel(batch.importMode) }}</td><td><time>{{ formatDateTime(batch.createdAt) }}</time></td><td class="workbench-count">新增 {{ batch.importedCount }} 项 · 已存在 {{ batch.skippedCount }} 项</td><td><RouterLink :to="authorGraphTarget(batch.authorId)" class="workbench-table__action">查看图谱 <ArrowRight :size="14" /></RouterLink></td></tr>
      </tbody></table></div>
    </PanelSection>
  </section>
</template>

<style scoped>
.workbench { overflow: auto; gap: 12px; }
.workbench :deep(.page-header) { padding-block: 0 2px; }
.workbench-link { display: inline-flex; align-items: center; gap: 8px; padding: 11px 16px; border-radius: 8px; background: hsl(var(--primary) / .07); color: hsl(var(--primary)); font-size: 14px; }
.workbench-columns { display: grid; grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr) minmax(0, 1fr); gap: 16px; }
.workbench-columns:not(:has(.workbench-overview)) { grid-template-columns: minmax(0, 1.7fr) minmax(0, 1fr); }
.workbench-search,.workbench-import,.workbench-overview { min-width: 0; padding: 16px 20px; border: 1px solid hsl(var(--border)); background: hsl(var(--card)); border-radius: 12px; }
.workbench-search { background: linear-gradient(135deg, hsl(var(--primary) / .045), hsl(var(--card)) 80%); }
.workbench-search label,.workbench-import h2,.workbench-overview h2 { display: flex; align-items: center; gap: 12px; font-size: 19px; font-weight: 600; }
.workbench-search label svg,.workbench-import h2 svg,.workbench-overview h2 svg { flex-shrink: 0; color: hsl(var(--primary)); }
.workbench-search p,.workbench-import p { margin: 8px 0 14px; color: hsl(var(--muted-foreground)); font-size: 14px; line-height: 1.6; }
.workbench-search__fields { display: flex; gap: 10px; }
.workbench-search__fields :deep(.el-input__wrapper) { min-height: 42px; }
.workbench-search__fields :deep(.el-button) { height: 44px; }
.workbench-search__hint { display: block; margin-top: 12px; color: hsl(var(--muted-foreground)); font-size: 12px; line-height: 1.6; }
.workbench-import { display: flex; flex-direction: column; transition: border-color .15s; }
.workbench-import:hover { border-color: hsl(var(--primary) / .4); }
.workbench-import p { margin-bottom: 12px; }
.workbench-import__zone { display: flex; align-items: center; justify-content: center; gap: 12px; flex: 1; min-height: 62px; padding: 8px 12px; border: 1px dashed hsl(var(--primary) / .3); border-radius: 8px; background: hsl(var(--primary) / .035); color: hsl(var(--primary)); font-size: 14px; }
.workbench-import__zone small { color: hsl(var(--muted-foreground)); font-size: 12px; }
.workbench-import > span { display: inline-flex; align-items: center; gap: 8px; margin-top: 8px; color: hsl(var(--primary)); font-size: 14px; }
.workbench-overview { padding: 16px; }
.workbench-overview header { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 10px; }
.workbench-overview h2 { font-size: 17px; gap: 8px; }
.workbench-overview header > a { color: hsl(var(--primary)); }
.workbench-overview__metrics { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 8px; }
.workbench-overview__metrics > div { display: flex; align-items: center; gap: 10px; padding: 8px 10px; border-radius: 8px; background: #f0f6fd; }
.workbench-overview__metrics > div:nth-child(2) { background: #eff9f6; }
.workbench-overview__metrics > div:nth-child(3) { background: #fff8ec; }
.workbench-overview__metrics > div:nth-child(4) { background: #f4f2fb; }
.workbench-overview__metrics svg { flex-shrink: 0; color: #227ce5; }
.workbench-overview__metrics > div:nth-child(2) svg { color: #15977c; }
.workbench-overview__metrics > div:nth-child(3) svg { color: #b98527; }
.workbench-overview__metrics > div:nth-child(4) svg { color: #8061b3; }
.workbench-overview__metrics span { color: hsl(var(--muted-foreground)); font-size: 12px; }
.workbench-overview__metrics strong { display: block; margin-top: 3px; color: hsl(var(--foreground)); font-size: 23px; line-height: 1.2; font-variant-numeric: tabular-nums; }
.workbench-overview__error,.workbench-overview__loading { margin-bottom: 10px; color: hsl(var(--muted-foreground)); font-size: 12px; }
.workbench-overview__error { color: hsl(var(--destructive)); }
.workbench-explore { position: relative; display: flex; align-items: center; justify-content: space-between; overflow: hidden; gap: 24px; padding: 14px 24px; border: 1px solid hsl(var(--border)); border-radius: 12px; background: #f0f7fd; }
.workbench-explore > img { position: absolute; inset: 0 0 0 auto; width: 60%; height: 100%; object-fit: cover; object-position: center 65%; opacity: .28; pointer-events: none; }
.workbench-explore > div { position: relative; }
.workbench-explore > div > span { color: hsl(var(--primary)); font-size: 12px; }
.workbench-explore h2 { margin-top: 4px; font-size: 19px; font-weight: 600; line-height: 1.4; }
.workbench-explore p { margin-top: 5px; color: hsl(var(--muted-foreground)); font-size: 14px; line-height: 1.5; }
.workbench-explore__links { display: flex; flex-shrink: 0; gap: 10px; }
.workbench-explore__links a { display: flex; align-items: center; gap: 8px; padding: 14px; border: 1px solid hsl(var(--border)); border-radius: 8px; background: hsl(var(--card) / .9); color: hsl(var(--primary)); font-size: 14px; }
.workbench-recent { flex: 0 0 auto !important; min-height: 260px !important; }
.workbench-recent :deep(.panel-section__header) { padding: 10px 16px; }
.workbench-recent :deep(.panel-section__body) { padding: 10px 16px 12px; }
.workbench-table-wrap { max-width: 100%; overflow: auto; border: 1px solid hsl(var(--border)); border-radius: 8px; }
.workbench-table { width: 100%; border-collapse: collapse; text-align: left; font-size: 14px; line-height: 1.5; }
.workbench-table th { background: hsl(var(--table-header-bg)); color: hsl(var(--muted-foreground)); font-weight: 500; white-space: nowrap; }
.workbench-table th,.workbench-table td { padding: 6px 14px; border-bottom: 1px solid hsl(var(--border) / .7); }
.workbench-table th { padding-block: 9px; }
.workbench-table tbody tr:nth-child(even) { background: hsl(var(--primary) / .025); }
.workbench-table tbody tr:last-child td { border-bottom: 0; }
.workbench-table tbody tr:hover { background: hsl(var(--primary) / .045); }
.workbench-author { display: flex; align-items: center; gap: 9px; white-space: nowrap; font-weight: 500; }
.workbench-author > span { display: grid; place-items: center; width: 26px; height: 26px; border-radius: 50%; color: hsl(var(--primary)); background: hsl(var(--primary) / .08); }
.workbench-file { min-width: 160px; max-width: 280px; overflow-wrap: anywhere; }
.workbench-table time,.workbench-count { white-space: nowrap; color: hsl(var(--muted-foreground)); font-size: 12px; }
.workbench-table__action { display: inline-flex; align-items: center; gap: 6px; color: hsl(var(--primary)); white-space: nowrap; }
.workbench-empty { padding: 24px; color: hsl(var(--muted-foreground)); font-size: 14px; line-height: 1.8; }
@media (max-width: 1350px) { .workbench-columns { grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr); }.workbench-overview { grid-column: 1 / -1; }.workbench-overview__metrics { grid-template-columns: repeat(4, minmax(0,1fr)); }.workbench-explore { align-items: flex-start; flex-direction: column; gap: 16px; } }
@media (max-width: 800px) { .workbench-columns,.workbench-columns:not(:has(.workbench-overview)) { grid-template-columns: 1fr; }.workbench-explore h2 { font-size: 18px; }.workbench-explore__links { flex-wrap: wrap; }.workbench-search,.workbench-import { padding: 20px; } }
@media (max-width: 500px) { .workbench-search__fields { flex-direction: column; }.workbench-overview__metrics { grid-template-columns: repeat(2, minmax(0,1fr)); }.workbench-explore { padding: 20px; }.workbench-explore__links { flex-direction: column; width: 100%; }.workbench-explore__links a { justify-content: space-between; }.workbench-link { font-size: 13px; } }
</style>
