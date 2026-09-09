<template>
  <!--
    数据导入面板（权限管理页第二个标签）：管理员批量导入论文
    ─────────────────────────────────────────────────────────
    两条入库途径：
      1. 文件导入：选择/拖入 JSON 文件 → 前端解析预览（篇数、作者数）→ 上传后端导入
      2. 在线爬取：关键词搜索 OpenAlex 单页（10/25 篇）→ 服务端映射后直接入库
    两种途径共用下方结果区（成功/跳过/失败 + 原因）与「手动同步图谱」按钮。
    格式：「论文为中心」的嵌套 JSON（{ papers: [...] }），作者/机构/关键词/渠道
          按名称自动去重——库里已有同名记录直接复用，同 DOI/同标题论文跳过，
          因此同一文件重复导入是安全的（幂等）。
  -->
  <div class="import-panel">
    <el-alert type="info" :closable="false" class="import-tip">
      支持「论文为中心」的嵌套 JSON 文件：一篇论文带上作者 / 机构 / 关键词 / 渠道，
      服务端按名称自动去重（已有同名记录直接复用），同 DOI / 同标题论文自动跳过，可重复导入。
      导入成功的数据会在数秒内自动同步到知识图谱（也可点「手动同步图谱」立即同步）。
    </el-alert>

    <div class="toolbar">
      <el-button @click="downloadTemplate">下载导入模板</el-button>
      <el-button type="primary" :disabled="!importFile || importing" :loading="importing" @click="doImport">
        开始导入{{ previewPapers.length > 0 ? `（${previewPapers.length} 篇）` : '' }}
      </el-button>
      <el-button :disabled="syncing" :loading="syncing" @click="syncGraph">手动同步图谱</el-button>
    </div>

    <!-- 文件选择：拖拽或点击（不自动上传，先本地解析预览） -->
    <el-upload drag accept=".json,application/json" :auto-upload="false" :show-file-list="false"
      :on-change="onFileChange" class="import-upload">
      <div class="upload-body">
        <p class="upload-main">将 JSON 文件拖到此处，或点击选择文件</p>
        <p class="upload-sub">{{ importFile ? `已选择：${importFile.name}` : '尚未选择文件' }}</p>
      </div>
    </el-upload>

    <!-- 在线爬取 OpenAlex：关键词搜索单页入库（联网调用，结果复用下方汇总表） -->
    <div class="crawl-box">
      <div class="crawl-head">
        <span class="crawl-title">在线爬取 OpenAlex</span>
        <span class="upload-sub">按关键词搜索 OpenAlex 学术库并直接入库（需联网，DOI 判重，可重复爬取）</span>
      </div>
      <div class="crawl-row">
        <el-input v-model="crawlKeyword" placeholder="搜索关键词，如 knowledge graph" style="width: 300px"
          clearable @keyup.enter="doCrawl" />
        <el-select v-model="crawlMaxRecords" style="width: 110px">
          <el-option :value="10" label="10 篇" />
          <el-option :value="25" label="25 篇" />
        </el-select>
        <el-button type="primary" :loading="crawling" @click="doCrawl">开始爬取</el-button>
      </div>
    </div>

    <!-- 导入前预览：文件里解析出来的论文清单 -->
    <el-table v-if="previewPapers.length > 0" :data="previewPapers" max-height="240" stripe class="preview-table">
      <el-table-column type="index" label="#" width="50" />
      <el-table-column prop="title" label="标题" min-width="280" show-overflow-tooltip />
      <el-table-column label="类型" width="150">
        <template #default="{ row }">{{ typeLabel(row.paperType) }}</template>
      </el-table-column>
      <el-table-column prop="publicationDate" label="发行时间" width="110" />
      <el-table-column label="作者" width="70" align="center">
        <template #default="{ row }">{{ (row.authors ?? []).length }}</template>
      </el-table-column>
      <el-table-column label="关键词" width="70" align="center">
        <template #default="{ row }">{{ (row.keywords ?? []).length }}</template>
      </el-table-column>
    </el-table>

    <!-- 导入结果：计数汇总 + 逐条明细 -->
    <div v-if="summary" class="result-box">
      <div class="result-stats">
        <el-tag type="success">成功 {{ summary.imported }}</el-tag>
        <el-tag type="warning">跳过（已存在）{{ summary.skipped }}</el-tag>
        <el-tag type="danger">失败 {{ summary.failed }}</el-tag>
        <span class="result-created">
          新建：作者 {{ summary.createdAuthors }} · 机构 {{ summary.createdInstitutions }}
          · 关键词 {{ summary.createdKeywords }} · 渠道 {{ summary.createdVenues }}
        </span>
      </div>
      <el-table :data="summary.rows" max-height="240" stripe class="preview-table">
        <el-table-column prop="index" label="#" width="50" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'IMPORTED' ? 'success' : row.status === 'SKIPPED' ? 'warning' : 'danger'"
              size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
        <el-table-column prop="message" label="说明" min-width="260" show-overflow-tooltip />
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 数据导入脚本：本地解析预览 → 上传导入 → 结果展示
 * ──────────────────────────────────────────────
 * 上传前先 FileReader 把 JSON 解析成 ImportPaperItem[] 预览，格式错了在本地就能发现；
 * 真正导入调用 adminApi.importPapers（multipart 上传，后端 ADMIN 校验）。
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi, type ImportPaperItem, type ImportSummary } from '../api'
import { ApiError } from '../api/http'

