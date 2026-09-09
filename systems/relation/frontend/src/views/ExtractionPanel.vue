<template>
  <!--
    实体抽取面板（权限管理页第四个标签）：管理员触发 LLM 抽取
    ─────────────────────────────────────────────────────────
    流程：关键词搜索论文勾选（≤20 篇）或手动输入 ID → 触发（统一发 paperIds 数组）
          → 后端异步执行，前端每 2 秒轮询状态 → 全部终态后自动停止
          → 展开行查看实体台账与关系明细（复用 COMPLETED 时自动拉取的结果）
    注意：LLM 调用依赖后端环境变量 LLM_API_KEY / LLM_BASE_URL，未配置时任务会 FAILED。
  -->
  <div class="extraction-panel">
    <el-alert type="info" :closable="false" class="panel-tip">
      调用大模型（LLM）分析论文标题与摘要，抽取方法 / 数据集 / 工具 / 主题 / 机构 / 人员六类实体
      及其关系，结果写入抽取台账并投影到知识图谱（研究实体节点 + EXTRACTED_FROM 证据边 + LLM 关系边）。
      后端需配置有效的 <b>LLM_API_KEY</b>（可选 <b>LLM_BASE_URL</b>）环境变量，未配置时抽取任务会以「失败」结束。
    </el-alert>

    <!-- 论文选择：关键词搜索勾选 + 一键抽最早 10 篇 PENDING -->
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索论文标题…" clearable style="width: 260px"
        @keyup.enter="searchPapers" />
      <el-button :loading="searching" @click="searchPapers">搜索</el-button>
      <el-button type="primary" plain :loading="triggeringDefault" @click="triggerDefault">
        抽取最早 10 篇待处理
      </el-button>
      <span class="muted">（PENDING 论文按 id 升序取前 10 篇）</span>
    </div>

    <!-- 搜索结果：勾选待抽取论文（最多 20 篇） -->
    <el-table v-if="candidates.length > 0" :data="candidates" max-height="260" stripe class="block-table"
      :selectable="selectable" @selection-change="onTableSelectionChange">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="标题" min-width="280" show-overflow-tooltip />
      <el-table-column label="抽取状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.extractionStatus)" size="small">
            {{ statusLabel(row.extractionStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="publicationYear" label="年份" width="70" />
    </el-table>

    <!-- 手动指定论文 ID：搜索不到时兜底 -->
    <div class="manual-row">
      <el-input v-model="manualId" placeholder="论文 ID（可逗号分隔多个）" style="width: 260px" />
      <el-button :loading="addingManual" @click="addManual">加入选择</el-button>
      <el-button type="primary" :disabled="selectedPapers.length === 0" :loading="triggering"
        @click="doTrigger">
        开始抽取（已选 {{ selectedPapers.length }} 篇）
      </el-button>
    </div>

    <!-- 已选论文清单（可单个移除） -->
    <div v-if="selectedPapers.length > 0" class="selected-box">
      <div class="selected-head">
        <span>已选论文（单次最多 20 篇）</span>
        <el-button link type="danger" @click="clearSelection">清空</el-button>
      </div>
      <el-tag v-for="p in selectedPapers" :key="p.id" closable class="selected-tag" @close="removeSelected(p.id)">
        #{{ p.id }} {{ p.title }}
      </el-tag>
    </div>

    <!-- 任务状态表格：触发后出现，自动轮询到全部终态为止 -->
    <div v-if="taskRows.length > 0" class="result-box">
      <div class="result-head">
        <span>抽取任务状态（每 2 秒自动轮询）</span>
        <span class="muted">{{ doneCount }}/{{ taskRows.length }} 已完成</span>
      </div>
      <el-table :data="taskRows" stripe :row-key="(row: TaskRow) => row.paperId" class="block-table"
        @expand-change="onExpandChange">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div v-loading="row.detailLoading" class="detail-box">
              <template v-if="row.detail">
                <h4>实体台账（{{ row.detail.entities.length }} 条）</h4>
                <el-table :data="row.detail.entities" size="small" max-height="220" stripe>
                  <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
                  <el-table-column label="类型" width="110">
                    <template #default="{ row: e }">{{ entityTypeLabel(e.type) }}</template>
                  </el-table-column>
                  <el-table-column label="归并到" width="160">
                    <template #default="{ row: e }">
                      <span v-if="e.resolvedEntityType">
                        {{ entityTypeLabel(e.resolvedEntityType) }} #{{ e.resolvedEntityId }}
                      </span>
                      <span v-else class="muted">未归并</span>
                    </template>
                  </el-table-column>
                </el-table>
                <h4>关系（{{ row.detail.relationships.length }} 条）</h4>
                <el-table :data="row.detail.relationships" size="small" max-height="220" stripe>
                  <el-table-column prop="sourceName" label="源实体" min-width="140" show-overflow-tooltip />
                  <el-table-column label="关系" width="130">
                    <template #default="{ row: r }">{{ relationLabel(r.type) }}</template>
                  </el-table-column>
                  <el-table-column prop="targetName" label="目标实体" min-width="140" show-overflow-tooltip />
                  <el-table-column prop="evidence" label="原文依据" min-width="200" show-overflow-tooltip />
                  <el-table-column label="置信度" width="80" align="center">
                    <template #default="{ row: r }">{{ (r.confidence * 100).toFixed(0) }}%</template>
                  </el-table-column>
                </el-table>
              </template>
              <p v-else-if="row.detailFetched" class="muted">该论文没有可展示的抽取明细</p>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="paperId" label="ID" width="80" />
        <el-table-column prop="paperTitle" label="标题" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="entityCount" label="实体数" width="90" align="center" />
        <el-table-column prop="relationshipCount" label="关系数" width="90" align="center" />
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 实体抽取脚本：选论文 → 触发异步抽取 → 轮询状态 → 展开看明细
 * ─────────────────────────────────────────────
 * 两个防坑点：
 *  - 触发请求体始终发 paperIds 数组（后端契约就是数组，缺省时抽最早 10 篇 PENDING）；
 *  - 轮询用 setInterval，组件卸载时必须 clearInterval，否则切标签后还在发请求。
 */
import { computed, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { extractionApi, papersApi, type ExtractionResultDto, type ExtractionStatus, type Paper } from '../api'
import { ApiError } from '../api/http'

/** 抽取状态 → 中文 + 标签色（PENDING=warning / IN_PROGRESS=primary / COMPLETED=success / FAILED=danger） */
const STATUS_LABELS: Record<string, string> = {
  PENDING: '待抽取', IN_PROGRESS: '抽取中', COMPLETED: '已完成', FAILED: '失败'
}
const STATUS_TAG_TYPES: Record<string, string> = {
  PENDING: 'warning', IN_PROGRESS: 'primary', COMPLETED: 'success', FAILED: 'danger'
}
/** 六类 LLM 实体 + 归并后的业务实体 → 中文 */
const ENTITY_TYPE_LABELS: Record<string, string> = {
  METHOD: '方法', DATASET: '数据集', TOOL: '工具', TOPIC: '主题', ORGANIZATION: '机构', PERSON: '人员',
  AUTHOR: '作者', INSTITUTION: '机构', KEYWORD: '关键词', RESEARCH_ENTITY: '研究实体'
}
/** 六类 LLM 关系 → 中文 */
const RELATION_LABELS: Record<string, string> = {
  USES: '使用', EXTENDS: '扩展', EVALUATES_ON: '在…上评估', APPLIED_TO: '应用于',
  PROPOSED_BY: '由…提出', COMPARED_WITH: '与…比较'
}

function statusLabel(s: string): string {
  return STATUS_LABELS[s] ?? s
}

function statusTagType(s: string): string {
  return STATUS_TAG_TYPES[s] ?? 'info'
}

function entityTypeLabel(t: string): string {
  return ENTITY_TYPE_LABELS[t] ?? t
}

function relationLabel(t: string): string {
  return RELATION_LABELS[t] ?? t
}

// ====================================================================
// 论文选择：表格勾选 + 手动 ID 两个来源合并
// ====================================================================
const keyword = ref('')
const candidates = ref<Paper[]>([])
const searching = ref(false)
/** 表格勾选的论文（el-table 的 selection-change 只回传表格行） */
const tableSelection = ref<Paper[]>([])
/** 手动输入 ID 加入的论文（不在候选表里，单独维护避免被表格事件覆盖） */
const manualPapers = ref<Paper[]>([])
const manualId = ref('')
const addingManual = ref(false)
/** 已选论文 = 表格勾选 ∪ 手动加入（按 id 去重） */
const selectedPapers = computed<Paper[]>(() => {
  const merged = new Map<number, Paper>()
  tableSelection.value.forEach((p) => merged.set(p.id, p))
  manualPapers.value.forEach((p) => merged.set(p.id, p))
  return [...merged.values()]
})

/** 搜索论文（标题关键词，最多拉 50 条候选） */
async function searchPapers() {
  searching.value = true
  try {
    const res = await papersApi.list({ keyword: keyword.value.trim() || undefined, size: 50 })
    candidates.value = res.items
    if (res.items.length === 0) {
      ElMessage.info('没有匹配的论文，可改用下方「手动指定论文 ID」')
    }
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? (err.problem?.detail ?? err.message) : '搜索失败')
  } finally {
    searching.value = false
  }
}

function onTableSelectionChange(rows: Paper[]) {
  tableSelection.value = rows
}

/** 勾选上限：满 20 篇后禁止再勾新行（已勾中的仍可取消） */
function selectable(row: Paper): boolean {
  return selectedPapers.value.length < 20 || selectedPapers.value.some((p) => p.id === row.id)
}

/** 手动加入：按 ID 拉论文详情（支持逗号分隔多个） */
async function addManual() {
  const ids = manualId.value
    .split(/[,，\s]+/)
    .map((s) => Number(s.trim()))
    .filter((n) => Number.isInteger(n) && n > 0)
  if (ids.length === 0) {
    ElMessage.warning('请输入有效的论文 ID')
    return
  }
  if (selectedPapers.value.length + ids.length > 20) {
    ElMessage.warning('单次最多抽取 20 篇论文')
    return
  }
  addingManual.value = true
  try {
    for (const id of ids) {
      if (selectedPapers.value.some((p) => p.id === id)) continue
      const paper = await papersApi.get(id)
      manualPapers.value = [...manualPapers.value, paper]
    }
    manualId.value = ''
    ElMessage.success('已加入选择')
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? (err.problem?.detail ?? err.message) : '论文不存在或查询失败')
  } finally {
    addingManual.value = false
  }
}

function removeSelected(id: number) {
  tableSelection.value = tableSelection.value.filter((p) => p.id !== id)
  manualPapers.value = manualPapers.value.filter((p) => p.id !== id)
}

function clearSelection() {
  tableSelection.value = []
  manualPapers.value = []
}

// ====================================================================
// 触发 + 轮询状态
// ====================================================================
interface TaskRow {
  paperId: number
  paperTitle: string
  status: ExtractionStatus
  entityCount: number
  relationshipCount: number
  detail: ExtractionResultDto | null
  detailFetched: boolean
  detailLoading: boolean
}

const triggering = ref(false)
const triggeringDefault = ref(false)
const taskRows = ref<TaskRow[]>([])
/** 轮询定时器句柄：卸载时必须清掉，避免组件销毁后仍发请求 */
let pollTimer: number | null = null
/** 轮询重入保护：一次轮询未结束时不启动下一轮 */
let polling = false

const doneCount = computed(
  () => taskRows.value.filter((r) => r.status === 'COMPLETED' || r.status === 'FAILED').length
)

/** 触发选中论文的抽取（请求体始终是数组） */
async function doTrigger() {
  if (selectedPapers.value.length === 0 || triggering.value) return
  triggering.value = true
  try {
    const res = await extractionApi.trigger(selectedPapers.value.map((p) => p.id))
    ElMessage.success(`已提交 ${res.queuedCount} 篇论文的抽取任务，稍候自动刷新状态`)
    initTaskRows(res.paperIds)
    startPolling()
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? (err.problem?.detail ?? err.message) : '触发失败')
  } finally {
    triggering.value = false
  }
}

/** 一键抽取最早 10 篇 PENDING（不带 paperIds，后端自动取） */
async function triggerDefault() {
  if (triggeringDefault.value) return
  triggeringDefault.value = true
  try {
    const res = await extractionApi.trigger()
    ElMessage.success(`已提交 ${res.queuedCount} 篇论文的抽取任务，稍候自动刷新状态`)
    initTaskRows(res.paperIds)
    startPolling()
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? (err.problem?.detail ?? err.message) : '触发失败')
  } finally {
    triggeringDefault.value = false
  }
}

