<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { ElAlert, ElButton, ElInputNumber, ElOption, ElSelect, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { FileSpreadsheet, ArrowRight, CheckCircle2, Upload } from 'lucide-vue-next'
import { PageHeader, PanelSection } from '@/components/business'
import { authorGraphTarget, authorImportApi, importFields, importModeLabel, type ImportBundleOptions, type ImportBundlePreview, type ImportBundleSummary, type ImportSummary } from '@/services/author-import'
import { toErrorMessage } from '@/services/api'
import { achievementTypeLabel } from '@/utils/filter-options'
import { formatDateTime } from '@/utils/format'

const form = reactive<ImportBundleOptions>({ scholarName: '', files: [] })
const files = ref<File[]>([])
const bundle = ref<ImportBundlePreview | null>(null)
const activeFile = ref(0)
const currentSettings = computed(() => form.files[activeFile.value]!)
const preview = computed(() => bundle.value?.files[activeFile.value]?.preview ?? null)
const previewSettings = ref('')
const history = ref<ImportSummary[]>([])
const result = ref<ImportBundleSummary | null>(null)
const busy = ref<'preview' | 'confirm' | ''>('')
const error = ref('')
const historyError = ref('')
const historyLoading = ref(false)
const showMapping = ref(false)
const sheetNumber = computed({ get: () => (currentSettings.value?.sheetIndex ?? 0) + 1, set: (value: number) => { currentSettings.value.sheetIndex = value - 1; switchSheet() } })
let request = 0
let historyRequest = 0
let controller: AbortController | undefined
const settingsKey = () => JSON.stringify(form)
const fresh = computed(() => Boolean(bundle.value) && settingsKey() === previewSettings.value)
const canConfirm = computed(() => fresh.value && bundle.value!.canConfirm && !busy.value && !result.value)
const issuePage = ref(1)
const visibleIssues = computed(() => preview.value?.issues.slice((issuePage.value - 1) * 20, issuePage.value * 20) ?? [])

watch(form, () => { result.value = null }, { deep: true })
watch(activeFile, () => { issuePage.value = 1; showMapping.value = false })

function chooseFile(event: Event): void {
  const next = Array.from((event.target as HTMLInputElement).files ?? [])
  controller?.abort(); request++; busy.value = ''
  bundle.value = null; result.value = null; error.value = ''; form.scholarName = ''; form.files = []; activeFile.value = 0
  files.value = []
  if (!next.length) return
  if (next.length > 10) { error.value = '每次最多选择 10 份同一学者的信息表'; return }
  if (next.some(file => !/\.(xlsx|xls|csv)$/i.test(file.name))) { error.value = '请选择 XLSX、XLS 或 CSV 信息表'; return }
  if (next.some(file => !file.size) || next.reduce((sum, file) => sum + file.size, 0) > 10 * 1024 * 1024) { error.value = '请选择非空信息表，本批文件合计不能超过 10 MB'; return }
  files.value = next
  form.files = next.map(() => ({ sheetIndex: 0, headerRow: 1, mapping: {} }))
}

async function parse(): Promise<void> {
  if (!files.value.length) { error.value = '请选择同一学者的信息表'; return }
  controller?.abort(); controller = new AbortController()
  const current = ++request
  const options = JSON.parse(settingsKey()) as ImportBundleOptions
  const requestedSettings = settingsKey()
  busy.value = 'preview'; error.value = ''; result.value = null
  try {
    const response = await authorImportApi.previewFiles(files.value, options, controller.signal)
    if (current !== request || settingsKey() !== requestedSettings) return
    bundle.value = response
    response.files.forEach((file, index) => {
      form.files[index]!.mapping = Object.fromEntries(importFields.map(([field]) => [field, file.preview.mapping[field] ?? -1]))
    })
    previewSettings.value = settingsKey()
    issuePage.value = 1
  } catch (cause) { if (current === request) error.value = toErrorMessage(cause) }
  finally { if (current === request) busy.value = '' }
}

async function confirm(): Promise<void> {
  if (!canConfirm.value || !files.value.length || !bundle.value) return
  const current = ++request
  controller = new AbortController()
  busy.value = 'confirm'; error.value = ''
  try {
    const response = await authorImportApi.confirmFiles(files.value, JSON.parse(settingsKey()) as ImportBundleOptions, bundle.value.previewKey, controller.signal)
    if (current !== request) return
    result.value = response
    await loadHistory()
  } catch (cause) {
    if (current === request) error.value = `${toErrorMessage(cause)}。如果请求超时，可重试确认；相同文件及设置不会重复入库。`
  } finally { if (current === request) busy.value = '' }
}

async function loadHistory(): Promise<void> {
  const current = ++historyRequest
  historyLoading.value = true; historyError.value = ''
  try { const rows = await authorImportApi.recent(); if (current === historyRequest) history.value = rows }
  catch (cause) { if (current === historyRequest) historyError.value = toErrorMessage(cause) }
  finally { if (current === historyRequest) historyLoading.value = false }
}

function switchSheet(): void { currentSettings.value.mapping = {}; previewSettings.value = '' }
async function selectScholar(name: string): Promise<void> { form.scholarName = name; await parse() }
onMounted(loadHistory)
onBeforeUnmount(() => { request++; historyRequest++; controller?.abort() })
</script>

<template>
  <section class="page-stack author-import">
    <PageHeader title="作者导入" description="将知网导出的学者信息表转为可检索的成果与知识图谱。" />
    <ol class="import-steps" aria-label="导入流程">
      <li :class="{ active: !bundle }"><span>01</span>上传同一学者的文件</li><li :class="{ active: bundle && !result }"><span>02</span>核对自动识别结果</li><li :class="{ active: result }"><span>03</span>导入并探索图谱</li>
    </ol>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <div class="import-layout">
      <PanelSection title="学者资料文件" subtitle="同时选择本人署名成果表，以及按该学者导师姓名筛选导出的硕论、博论表。" :padded="false">
        <fieldset class="import-form" :disabled="Boolean(busy)" :inert="Boolean(busy)">
          <div class="import-file">
            <FileSpreadsheet :size="32" aria-hidden="true" /><strong>{{ files.length ? `已选择 ${files.length} 份文件` : '选择知网导出的信息表' }}</strong><p>XLSX / XLS / CSV · 最多 10 份 · 合计 10 MB、2000 条</p>
            <input type="file" multiple accept=".xlsx,.xls,.csv" aria-label="选择信息表" @change="chooseFile" />
            <span v-for="(file, index) in files" :key="index">{{ file.name }}</span>
          </div>
          <div v-if="files.length > 1" class="import-field"><label>查看文件与设置</label><ElSelect v-model="activeFile" aria-label="查看文件"><ElOption v-for="(file, index) in files" :key="index" :label="file.name" :value="index" /></ElSelect></div>
          <div v-if="files.length" class="import-row">
            <div v-if="preview && preview.sheets.length > 1" class="import-field"><label>工作表</label><ElSelect v-model="currentSettings.sheetIndex" aria-label="工作表" @change="switchSheet"><ElOption v-for="(sheet, index) in preview.sheets" :key="index" :label="sheet" :value="index" /></ElSelect></div>
            <div v-else class="import-field"><label>工作表序号</label><ElInputNumber v-model="sheetNumber" aria-label="工作表序号" :min="1" :max="20" :precision="0" /><small>从 1 开始；首张表为说明页时可选择后续工作表。</small></div>
            <div class="import-field"><label>表头所在行</label><ElInputNumber v-model="currentSettings.headerRow" aria-label="表头所在行" :min="1" :max="20" :precision="0" /></div>
          </div>
          <ElButton type="primary" :loading="busy === 'preview'" :disabled="busy === 'confirm'" @click="parse"><Upload :size="16" />{{ preview ? '重新解析预览' : '解析并预览' }}</ElButton>
        </fieldset>
      </PanelSection>
      <aside class="import-guide">
        <span class="import-guide__eyebrow">CNKI · 学者资料</span><h2>上传一组文件，<br />连接同一学者的研究成果。</h2>
        <p>自动识别你提供的知网表头，并保留全部原始列，便于核对基金、卷期、页码及分类号。</p>
        <ul><li><CheckCircle2 :size="16" />作者、论文与专利</li><li><CheckCircle2 :size="16" />机构、期刊与研究关键词</li><li><CheckCircle2 :size="16" />硕士、博士论文指导关系</li><li><CheckCircle2 :size="16" />摘要、DOI 与知网原文链接</li></ul>
        <p class="import-guide__note">所有问题行修正后才可确认。重复成果保留原记录；没有提供的信息不会自动补写。</p>
      </aside>
    </div>
    <PanelSection v-if="bundle" title="自动识别结果" :subtitle="`本批共 ${bundle.totalRows} 条，校验通过 ${bundle.validRows} 条。`">
      <p class="import-identity">学者：<strong>{{ bundle.scholarName || '待确定' }}</strong></p>
      <div v-if="bundle.candidates.length > 1" class="import-candidates" aria-label="文件中的学者候选"><ElButton v-for="name in bundle.candidates" :key="name" :type="bundle.scholarName === name ? 'primary' : 'default'" :disabled="Boolean(busy)" @click="selectScholar(name)">选择 {{ name }}</ElButton></div>
      <p class="import-identity">表内机构：{{ bundle.organizations.join('；') || '未提供' }}</p>
      <p v-for="message in bundle.messages" :key="message" class="import-explanation">{{ message }}</p>
      <ul class="import-file-summary"><li v-for="(item, index) in bundle.files" :key="index"><ElButton link :disabled="Boolean(busy)" @click="activeFile = index">{{ item.fileName }}</ElButton><span>{{ item.modes.map(importModeLabel).join('、') }} · {{ item.preview.totalRows }} 条</span><ElTag :type="item.preview.validRows === item.preview.totalRows ? 'success' : 'danger'">{{ item.preview.validRows === item.preview.totalRows ? '校验通过' : '需修正' }}</ElTag></li></ul>
    </PanelSection>
    <PanelSection v-if="preview" title="解析预览" :subtitle="`共 ${preview.totalRows} 条，校验通过 ${preview.validRows} 条；表格展示前 20 条。`" :padded="false">
      <template #actions><ElButton link @click="showMapping = !showMapping">{{ showMapping ? '收起字段映射' : '调整字段映射' }}</ElButton></template>
      <ElAlert v-if="!fresh" title="文件设置或字段映射已改变，请重新解析预览。" type="warning" :closable="false" />
      <div v-if="showMapping" class="import-mapping">
        <div v-for="[field, label] in importFields" :key="field" class="import-field"><label>{{ label }}</label><ElSelect v-model="currentSettings.mapping[field]" :aria-label="`映射${label}`" :disabled="Boolean(busy)"><ElOption label="不映射" :value="-1" /><ElOption v-for="(header, index) in preview.headers" :key="index" :label="`${index + 1}. ${header || '空列名'}`" :value="index" /></ElSelect></div>
      </div>
      <ElTable :data="preview.rows" class="import-table" max-height="440" row-key="rowNumber" aria-label="解析记录预览">
        <ElTableColumn prop="rowNumber" label="行" width="60" /><ElTableColumn prop="title" label="题名" min-width="260" show-overflow-tooltip />
        <ElTableColumn label="类型" width="130"><template #default="{ row }">{{ achievementTypeLabel(row.type) }}</template></ElTableColumn>
        <ElTableColumn label="作者" min-width="160"><template #default="{ row }">{{ row.authors.join('；') }}</template></ElTableColumn>
        <ElTableColumn label="机构" min-width="160" show-overflow-tooltip><template #default="{ row }">{{ row.organizations.join('；') }}</template></ElTableColumn>
        <ElTableColumn prop="publicationDate" label="日期" width="120" />
        <ElTableColumn label="摘要" min-width="160" show-overflow-tooltip><template #default="{ row }">{{ row.abstractText || '未提供' }}</template></ElTableColumn>
        <ElTableColumn label="校验" width="100"><template #default="{ row }"><ElTag :type="row.errors.length ? 'danger' : row.warnings.length ? 'warning' : 'success'">{{ row.errors.length ? '需修正' : row.warnings.length ? '有提示' : '通过' }}</ElTag></template></ElTableColumn>
      </ElTable>
      <div v-if="preview.issues.length" class="import-issues"><h3>问题与提示 · {{ preview.issues.length }} 行</h3><p v-for="issue in visibleIssues" :key="issue.rowNumber"><strong>第 {{ issue.rowNumber }} 行</strong><span v-for="message in issue.errors" :key="message" class="text-destructive">{{ message }}</span><span v-for="message in issue.warnings" :key="message">{{ message }}</span></p><div v-if="preview.issues.length > 20"><ElButton :disabled="issuePage === 1" @click="issuePage--">上一页</ElButton><span>{{ issuePage }} / {{ Math.ceil(preview.issues.length / 20) }}</span><ElButton :disabled="issuePage * 20 >= preview.issues.length" @click="issuePage++">下一页</ElButton></div></div>
      <div class="import-confirm"><p>确认本批文件属于同一学者，硕博文件按其导师姓名筛选导出。所有文件校验通过后一起保存；任一文件失败则整批回滚。</p><ElButton type="primary" :disabled="!canConfirm" :loading="busy === 'confirm'" @click="confirm">确认导入 {{ bundle!.totalRows }} 条 <ArrowRight :size="16" /></ElButton></div>
    </PanelSection>
    <section v-if="result" class="import-success" role="status"><CheckCircle2 :size="28" /><div><h2>导入已完成</h2><p>新增 {{ result.importedCount }} 项成果，补充关系 {{ result.linkedCount }} 项，已存在 {{ result.skippedCount }} 项。图谱正在后台同步，可稍后刷新查看。</p><RouterLink :to="authorGraphTarget(result.authorId)">查看 {{ result.scholarName }} 的知识图谱 →</RouterLink></div></section>
    <PanelSection title="最近导入" subtitle="最近 20 次成功导入，可继续查看对应学者图谱。"><template #actions><ElButton link :loading="historyLoading" @click="loadHistory">刷新</ElButton></template>
      <ElAlert v-if="historyError" :title="historyError" type="error" :closable="false" />
      <ElTable :data="history" aria-label="最近导入记录" empty-text="尚无导入记录" row-key="id"><ElTableColumn prop="scholarName" label="学者" min-width="110" /><ElTableColumn prop="fileName" label="文件" min-width="200" show-overflow-tooltip /><ElTableColumn label="类型" min-width="120"><template #default="{ row }">{{ importModeLabel(row.importMode) }}</template></ElTableColumn><ElTableColumn prop="importedCount" label="新增" width="80" /><ElTableColumn label="时间" min-width="170"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></ElTableColumn><ElTableColumn label="操作" width="100"><template #default="{ row }"><RouterLink class="text-primary" :to="authorGraphTarget(row.authorId)">查看图谱</RouterLink></template></ElTableColumn></ElTable>
    </PanelSection>
  </section>
</template>

<style scoped>
.author-import > .panel-section { flex: none; min-height: auto; overflow: visible; }
.author-import :deep(.panel-section__body) { display: block; flex: none; min-height: auto; overflow: visible; }
.author-import { overflow: auto; padding-bottom: 32px; }
.import-identity { margin-bottom: 12px; line-height: 1.8; overflow-wrap: anywhere; }.import-explanation { font-size: 13px; line-height: 1.8; color: hsl(var(--muted-foreground)); }.import-candidates { display: flex; flex-wrap: wrap; gap: 8px; margin: 12px 0; }.import-candidates .el-button { margin-left: 0; }.import-file-summary { list-style: none; padding: 0; margin-top: 18px; display: grid; gap: 12px; }.import-file-summary li { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; font-size: 13px; }.import-file-summary .el-button { white-space: normal; overflow-wrap: anywhere; text-align: left; }
.import-steps { display: flex; list-style: none; gap: 32px; border-bottom: 1px solid hsl(var(--border)); padding: 0 0 18px; color: hsl(var(--muted-foreground)); }
.import-steps li { display: flex; gap: 10px; align-items: center; font-size: 14px; }.import-steps span { font: 18px Georgia, serif; }.import-steps .active { color: hsl(var(--primary)); }
.import-layout { display: grid; grid-template-columns: minmax(380px, 1.8fr) minmax(260px, 1fr); gap: 24px; }
.import-form { border: 0; padding: 24px; display: grid; gap: 20px; min-width: 0; }.import-field { display: grid; gap: 8px; min-width: 0; }.import-field label { font-size: 14px; font-weight: 500; }.import-field small { color: hsl(var(--muted-foreground)); line-height: 1.6; }.import-field label span { color: hsl(var(--primary)); }
.import-row { display: flex; gap: 24px; flex-wrap: wrap; }.import-row > div { flex: 1; min-width: 160px; }.import-form > .el-button { justify-self: start; }
.import-file { display: grid; justify-items: start; gap: 10px; padding: 22px; border: 1px dashed hsl(var(--primary) / .5); border-radius: 6px; background: hsl(var(--primary) / .035); overflow-wrap: anywhere; }.import-file svg { color: hsl(var(--primary)); }.import-file p { font-size: 12px; color: hsl(var(--muted-foreground)); }.import-file input { max-width: 100%; font-size: 13px; }.import-file input::file-selector-button { padding: 7px 12px; margin-right: 12px; color: hsl(var(--foreground)); background: hsl(var(--secondary)); border: 1px solid hsl(var(--border)); border-radius: 4px; cursor: pointer; }
.import-guide { padding: 30px 26px; border-left: 1px solid hsl(var(--primary) / .3); align-self: start; }.import-guide__eyebrow { letter-spacing: .12em; font-size: 11px; color: hsl(var(--primary)); }.import-guide h2 { margin: 24px 0 16px; font: 27px/1.6 Georgia, 'SimSun', serif; }.import-guide p { font-size: 13px; line-height: 1.9; color: hsl(var(--muted-foreground)); }.import-guide ul { list-style: none; padding: 0; margin: 26px 0; display: grid; gap: 17px; }.import-guide li { display: flex; align-items: center; gap: 10px; font-size: 13px; }.import-guide svg { color: hsl(var(--primary)); }.import-guide__note { padding-top: 20px; border-top: 1px solid hsl(var(--border)); }
.import-mapping { padding: 22px; display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 18px; }.import-table { width: 100%; }.import-issues { padding: 20px; max-height: 360px; overflow: auto; }.import-issues h3 { font-size: 14px; margin-bottom: 12px; }.import-issues p { display: flex; flex-wrap: wrap; gap: 10px; font-size: 12px; color: hsl(var(--muted-foreground)); padding: 6px 0; }.import-issues > div { margin-top: 12px; display: flex; align-items: center; gap: 12px; }
.import-confirm { display: flex; padding: 22px; justify-content: space-between; align-items: center; gap: 18px; border-top: 1px solid hsl(var(--border)); }.import-confirm p { font-size: 12px; line-height: 1.7; max-width: 540px; color: hsl(var(--muted-foreground)); }.import-success { display: flex; gap: 18px; padding: 24px; border: 1px solid hsl(var(--primary) / .5); background: hsl(var(--primary) / .05); }.import-success svg, .import-success a { color: hsl(var(--primary)); }.import-success h2 { font-size: 19px; }.import-success p { margin: 8px 0 14px; font-size: 13px; line-height: 1.8; }
@media(max-width: 1000px) { .import-layout { grid-template-columns: 1fr; }.import-guide { display: none; }.import-mapping { grid-template-columns: repeat(2,minmax(0,1fr)); } }
@media(max-width: 600px) { .import-steps { gap: 10px; }.import-steps li { font-size: 11px; gap: 5px; }.import-steps span { font-size: 14px; }.import-form { padding: 16px; }.import-mapping { grid-template-columns: 1fr; }.import-confirm { align-items: start; flex-direction: column; } }
</style>