/** 论文类型 → 中文显示（与 DataView 保持一致的白名单） */
const TYPE_LABELS: Record<string, string> = {
  JOURNAL_ARTICLE: '期刊论文', CONFERENCE_PAPER: '会议论文', PATENT: '专利', OTHER: '其他'
}
const STATUS_LABELS: Record<string, string> = {
  IMPORTED: '导入成功', SKIPPED: '已存在跳过', FAILED: '失败'
}

function typeLabel(t?: string): string {
  return TYPE_LABELS[t ?? ''] ?? (t ?? '—')
}

function statusLabel(s: string): string {
  return STATUS_LABELS[s] ?? s
}

const importFile = ref<File | null>(null)
const previewPapers = ref<ImportPaperItem[]>([])
const summary = ref<ImportSummary | null>(null)
const importing = ref(false)
const syncing = ref(false)

/** 选择文件：本地解析 JSON 并校验 papers 数组，通过才允许导入 */
function onFileChange(uploadFile: { raw?: File }) {
  const file = uploadFile.raw
  if (!file) return
  summary.value = null
  const reader = new FileReader()
  reader.onload = () => {
    try {
      const parsed = JSON.parse(String(reader.result)) as { papers?: ImportPaperItem[] }
      if (!parsed || !Array.isArray(parsed.papers) || parsed.papers.length === 0) {
        ElMessage.error('文件格式不正确：根节点应包含非空的 papers 数组')
        importFile.value = null
        previewPapers.value = []
        return
      }
      importFile.value = file
      previewPapers.value = parsed.papers
      ElMessage.success(`已解析 ${parsed.papers.length} 篇论文，确认无误后点击「开始导入」`)
    } catch {
      ElMessage.error('文件不是合法的 JSON，请用「下载导入模板」检查格式')
      importFile.value = null
      previewPapers.value = []
    }
  }
  reader.onerror = () => ElMessage.error('读取文件失败，请重试')
  reader.readAsText(file)
}

/** 执行导入：上传文件到后端，展示逐条结果 */
async function doImport() {
  // 防重入：按钮 disabled 要等 Vue 下一次渲染才生效，快速双击时靠这里兜底
  if (!importFile.value || importing.value) return
  importing.value = true
  try {
    const res = await adminApi.importPapers(importFile.value)
    summary.value = res
    if (res.failed === 0 && res.skipped === 0) {
      ElMessage.success(`全部导入成功：新增 ${res.imported} 篇论文`)
    } else {
      ElMessage.warning(`导入完成：成功 ${res.imported} / 跳过 ${res.skipped} / 失败 ${res.failed}（详见下方明细）`)
    }
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? (err.problem?.detail ?? err.message) : '导入失败')
  } finally {
    importing.value = false
  }
}