/** 用触发接口回传的 paperIds 初始化状态表（标题未知时先占位，轮询会回填） */
function initTaskRows(paperIds: number[]) {
  taskRows.value = paperIds.map((id) => {
    const found = selectedPapers.value.find((p) => p.id === id)
    return {
      paperId: id,
      paperTitle: found ? found.title : `论文 #${id}`,
      status: 'PENDING' as ExtractionStatus,
      entityCount: 0,
      relationshipCount: 0,
      detail: null,
      detailFetched: false,
      detailLoading: false
    }
  })
}

function startPolling() {
  stopPolling()
  pollTimer = window.setInterval(pollStatuses, 2000)
  void pollStatuses()
}

/** 轮询一遍：只查未终态的行；全部终态后自动停表 */
async function pollStatuses() {
  if (polling) return
  const pending = taskRows.value.filter((r) => r.status === 'PENDING' || r.status === 'IN_PROGRESS')
  if (pending.length === 0) {
    stopPolling()
    return
  }
  polling = true
  try {
    await Promise.all(pending.map(async (row) => {
      try {
        const s = await extractionApi.status(row.paperId)
        row.paperTitle = s.paperTitle
        row.status = s.status
        row.entityCount = s.extractedEntityCount
        row.relationshipCount = s.extractedRelationshipCount
        // 抽取完成（无论成败）顺手把明细拉回来缓存，展开行时直接展示
        if (row.status === 'COMPLETED' && !row.detailFetched) {
          row.detail = await extractionApi.result(row.paperId)
          row.detailFetched = true
        }
      } catch {
        // 单篇轮询失败不中断整轮，下一轮再试
      }
    }))
  } finally {
    polling = false
  }
  if (!taskRows.value.some((r) => r.status === 'PENDING' || r.status === 'IN_PROGRESS')) {
    stopPolling()
  }
}

