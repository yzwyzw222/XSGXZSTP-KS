<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElPagination,
  ElTable,
  ElTableColumn,
  ElTag,
  vLoading,
} from 'element-plus'

import CountUpNumber from '@/components/CountUpNumber.vue'
import { toErrorMessage } from '@/services/api'
import { crawlApi } from '@/services/business'
import { hasPermission } from '@/services/session'
import type {
  CrawlFailure,
  CrawlRun,
  CrawlSchedule,
  CrawlTask,
  CrawlTaskParameters,
  PageResponse,
} from '@/types/api'
import { formatDateTime, splitValues } from '@/utils/format'
import { prefersReducedMotion } from '@/utils/motion'

interface TaskForm {
  sourceId: number
  name: string
  publicationDateFrom: string
  publicationDateTo: string
  keyword: string
  authorIds: string
  institutionIds: string
  dois: string
  orcids: string
  rorIds: string
  updatedFrom: string
  updatedUntil: string
  maxPages: number
  maxRecords: number
}

interface RunLogEntry {
  id: number
  level: 'info' | 'success' | 'warning'
  message: string
  createdAt: string
}

const tasks = ref<PageResponse<CrawlTask>>({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const failures = ref<PageResponse<CrawlFailure>>({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const taskDialog = ref(false)
const scheduleDialog = ref(false)
const runDialog = ref(false)
const editing = ref<CrawlTask | null>(null)
const scheduling = ref<CrawlTask | null>(null)
const currentRun = ref<CrawlRun | null>(null)
const runLogs = ref<RunLogEntry[]>([])
const logStream = ref<HTMLElement | null>(null)
const runId = ref<number | undefined>()
const controlling = ref('')
let runPollTimer: number | undefined
let runPollFailures = 0
let runLogSequence = 0
const scheduleVersions = new Map<number, number>()
const canCreate = computed(() => hasPermission('CRAWL_TASK_CREATE'))
const canUpdate = computed(() => hasPermission('CRAWL_TASK_UPDATE'))
const canControl = computed(() => hasPermission('CRAWL_TASK_CONTROL'))
const canSchedule = computed(() => hasPermission('CRAWL_SCHEDULE_MANAGE'))
const form = reactive<TaskForm>(emptyTaskForm())
const scheduleForm = reactive({
  localTime: '02:00',
  timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai',
  version: undefined as number | undefined,
})
const runProgress = computed(() => {
  const run = currentRun.value
  if (!run) return 0
  if (['SUCCEEDED', 'COMPLETED'].includes(run.status)) return 100
  const taskLimit = tasks.value.items.find((item) => item.id === run.taskId)?.parameters.maxRecords
  if (!taskLimit) return 0
  return Math.min(99, Math.round(run.readCount / taskLimit * 100))
})
const runStatusTone = computed(() => {
  const status = currentRun.value?.status ?? ''
  if (['RUNNING', 'QUEUED', 'PENDING'].includes(status)) return 'is-running'
  if (status.includes('RETRY') || status === 'FAILED') return 'is-warning'
  return 'is-idle'
})

function emptyTaskForm(): TaskForm {
  return {
    sourceId: 1,
    name: '',
    publicationDateFrom: '',
    publicationDateTo: '',
    keyword: '',
    authorIds: '',
    institutionIds: '',
    dois: '',
    orcids: '',
    rorIds: '',
    updatedFrom: '',
    updatedUntil: '',
    maxPages: 1,
    maxRecords: 100,
  }
}

function toParameters(): CrawlTaskParameters {
  return {
    publicationDateFrom: form.publicationDateFrom || null,
    publicationDateTo: form.publicationDateTo || null,
    keyword: form.keyword.trim() || null,
    authorIds: splitValues(form.authorIds),
    institutionIds: splitValues(form.institutionIds),
    dois: splitValues(form.dois),
    orcids: splitValues(form.orcids),
    rorIds: splitValues(form.rorIds),
    updatedFrom: form.updatedFrom ? new Date(form.updatedFrom).toISOString() : null,
    updatedUntil: form.updatedUntil ? new Date(form.updatedUntil).toISOString() : null,
    maxPages: form.maxPages,
    maxRecords: form.maxRecords,
  }
}

async function load(page = 0): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    tasks.value = await crawlApi.tasks(page, tasks.value.size)
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editing.value = null
  Object.assign(form, emptyTaskForm())
  taskDialog.value = true
}

function openEdit(task: CrawlTask): void {
  editing.value = task
  Object.assign(form, {
    sourceId: task.sourceId,
    name: task.name,
    publicationDateFrom: task.parameters.publicationDateFrom ?? '',
    publicationDateTo: task.parameters.publicationDateTo ?? '',
    keyword: task.parameters.keyword ?? '',
    authorIds: task.parameters.authorIds.join(', '),
    institutionIds: task.parameters.institutionIds.join(', '),
    dois: task.parameters.dois.join(', '),
    orcids: task.parameters.orcids.join(', '),
    rorIds: task.parameters.rorIds.join(', '),
    updatedFrom: task.parameters.updatedFrom?.slice(0, 16) ?? '',
    updatedUntil: task.parameters.updatedUntil?.slice(0, 16) ?? '',
    maxPages: task.parameters.maxPages,
    maxRecords: task.parameters.maxRecords,
  })
  taskDialog.value = true
}

async function saveTask(): Promise<void> {
  if (!form.name.trim()) {
    errorMessage.value = '任务名称不能为空'
    return
  }
  saving.value = true
  errorMessage.value = ''
  try {
    if (editing.value) {
      await crawlApi.updateTask(editing.value, form.name.trim(), toParameters())
    } else {
      await crawlApi.createTask({ sourceId: form.sourceId, name: form.name.trim(), parameters: toParameters() })
    }
    taskDialog.value = false
    ElMessage.success(editing.value ? '采集任务已更新' : '采集任务已创建')
    await load(tasks.value.page)
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    saving.value = false
  }
}

async function trigger(task: CrawlTask): Promise<void> {
  errorMessage.value = ''
  try {
    currentRun.value = await crawlApi.trigger(task.id)
    resetRunLogs()
    appendRunLog('info', `运行 ${currentRun.value.runNumber} 已进入队列`)
    runId.value = currentRun.value.id
    runDialog.value = true
    await loadFailures()
    scheduleRunPoll()
    ElMessage.success('采集运行已进入队列')
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  }
}

function openSchedule(task: CrawlTask): void {
  scheduling.value = task
  scheduleForm.version = scheduleVersions.get(task.id)
  scheduleDialog.value = true
}

async function saveSchedule(): Promise<void> {
  if (!scheduling.value) return
  saving.value = true
  try {
    const result: CrawlSchedule = await crawlApi.schedule(
      scheduling.value.id,
      scheduleForm.localTime,
      scheduleForm.timeZone,
      scheduleForm.version,
    )
    scheduleVersions.set(scheduling.value.id, result.version)
    scheduleDialog.value = false
    ElMessage.success('每日调度已保存，下次执行：' + formatDateTime(result.nextFireAt))
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    saving.value = false
  }
}

async function findRun(): Promise<void> {
  if (!runId.value || runId.value < 1) {
    errorMessage.value = '请输入有效的运行编号'
    return
  }
  errorMessage.value = ''
  try {
    currentRun.value = await crawlApi.run(runId.value)
    resetRunLogs()
    appendRunLog('info', `已连接运行 ${currentRun.value.runNumber}`)
    appendRunSnapshot(currentRun.value)
    runDialog.value = true
    await loadFailures()
    scheduleRunPoll()
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  }
}

async function loadFailures(page = 0): Promise<void> {
  if (!currentRun.value) return
  try {
    failures.value = await crawlApi.failures(currentRun.value.id, page, failures.value.size)
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  }
}

async function control(action: 'pause' | 'resume' | 'cancel' | 'retry-failures'): Promise<void> {
  if (!currentRun.value) return
  controlling.value = action
  try {
    currentRun.value = await crawlApi.control(currentRun.value.id, action)
    appendRunLog('info', `控制指令 ${action} 已提交，当前状态 ${currentRun.value.status}`)
    ElMessage.success('运行控制请求已提交')
    await loadFailures()
    scheduleRunPoll()
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    controlling.value = ''
  }
}

function resetRunLogs(): void {
  runLogs.value = []
  runLogSequence = 0
  runPollFailures = 0
}

function appendRunSnapshot(run: CrawlRun): void {
  appendRunLog('info', `已读取 ${run.readCount} 条，解析 ${run.parsedCount} 条`)
  appendRunLog('success', `新增 ${run.createdCount} 条，更新 ${run.updatedCount} 条，重复 ${run.duplicateCount} 条`)
  if (run.failureCount > 0) appendRunLog('warning', `当前有 ${run.failureCount} 条失败记录`)
}

function appendRunChanges(previous: CrawlRun, next: CrawlRun): void {
  if (previous.status !== next.status) {
    const level = next.status === 'FAILED' ? 'warning' : next.status === 'SUCCEEDED' ? 'success' : 'info'
    appendRunLog(level, `运行状态由 ${previous.status} 更新为 ${next.status}`)
  }
  if (previous.readCount !== next.readCount || previous.parsedCount !== next.parsedCount) {
    appendRunLog('info', `已读取 ${next.readCount} 条，解析 ${next.parsedCount} 条`)
  }
  if (previous.createdCount !== next.createdCount || previous.updatedCount !== next.updatedCount) {
    appendRunLog('success', `已入库 ${next.createdCount + next.updatedCount} 条成果`)
  }
  if (previous.failureCount !== next.failureCount) {
    appendRunLog('warning', `失败记录更新为 ${next.failureCount} 条`)
  }
}

/** 活动流只保留最近 80 行，并在新增行后平滑滚动到底部。 */
function appendRunLog(level: RunLogEntry['level'], message: string): void {
  runLogs.value = [
    ...runLogs.value,
    {
      id: ++runLogSequence,
      level,
      message,
      createdAt: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
    },
  ].slice(-80)
  void nextTick(() => {
    logStream.value?.scrollTo({
      top: logStream.value.scrollHeight,
      behavior: prefersReducedMotion() ? 'auto' : 'smooth',
    })
  })
}

function isRunActive(status: string): boolean {
  return ['PENDING', 'QUEUED', 'RUNNING', 'PAUSING', 'CANCEL_REQUESTED', 'RETRYING'].includes(status)
}

function clearRunPoll(): void {
  if (runPollTimer !== undefined) {
    window.clearTimeout(runPollTimer)
    runPollTimer = undefined
  }
}

function scheduleRunPoll(): void {
  clearRunPoll()
  if (!runDialog.value || !currentRun.value || !isRunActive(currentRun.value.status)) return
  runPollTimer = window.setTimeout(() => void pollRun(), 1500)
}

/** 运行详情采用串行有界轮询，连续三次失败后停止，避免失联时无限施压。 */
async function pollRun(): Promise<void> {
  const previous = currentRun.value
  if (!previous || !runDialog.value) return
  try {
    const next = await crawlApi.run(previous.id)
    if (!runDialog.value || currentRun.value?.id !== next.id) return
    runPollFailures = 0
    appendRunChanges(previous, next)
    currentRun.value = next
    if (next.failureCount !== previous.failureCount) await loadFailures()
    scheduleRunPoll()
  } catch (error) {
    runPollFailures += 1
    appendRunLog('warning', `状态刷新失败（${runPollFailures}/3）`)
    if (runPollFailures >= 3) {
      errorMessage.value = `运行状态自动刷新已停止：${toErrorMessage(error)}`
      clearRunPoll()
    } else {
      scheduleRunPoll()
    }
  }
}

onMounted(() => load())
watch(runDialog, (visible) => {
  if (visible) scheduleRunPoll()
  else clearRunPoll()
})
onBeforeUnmount(clearRunPoll)
</script>

<template>
  <section class="page-stack">
    <header class="page-heading">
      <div>
        <span class="eyebrow">INGESTION / CRAWL TASKS</span>
        <h1>采集任务</h1>
        <p>定义受控采集范围，触发或调度任务，并按运行编号检查处理计数和失败证据。</p>
      </div>
      <ElButton v-if="canCreate" type="primary" @click="openCreate">新建采集任务</ElButton>
    </header>

    <div class="filter-panel toolbar">
      <div>
        <strong>运行追踪</strong>
        <span class="meta-line block-note">输入运行编号可查看实时状态与失败明细</span>
      </div>
      <div class="inline-search">
        <ElInputNumber v-model="runId" :min="1" controls-position="right" placeholder="运行编号" />
        <ElButton @click="findRun">查询运行</ElButton>
      </div>
    </div>
    <ElAlert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />

    <div class="content-panel">
      <div class="toolbar"><strong>任务定义</strong><span class="meta-line">共 {{ tasks.totalElements }} 个</span></div>
      <ElTable v-loading="loading" :data="tasks.items" empty-text="暂无采集任务">
        <ElTableColumn prop="name" label="任务名称" min-width="200" />
        <ElTableColumn prop="sourceId" label="来源 ID" width="100" />
        <ElTableColumn label="采集范围" min-width="260">
          <template #default="{ row }">
            <span>{{ row.parameters.keyword || '未限定关键词' }}</span>
            <small class="block-note">{{ row.parameters.publicationDateFrom || '起始不限' }} — {{ row.parameters.publicationDateTo || '结束不限' }}</small>
          </template>
        </ElTableColumn>
        <ElTableColumn label="上限" width="150">
          <template #default="{ row }">{{ row.parameters.maxPages }} 页 / {{ row.parameters.maxRecords }} 条</template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="90">
          <template #default="{ row }"><ElTag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</ElTag></template>
        </ElTableColumn>
        <ElTableColumn label="更新时间" width="170"><template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template></ElTableColumn>
        <ElTableColumn label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <ElButton v-if="canUpdate" link type="primary" @click="openEdit(row as CrawlTask)">编辑</ElButton>
            <ElButton v-if="canControl" link type="primary" @click="trigger(row as CrawlTask)">立即执行</ElButton>
            <ElButton v-if="canSchedule" link type="primary" @click="openSchedule(row as CrawlTask)">调度</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <div v-if="tasks.totalPages > 1" class="pagination-row">
        <ElPagination :current-page="tasks.page + 1" :page-size="tasks.size" :total="tasks.totalElements" layout="prev, pager, next" @current-change="(page: number) => load(page - 1)" />
      </div>
    </div>

    <ElDialog v-model="taskDialog" :title="editing ? '编辑采集任务' : '新建采集任务'" width="760px">
      <ElForm label-position="top">
        <div class="form-grid">
          <ElFormItem label="数据源 ID"><ElInputNumber v-model="form.sourceId" :min="1" :disabled="Boolean(editing)" /></ElFormItem>
          <ElFormItem label="任务名称"><ElInput v-model="form.name" maxlength="128" /></ElFormItem>
          <ElFormItem label="出版日期起"><ElInput v-model="form.publicationDateFrom" type="date" /></ElFormItem>
          <ElFormItem label="出版日期止"><ElInput v-model="form.publicationDateTo" type="date" /></ElFormItem>
          <ElFormItem label="关键词"><ElInput v-model="form.keyword" maxlength="200" /></ElFormItem>
          <ElFormItem label="作者 ID（逗号分隔）"><ElInput v-model="form.authorIds" /></ElFormItem>
          <ElFormItem label="机构 ID（逗号分隔）"><ElInput v-model="form.institutionIds" /></ElFormItem>
          <ElFormItem label="DOI（逗号分隔）"><ElInput v-model="form.dois" /></ElFormItem>
          <ElFormItem label="ORCID（逗号分隔）"><ElInput v-model="form.orcids" /></ElFormItem>
          <ElFormItem label="ROR ID（逗号分隔）"><ElInput v-model="form.rorIds" /></ElFormItem>
          <ElFormItem label="外部更新时间起"><ElInput v-model="form.updatedFrom" type="datetime-local" /></ElFormItem>
          <ElFormItem label="外部更新时间止"><ElInput v-model="form.updatedUntil" type="datetime-local" /></ElFormItem>
          <ElFormItem label="最大页数"><ElInputNumber v-model="form.maxPages" :min="1" :max="5" /></ElFormItem>
          <ElFormItem label="最大记录数"><ElInputNumber v-model="form.maxRecords" :min="1" :max="500" /></ElFormItem>
        </div>
      </ElForm>
      <template #footer><ElButton @click="taskDialog = false">取消</ElButton><ElButton type="primary" :loading="saving" @click="saveTask">保存</ElButton></template>
    </ElDialog>

    <ElDialog v-model="scheduleDialog" title="配置每日调度" width="520px">
      <ElForm label-position="top">
        <ElFormItem label="本地时间"><ElInput v-model="scheduleForm.localTime" type="time" /></ElFormItem>
        <ElFormItem label="IANA 时区"><ElInput v-model="scheduleForm.timeZone" placeholder="Asia/Shanghai" /></ElFormItem>
        <ElFormItem label="计划版本（首次配置留空）">
          <ElInputNumber v-model="scheduleForm.version" :min="0" :precision="0" controls-position="right" />
        </ElFormItem>
      </ElForm>
      <ElAlert title="本页面后续修改会自动携带版本；刷新后修改已有计划时，请填写服务端当前版本以启用冲突校验。" type="info" :closable="false" />
      <template #footer><ElButton @click="scheduleDialog = false">取消</ElButton><ElButton type="primary" :loading="saving" @click="saveSchedule">保存调度</ElButton></template>
    </ElDialog>

    <ElDialog v-model="runDialog" title="采集运行详情" width="900px">
      <template v-if="currentRun">
        <div class="toolbar run-toolbar">
          <span class="run-status-label">
            <i class="status-pulse" :class="runStatusTone" aria-hidden="true" />
            <strong>{{ currentRun.status }}</strong>
            <small>{{ currentRun.runNumber }}</small>
          </span>
          <div v-if="canControl">
            <ElButton v-if="currentRun.status === 'RUNNING'" :loading="controlling === 'pause'" @click="control('pause')">暂停</ElButton>
            <ElButton v-if="currentRun.status === 'PAUSED'" :loading="controlling === 'resume'" @click="control('resume')">恢复</ElButton>
            <ElButton v-if="['RUNNING', 'PAUSED'].includes(currentRun.status)" type="danger" plain :loading="controlling === 'cancel'" @click="control('cancel')">取消</ElButton>
            <ElButton v-if="currentRun.failureCount > 0" :loading="controlling === 'retry-failures'" @click="control('retry-failures')">重试失败项</ElButton>
          </div>
        </div>
        <div class="crawler-progress" :aria-label="`采集进度 ${runProgress}%`">
          <span><i :style="{ width: `${runProgress}%` }" /></span>
          <b>{{ runProgress }}%</b>
        </div>
        <ElDescriptions :column="4" border>
          <ElDescriptionsItem label="读取"><CountUpNumber :value="currentRun.readCount" /></ElDescriptionsItem>
          <ElDescriptionsItem label="解析"><CountUpNumber :value="currentRun.parsedCount" /></ElDescriptionsItem>
          <ElDescriptionsItem label="新增"><CountUpNumber :value="currentRun.createdCount" /></ElDescriptionsItem>
          <ElDescriptionsItem label="更新"><CountUpNumber :value="currentRun.updatedCount" /></ElDescriptionsItem>
          <ElDescriptionsItem label="重复"><CountUpNumber :value="currentRun.duplicateCount" /></ElDescriptionsItem>
          <ElDescriptionsItem label="失败"><CountUpNumber :value="currentRun.failureCount" /></ElDescriptionsItem>
          <ElDescriptionsItem label="请求"><CountUpNumber :value="currentRun.requestCount" /></ElDescriptionsItem>
          <ElDescriptionsItem label="开始">{{ formatDateTime(currentRun.startedAt) }}</ElDescriptionsItem>
        </ElDescriptions>
        <section class="live-log-panel">
          <header><strong>实时活动流</strong><span>每 1.5 秒刷新 · 最近 80 行</span></header>
          <ol ref="logStream" aria-live="polite">
            <li v-for="item in runLogs" :key="item.id" :data-level="item.level">
              <time>{{ item.createdAt }}</time><i aria-hidden="true" /><span>{{ item.message }}</span>
            </li>
          </ol>
        </section>
        <h3 class="subsection-heading">失败明细</h3>
        <ElTable :data="failures.items" empty-text="当前运行没有失败记录">
          <ElTableColumn prop="externalRecordId" label="外部记录" min-width="150" />
          <ElTableColumn prop="failureStage" label="阶段" width="120" />
          <ElTableColumn prop="errorCategory" label="分类" width="150" />
          <ElTableColumn prop="safeMessage" label="安全错误信息" min-width="240" />
          <ElTableColumn prop="attemptCount" label="尝试" width="70" />
          <ElTableColumn label="可重试" width="80"><template #default="{ row }">{{ row.retryable ? '是' : '否' }}</template></ElTableColumn>
        </ElTable>
        <div v-if="failures.totalPages > 1" class="pagination-row">
          <ElPagination :current-page="failures.page + 1" :page-size="failures.size" :total="failures.totalElements" layout="prev, pager, next" @current-change="(page: number) => loadFailures(page - 1)" />
        </div>
      </template>
    </ElDialog>
  </section>
</template>