/** 手动触发图同步：让刚导入的数据立刻出现在知识图谱里 */
async function syncGraph() {
  syncing.value = true
  try {
    const res = await adminApi.syncGraph()
    ElMessage.success(`图谱已同步，共处理 ${res.processed} 条事件`)
  } catch {
    ElMessage.error('同步失败，请稍后重试')
  } finally {
    syncing.value = false
  }
}

// ====================================================================
// 在线爬取 OpenAlex（关键词搜索单页，结果复用与文件导入相同的汇总结构）
// ====================================================================
const crawlKeyword = ref('')
const crawlMaxRecords = ref(10)
const crawling = ref(false)

/** 执行爬取：DOI 判重保证幂等，同批数据再爬会全部 SKIPPED */
async function doCrawl() {
  const kw = crawlKeyword.value.trim()
  if (!kw) {
    ElMessage.warning('请输入搜索关键词')
    return
  }
  if (crawling.value) return
  crawling.value = true
  try {
    const res = await adminApi.crawlOpenAlex({ keyword: kw, maxRecords: crawlMaxRecords.value })
    summary.value = res
    if (res.failed === 0 && res.skipped === 0) {
      ElMessage.success(`爬取完成：新增 ${res.imported} 篇论文`)
    } else {
      ElMessage.warning(`爬取完成：成功 ${res.imported} / 跳过 ${res.skipped} / 失败 ${res.failed}（详见下方明细）`)
    }
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? (err.problem?.detail ?? err.message) : '爬取失败，请检查网络')
  } finally {
    crawling.value = false
  }
}

/** 生成示例导入文件供下载（前端拼 Blob，无需后端接口） */
function downloadTemplate() {
  const template = {
    papers: [
      {
        title: '示例论文：知识图谱构建方法研究',
        doi: '10.0000/example.2026.001',
        paperType: 'JOURNAL_ARTICLE',
        language: 'zh',
        publicationDate: '2026-01-15',
        abstractText: '这是一篇用于演示导入格式的示例论文。',
        citationCount: 3,
        venue: { name: '示例学报', venueType: 'JOURNAL', issn: '1000-0001' },
        authors: [
          { name: '示例作者甲', orcid: '0000-0001-0000-0001', institution: { name: '示例大学', countryCode: 'CN' } },
          { name: '示例作者乙', institution: { name: '示例大学', countryCode: 'CN' } }
        ],
        keywords: [
          { name: '知识图谱', fieldName: '人工智能' },
          { name: '关系抽取', fieldName: '自然语言处理' }
        ]
      }
    ]
  }
  const blob = new Blob([JSON.stringify(template, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'import-template.json'
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<style scoped>
.import-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.import-tip {
  margin: 0;
}

.toolbar {
  display: flex;
  gap: var(--space-3);
  align-items: center;
}

.import-upload :deep(.el-upload) {
  width: 100%;
}

.import-upload :deep(.el-upload-dragger) {
  width: 100%;
  padding: var(--space-4);
}

.upload-main {
  color: var(--ink);
  font-size: 14px;
  margin: 0 0 var(--space-1) 0;
}

.upload-sub {
  color: var(--muted);
  font-size: 12px;
  margin: 0;
}

.crawl-box {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  background: var(--paper);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-3);
}

.crawl-head {
  display: flex;
  gap: var(--space-3);
  align-items: baseline;
}

.crawl-title {
  color: var(--ink);
  font-size: 14px;
  font-weight: 600;
}

.crawl-row {
  display: flex;
  gap: var(--space-3);
  align-items: center;
}

.preview-table {
  width: 100%;
}

.result-box {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  background: var(--paper);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-3);
}

.result-stats {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  flex-wrap: wrap;
}

.result-created {
  color: var(--muted);
  font-size: 12px;
}
</style>