function stopPolling() {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

/** 展开行时确保明细已加载（COMPLETED 已在轮询里预取，这里兜底 FAILED 等情况） */
function onExpandChange(row: TaskRow, expandedRows: TaskRow[]) {
  if (!expandedRows.some((r) => r.paperId === row.paperId)) return
  void loadDetail(row)
}

async function loadDetail(row: TaskRow) {
  if (row.detailFetched) return
  row.detailLoading = true
  try {
    row.detail = await extractionApi.result(row.paperId)
    row.detailFetched = true
  } catch {
    // 拉不到明细就留空，展开区显示"没有可展示的明细"
  } finally {
    row.detailLoading = false
  }
}

onUnmounted(stopPolling)
</script>

<style scoped>
.extraction-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.panel-tip {
  margin: 0;
}

.toolbar {
  display: flex;
  gap: var(--space-3);
  align-items: center;
  flex-wrap: wrap;
}

.manual-row {
  display: flex;
  gap: var(--space-3);
  align-items: center;
}

.muted {
  color: var(--muted);
  font-size: 12px;
}

.block-table {
  width: 100%;
}

.selected-box {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  background: var(--paper);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-3);
}

.selected-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--ink);
  font-size: 13px;
}

.selected-tag {
  align-self: flex-start;
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

.result-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--ink);
  font-size: 13px;
}

.detail-box {
  padding: var(--space-2) var(--space-4);
  min-height: 60px;
}

.detail-box h4 {
  color: var(--ink);
  font-size: 13px;
  margin: var(--space-2) 0;
}
</style>
