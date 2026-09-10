<script setup lang="ts">
import { ElAlert, ElButton, ElDatePicker, ElDialog, ElInput, ElInputNumber, ElMessage, ElMessageBox, ElOption, ElProgress, ElSelect, ElSwitch, ElTimePicker } from 'element-plus'
import FormField from '@/components/business/FormField.vue'
import SourceEntitySelect from '@/components/business/SourceEntitySelect.vue'
import type { DataTableColumn } from '@/components/business/types'
import { CalendarClock, Play, Plus, Search } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'

import { useDataSources } from '@/composables/useDataSources'
import CountUpNumber from '@/components/CountUpNumber.vue'
import { DataTable, LiveLogPanel, PageHeader, PanelSection, StatusPill } from '@/components/business'
import type { LogEntry } from '@/components/business/types'
import { ApiError, toErrorMessage as defaultErrorMessage } from '@/services/api'
import { crawlApi } from '@/services/business'
import { useSessionStore } from '@/stores/session'
import type { CrawlFailure, CrawlRun, CrawlSchedule, CrawlTask, CrawlTaskParameters, CrawlWindow, PageResponse } from '@/types/api'
import { formatDateTime, splitValues } from '@/utils/format'
import { instantRange, publicationDate, toLocalDateTime } from '@/utils/date'

const session = useSessionStore()
const { hasPermission } = session

function toErrorMessage(error: unknown): string {
  return error instanceof ApiError && error.status === 409 ? error.message : defaultErrorMessage(error)
}

interface TaskForm {
  sourceId: string
  name: string
  publicationDateFrom: string
  publicationDateTo: string
  keyword: string
  authorIds: string[]
  institutionIds: string[]
  dois: string
  orcids: string
  rorIds: string
  updatedFrom: string
  updatedUntil: string
  maxPages: string
  maxRecords: string
}

interface RunLogEntry extends LogEntry {
  level: 'info' | 'success' | 'warning'
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
const runIdInput = ref('')
const controlling = ref('')
let runPollTimer: number | undefined
let runPollFailures = 0
let runLogSequence = 0
const { sources, sourceLoading, sourceError, loadSources, sourceName } = useDataSources()
const savedSchedule = ref<CrawlSchedule | null>(null)
const scheduleLoading = ref(false)
const scheduleReady = ref(false)
let scheduleRequest = 0
const historyDialog = ref(false)
const historyTask = ref<CrawlTask | null>(null)
const historyLoading = ref(false)
let historyRequest = 0
let failureRequest = 0
const history = ref<PageResponse<CrawlRun>>({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const runTask = ref<CrawlTask | null>(null)
const runWindow = ref<CrawlWindow | null>(null)
const canCreate = computed(() => hasPermission('CRAWL_TASK_CREATE'))
const canUpdate = computed(() => hasPermission('CRAWL_TASK_UPDATE'))
const canControl = computed(() => hasPermission('CRAWL_TASK_CONTROL'))
const canSchedule = computed(() => hasPermission('CRAWL_SCHEDULE_MANAGE'))
const form = reactive<TaskForm>(emptyTaskForm())
const authorReady = ref(true)
const institutionReady = ref(true)
const entitySelectionError = '请完成作者、机构的名称选择或回显；未选中的搜索文字请先清空'
const scheduleForm = reactive({
  localTime: '02:00',
  timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai',
  version: '',
  incrementalMode: 'FIXED_SCOPE_REFRESH',
  enabled: true,
})
const selectedSource = computed(() => sources.value.find((source) => source.id === Number(form.sourceId)))
const isCrossref = computed(() => selectedSource.value?.sourceType === 'CROSSREF')
watch([authorReady, institutionReady, isCrossref], ([author, institution, crossref]) => {
  if ((crossref || (author && institution)) && errorMessage.value === entitySelectionError) errorMessage.value = ''
})
const scheduledSource = computed(() => sources.value.find((source) => source.id === scheduling.value?.sourceId))
const incrementalMode = computed(() => scheduledSource.value?.sourceType === 'CROSSREF'
  ? 'CLOSED_INDEX_DATE_WINDOW' : 'ROLLING_PUBLICATION_DATE_WINDOW')

const completionMessages: Record<string, string> = {
  SOURCE_EXHAUSTED: '当前查询范围的来源游标已耗尽；不代表整个学术数据库完整。',
  PAGE_LIMIT: '达到页数上限，范围尚未采集完整。请缩小日期范围或增加筛选条件后分批采集。',
  RECORD_LIMIT: '达到记录上限，范围尚未采集完整。请缩小日期范围或增加筛选条件后分批采集。',
  RETRY_BATCH_COMPLETED: '本次失败记录重试批次已完成。',
  QUOTA_EXHAUSTED: '来源每日额度耗尽，已保留检查点；额度恢复后自动继续，最多三次。',
  QUOTA_RETRY_LIMIT: '已用完三次额度自动恢复机会。请检查来源额度后重新安排采集。',
  USER_PAUSED: '已由用户暂停，等待手动恢复。',
  USER_CANCELLED: '已由用户取消，已提交数据保留。',
  BATCH_FAILED: '批次执行失败，请结合失败明细与操作日志排查。',
}
const completionMessage = computed(() => {
  const reason = currentRun.value?.completionReason
  if (reason === 'BATCH_FAILED' && !currentRun.value?.batchJobExecutionId) {
    return '采集批次未能启动，尚未发出来源请求。请查看下方系统失败明细；排查后使用任务的“立即执行”重新启动。平台审计已移至统一门户的日志管理。'
  }
  return reason ? completionMessages[reason] ?? '结束原因未知，请刷新后查看。' : ''
})

const runProgress = computed(() => {
  const run = currentRun.value
  if (!run) return 0
  if (['SUCCEEDED', 'COMPLETED'].includes(run.status)) return 100
  const taskLimit = runWindow.value?.scope.maxRecords ?? runTask.value?.parameters.maxRecords
  if (!taskLimit) return 0
  return Math.min(99, Math.round(run.readCount / taskLimit * 100))
})

const taskColumns: DataTableColumn<CrawlTask>[] = [
  { accessorKey: 'name', header: '任务名称', enableSorting: false },
  { id: 'sourceName', accessorFn: (row) => sourceName(row.sourceId), header: '数据源', enableSorting: false, meta: { width: '110px' } },
  { id: 'scope', accessorFn: (row) => row.parameters.keyword || '未限定关键词', header: '采集范围', enableSorting: false },
  { id: 'limit', accessorFn: (row) => `${row.parameters.maxPages} 页 / ${row.parameters.maxRecords} 条`, header: '上限', enableSorting: false, meta: { width: '140px' } },
  { id: 'enabled', accessorFn: (row) => (row.enabled ? '启用' : '停用'), header: '状态', enableSorting: false, meta: { width: '90px' } },
  { id: 'updatedAt', accessorFn: (row) => formatDateTime(row.updatedAt), header: '更新时间', enableSorting: false, meta: { width: '160px' } },
  { id: 'actions', header: '操作', enableSorting: false, meta: { width: '220px' } },
]

const failureColumns: DataTableColumn<CrawlFailure>[] = [
  { accessorKey: 'externalRecordId', header: '外部记录', enableSorting: false },
  { accessorKey: 'failureStage', header: '阶段', enableSorting: false, meta: { width: '110px' } },
  { accessorKey: 'errorCategory', header: '分类', enableSorting: false, meta: { width: '140px' } },
  { accessorKey: 'safeMessage', header: '安全错误信息', enableSorting: false },
  { accessorKey: 'attemptCount', header: '尝试', enableSorting: false, meta: { width: '70px' } },
  { id: 'retryable', accessorFn: (row) => (row.retryable ? '是' : '否'), header: '可重试', enableSorting: false, meta: { width: '80px' } },
]

function emptyTaskForm(): TaskForm {
  return {
    sourceId: '1', name: '', publicationDateFrom: '', publicationDateTo: '', keyword: '',
    authorIds: [], institutionIds: [], dois: '', orcids: '', rorIds: '',
    updatedFrom: '', updatedUntil: '', maxPages: '1', maxRecords: '100',
  }
}

function toParameters(): CrawlTaskParameters {
  const range = isCrossref.value ? instantRange(form.updatedFrom, form.updatedUntil) : { from: null, to: null }
  const from = publicationDate(form.publicationDateFrom)
  const to = publicationDate(form.publicationDateTo)
  if (from && to && from > to) throw new RangeError('出版日期起不能晚于出版日期止')
  if (!Number.isInteger(Number(form.maxPages)) || Number(form.maxPages) < 1 || Number(form.maxPages) > 5
    || !Number.isInteger(Number(form.maxRecords)) || Number(form.maxRecords) < 1 || Number(form.maxRecords) > 500) {
    throw new RangeError('最大页数须为 1–5，最大记录数须为 1–500 的整数')
  }
  return {
    publicationDateFrom: from,
    publicationDateTo: to,
    keyword: form.keyword.trim() || null,
    authorIds: isCrossref.value ? [] : [...form.authorIds],
    institutionIds: isCrossref.value ? [] : [...form.institutionIds],
    dois: isCrossref.value ? splitValues(form.dois) : [],
    orcids: isCrossref.value ? splitValues(form.orcids) : [],
    rorIds: isCrossref.value ? splitValues(form.rorIds) : [],
    updatedFrom: range.from ?? null,
    updatedUntil: range.to ?? null,
    maxPages: Number(form.maxPages),
    maxRecords: Number(form.maxRecords),
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

const historyColumns: DataTableColumn<CrawlRun>[] = [
  { accessorKey: 'runNumber', header: '运行编号', enableSorting: false },
  { accessorKey: 'status', header: '状态', enableSorting: false },
  { accessorKey: 'readCount', header: '读取', enableSorting: false },
  { accessorKey: 'failureCount', header: '失败记录', enableSorting: false },
  { id: 'startedAt', accessorFn: (row) => formatDateTime(row.startedAt), header: '开始时间', enableSorting: false },
  { id: 'actions', header: '操作', enableSorting: false },
]

/** 读取任务上限与实际窗口，运行详情不依赖当前列表页。 */
async function loadRunContext(): Promise<void> {
  const run = currentRun.value
  if (!run) return
  runTask.value = null
  runWindow.value = null
  try {
    const [task, window] = await Promise.all([crawlApi.task(run.taskId), crawlApi.window(run.id)])
    if (currentRun.value?.id !== run.id) return
    runTask.value = task
    runWindow.value = window ?? null
  } catch (error) {
    if (currentRun.value?.id === run.id) errorMessage.value = toErrorMessage(error)
  }
}

async function openHistory(task: CrawlTask): Promise<void> {
  history.value = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
  historyTask.value = task
  historyDialog.value = true
  errorMessage.value = ''
  await loadHistory()
}

/** 运行历史分页读取，过期响应不得覆盖另一任务的历史。 */
async function loadHistory(page = 0): Promise<void> {
  const request = ++historyRequest
  const task = historyTask.value
  if (!task) return
  historyLoading.value = true
  try {
    const result = await crawlApi.runs(task.id, page, history.value.size)
    if (request === historyRequest && historyDialog.value) history.value = result
  } catch (error) {
    if (request === historyRequest) errorMessage.value = toErrorMessage(error)
  } finally {
    if (request === historyRequest) historyLoading.value = false
  }
}

async function viewHistoryRun(run: CrawlRun): Promise<void> {
  runIdInput.value = String(run.id)
  await findRun()
}

/** 移除计划保留采集数据和运行历史。 */
async function deleteSchedule(): Promise<void> {
  const schedule = savedSchedule.value
  if (!schedule || saving.value) return
  try {
    await ElMessageBox.confirm('移除每日计划后将停止自动触发，已采集数据和运行历史会保留。', '移除每日计划', {
      confirmButtonText: '移除计划', cancelButtonText: '取消', type: 'warning',
    })
  } catch (action) {
    if (action === 'cancel' || action === 'close') return
    errorMessage.value = toErrorMessage(action)
    return
  }
  saving.value = true
  try {
    await crawlApi.deleteSchedule(schedule.taskId, schedule.version)
    savedSchedule.value = null
    scheduleDialog.value = false
    ElMessage.success('每日计划已移除')
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    saving.value = false
  }
}

function openCreate(): void {
  errorMessage.value = ''
  editing.value = null
  Object.assign(form, emptyTaskForm())
  form.sourceId = String(sources.value.find((source) => source.enabled)?.id ?? '')
  taskDialog.value = true
}

function openHistoricalRefresh(): void {
  openCreate()
  const now = new Date()
  const year = now.getFullYear() - 1
  const month = now.getMonth()
  const monthLabel = String(month + 1).padStart(2, '0')
  form.name = `历史复查 ${year}-${monthLabel}`
  form.publicationDateFrom = `${year}-${monthLabel}-01`
  form.publicationDateTo = `${year}-${monthLabel}-${new Date(year, month + 1, 0).getDate()}`
}

function openEdit(task: CrawlTask): void {
  editing.value = task
  Object.assign(form, {
    sourceId: String(task.sourceId),
    name: task.name,
    publicationDateFrom: task.parameters.publicationDateFrom ?? '',
    publicationDateTo: task.parameters.publicationDateTo ?? '',
    keyword: task.parameters.keyword ?? '',
    authorIds: [...task.parameters.authorIds],
    institutionIds: [...task.parameters.institutionIds],
    dois: task.parameters.dois.join(', '),
    orcids: task.parameters.orcids.join(', '),
    rorIds: task.parameters.rorIds.join(', '),
    updatedFrom: toLocalDateTime(task.parameters.updatedFrom),
    updatedUntil: toLocalDateTime(task.parameters.updatedUntil),
    maxPages: String(task.parameters.maxPages),
    maxRecords: String(task.parameters.maxRecords),
  })
  taskDialog.value = true
}

async function saveTask(): Promise<void> {
  if (saving.value) return
  if (!form.name.trim()) {
    errorMessage.value = '任务名称不能为空'
    return
  }
  if (!selectedSource.value?.enabled) {
    errorMessage.value = '请选择已启用的数据源'
    return
  }
  if (!isCrossref.value && (!authorReady.value || !institutionReady.value)) {
    errorMessage.value = entitySelectionError
    return
  }
  saving.value = true
  errorMessage.value = ''
  try {
    if (editing.value) {
      await crawlApi.updateTask(editing.value, form.name.trim(), toParameters())
    } else {
      await crawlApi.createTask({ sourceId: Number(form.sourceId), name: form.name.trim(), parameters: toParameters() })
    }
    taskDialog.value = false
    ElMessage.success(editing.value ? '采集任务已更新' : '采集任务已创建')
    await load(tasks.value.page)
  } catch (error) {
    errorMessage.value = error instanceof RangeError ? error.message : toErrorMessage(error)
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
    runIdInput.value = String(currentRun.value.id)
    runDialog.value = true
    await loadRunContext()
    await loadFailures()
    scheduleRunPoll()
    ElMessage.success('采集运行已进入队列')
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  }
}

/** 每次打开从服务器读取计划，页面刷新和其他用户修改后仍能正确校验版本。 */
async function openSchedule(task: CrawlTask): Promise<void> {
  const request = ++scheduleRequest
  scheduling.value = task
  savedSchedule.value = null
  errorMessage.value = ''
  scheduleLoading.value = true
  scheduleReady.value = false
  scheduleDialog.value = true
  try {
    const value = await crawlApi.getSchedule(task.id)
    if (request !== scheduleRequest || !scheduleDialog.value) return
    savedSchedule.value = value ?? null
    Object.assign(scheduleForm, {
      localTime: value?.localTime.slice(0, 5) ?? '02:00',
      timeZone: value?.timeZone ?? Intl.DateTimeFormat().resolvedOptions().timeZone ?? 'Asia/Shanghai',
      version: value ? String(value.version) : '',
      incrementalMode: value?.incrementalMode ?? 'FIXED_SCOPE_REFRESH',
      enabled: value?.enabled ?? true,
    })
    scheduleReady.value = true
  } catch (error) {
    if (request === scheduleRequest) errorMessage.value = toErrorMessage(error)
  } finally {
    if (request === scheduleRequest) scheduleLoading.value = false
  }
}

async function saveSchedule(): Promise<void> {
  if (!scheduling.value || !scheduleReady.value || scheduleLoading.value || saving.value) return
  saving.value = true
  try {
    const result: CrawlSchedule = await crawlApi.schedule(
      scheduling.value.id,
      scheduleForm.localTime,
      scheduleForm.timeZone,
      scheduleForm.version === '' ? undefined : Number(scheduleForm.version),
      scheduleForm.incrementalMode,
      scheduleForm.enabled,
    )
    savedSchedule.value = result
    scheduleDialog.value = false
    ElMessage.success(result.enabled ? '每日调度已保存，下次执行：' + formatDateTime(result.nextFireAt) : '每日调度已停用')
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    saving.value = false
  }
}

async function findRun(): Promise<void> {
  const id = Number(runIdInput.value)
  if (!Number.isInteger(id) || id < 1) {
    errorMessage.value = '请输入有效的运行编号'
    return
  }
  errorMessage.value = ''
  try {
    currentRun.value = await crawlApi.run(id)
    await loadRunContext()
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
  const runId = currentRun.value?.id
  if (!runId) return
  const request = ++failureRequest
  try {
    const result = await crawlApi.failures(runId, page, failures.value.size)
    if (request === failureRequest && currentRun.value?.id === runId) failures.value = result
  } catch (error) {
    if (request === failureRequest) errorMessage.value = toErrorMessage(error)
  }
}

async function control(action: 'pause' | 'resume' | 'cancel' | 'retry-failures' | 'retry-run'): Promise<void> {
  if (!currentRun.value || controlling.value) return
  controlling.value = action
  try {
    currentRun.value = await crawlApi.control(currentRun.value.id, action)
    await loadRunContext()
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

/** 活动流只保留最近 80 行，LiveLogPanel 负责平滑滚动到底部。 */
function appendRunLog(level: RunLogEntry['level'], message: string): void {
  runLogs.value = [
    ...runLogs.value,
    {
      id: ++runLogSequence,
      level,
      message,
      time: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
    },
  ].slice(-80)
}

function isRunActive(status: string): boolean {
  return ['PENDING', 'QUEUED', 'RUNNING', 'PAUSING', 'CANCELLING', 'CANCEL_REQUESTED', 'RETRYING'].includes(status)
}

function clearRunPoll(): void {
  if (runPollTimer !== undefined) {
    window.clearTimeout(runPollTimer)
    runPollTimer = undefined
  }
}

function scheduleRunPoll(): void {
  clearRunPoll()
  if (!runDialog.value || !currentRun.value) return
  const deferred = currentRun.value.status === 'PAUSED' && Boolean(currentRun.value.deferredUntil)
  if (!isRunActive(currentRun.value.status) && !deferred) return
  runPollTimer = window.setTimeout(() => void pollRun(), deferred ? 30_000 : 1500)
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
    if (next.failureCount !== previous.failureCount || next.status !== previous.status) await loadFailures()
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

onMounted(() => { void load(); void loadSources() })
watch(runDialog, (visible) => {
  if (visible) scheduleRunPoll()
  else clearRunPoll()
})
onBeforeUnmount(() => {
  historyRequest++
  failureRequest++
  scheduleRequest++
  historyDialog.value = false
  scheduleDialog.value = false
  runDialog.value = false
  clearRunPoll()
})
</script>

<template>
  <section class="page-stack">
    <PageHeader
      title="采集任务"
      description="定义受控采集范围，触发或调度任务，并按运行编号检查处理计数和失败证据。"
    >
      <template #actions>
        <ElButton v-if="canCreate" @click="openHistoricalRefresh" plain><CalendarClock class="size-4" />历史复查</ElButton>
        <ElButton v-if="canCreate" @click="openCreate" type="primary"><Plus class="size-4" />新建采集任务</ElButton>
      </template>
    </PageHeader>

    <section class="workflow-toolbar" aria-label="运行追踪">
      <div><h2>运行追踪</h2><p>通过运行编号定位进度、恢复安排与失败证据</p></div>
      <div class="workflow-toolbar__fields">
        <ElInputNumber :model-value="runIdInput === '' ? undefined : Number(runIdInput)" @update:model-value="(value) => { runIdInput = value == null ? '' : String(value) }"  :min="1" placeholder="运行编号" aria-label="运行编号" @keydown.enter="findRun" controls-position="right" style="width: min(100%, 200px)" />
        <ElButton @click="findRun" plain><Search class="size-4" />查询运行</ElButton>
      </div>
    </section>

    <p class="context-note">每次运行最多 5 页 / 500 条 · 支持固定范围复查与增量窗口推进 · 达到上限后仍需核对完整性</p>

    <ElAlert v-if="errorMessage && !taskDialog && !scheduleDialog && !runDialog && !historyDialog" type="error" :closable="false" show-icon><template #title>{{ errorMessage }}</template></ElAlert>

    <ElAlert v-if="sourceError" type="error" :closable="false" :title="sourceError" show-icon>
      <ElButton @click="loadSources" :loading="sourceLoading" link>重试读取数据源</ElButton>
    </ElAlert>

    <PanelSection title="任务定义" :subtitle="`共 ${tasks.totalElements} 个`">
      <DataTable fill
        :columns="taskColumns"
        :data="tasks.items"
        :loading="loading"
        :page="tasks.page"
        :size="tasks.size"
        :total="tasks.totalElements"
        empty-text="暂无采集任务"
        empty-description="创建采集任务并指定来源和研究范围，随后可在此执行或配置每日计划。"
        :get-row-id="(row) => String(row.id)"
        @update:page="load"
      >
        <template #cell-sourceName="{ row }">{{ sourceName(row.sourceId) }}</template>
        <template #cell-name="{ row }">
          <strong class="block text-sm font-medium">{{ row.name }}</strong>
          <span class="mono-evidence text-muted-foreground">任务 #{{ row.id }}</span>
        </template>
        <template #cell-scope="{ row }">
          <span class="text-foreground">{{ row.parameters.keyword || '未限定关键词' }}</span>
          <span class="mt-0.5 block text-xs text-muted-foreground">
            {{ row.parameters.publicationDateFrom || '起始不限' }} — {{ row.parameters.publicationDateTo || '结束不限' }}
          </span>
        </template>
        <template #cell-enabled="{ row }">
          <StatusPill :status="row.enabled ? 'ACTIVE' : 'DISABLED'" />
        </template>
        <template #cell-actions="{ row }">
          <div class="flex flex-wrap items-center gap-1">
            <ElButton size="small" link type="primary" @click="openHistory(row)">运行历史</ElButton>
            <ElButton v-if="canUpdate" size="small" class="h-auto p-0" @click="openEdit(row)" link type="primary">编辑</ElButton>
            <ElButton v-if="canControl" size="small" class="h-auto p-0" @click="trigger(row)" link type="primary">
              <Play class="size-3.5" />立即执行
            </ElButton>
            <ElButton v-if="canSchedule" size="small" class="h-auto p-0" @click="openSchedule(row)" link type="primary">
              <CalendarClock class="size-3.5" />调度
            </ElButton>
          </div>
        </template>
      </DataTable>
    </PanelSection>

    <!-- 任务编辑 -->
    <ElDialog v-model="taskDialog" width="min(768px, calc(100vw - 32px))" append-to-body destroy-on-close class="aacv-form-dialog" body-class="aacv-dialog-body">
        <ElAlert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" show-icon />

          <template #header="{ titleId }"><h2 :id="titleId">{{ editing ? '编辑采集任务' : '新建采集任务' }}</h2></template>
          <p class="mb-4 text-sm text-muted-foreground">配置采集范围与数量上限。{{ isCrossref ? '多个 DOI、ORCID 或 ROR 使用逗号分隔。' : '作者、机构可按名称搜索后多选。' }}</p>

        <form class="grid gap-4" novalidate @submit.prevent="saveTask">
          <p class="text-sm text-muted-foreground">历史复查会重新读取指定出版范围，补齐旧成果后续更新。默认提供去年同月范围，请结合作者、机构或关键词缩小范围；达到上限后需继续拆分任务。</p>
          <div class="grid grid-cols-1 items-start gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <FormField for="sourceName" label="数据源名称">
              <ElSelect id="sourceName" v-model="form.sourceId" :loading="sourceLoading" :disabled="Boolean(editing) || sourceLoading" placeholder="选择数据源" style="width: 100%">
                <ElOption v-for="source in sources" :key="source.id" :value="String(source.id)" :label="sourceName(source.id) + (source.enabled ? '' : '（已停用）')" :disabled="!source.enabled" />
              </ElSelect>
              <p v-if="!sourceLoading && !sources.length" class="text-xs text-muted-foreground">请先在数据源页面配置并启用 OpenAlex 或 Crossref。</p>
            </FormField>
            <FormField class="sm:col-span-2" for="taskName" label="任务名称"><ElInput id="taskName" v-model="form.name" :maxlength="128"  /></FormField>
            <FormField for="dateFrom" label="出版日期起"><ElDatePicker id="dateFrom" v-model="form.publicationDateFrom" type="date"  value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" /></FormField>
            <FormField for="dateTo" label="出版日期止"><ElDatePicker id="dateTo" v-model="form.publicationDateTo" type="date"  value-format="YYYY-MM-DD" format="YYYY-MM-DD" style="width: 100%" /></FormField>
            <FormField for="keyword" label="关键词（可选）" :hint="isCrossref
              ? '在 Crossref 中检索成果元数据，与其余筛选条件共同限定范围；留空仅按其他条件采集。'
              : '在 OpenAlex 的标题、摘要及可检索全文中搜索，与日期、作者、机构共同限定范围；留空仅按其他条件采集。'">
              <ElInput id="keyword" v-model="form.keyword" :maxlength="200" placeholder="例如：graph neural networks" />
            </FormField>
            <FormField v-if="taskDialog && selectedSource && !isCrossref" for="authorNames" label="作者名称">
              <SourceEntitySelect id="authorNames" v-model="form.authorIds" :source-id="selectedSource.id" kind="authors" @ready="authorReady = $event" />
            </FormField>
            <FormField v-if="taskDialog && selectedSource && !isCrossref" for="institutionNames" label="机构名称">
              <SourceEntitySelect id="institutionNames" v-model="form.institutionIds" :source-id="selectedSource.id" kind="institutions" @ready="institutionReady = $event" />
            </FormField>
            <FormField v-if="isCrossref" for="dois" label="DOI（逗号分隔）"><ElInput id="dois" v-model="form.dois"  /></FormField>
            <FormField v-if="isCrossref" for="orcids" label="ORCID（逗号分隔）"><ElInput id="orcids" v-model="form.orcids"  /></FormField>
            <FormField v-if="isCrossref" for="rorIds" label="ROR ID（逗号分隔）"><ElInput id="rorIds" v-model="form.rorIds"  /></FormField>
            <FormField v-if="isCrossref" for="updatedFrom" label="外部更新时间起"><ElDatePicker id="updatedFrom" v-model="form.updatedFrom" type="datetime"  value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" style="width: 100%" /></FormField>
            <FormField v-if="isCrossref" for="updatedUntil" label="外部更新时间止"><ElDatePicker id="updatedUntil" v-model="form.updatedUntil" type="datetime"  value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" style="width: 100%" /></FormField>
            <FormField for="maxPages" label="最大页数"><ElInputNumber id="maxPages" :model-value="form.maxPages === '' ? undefined : Number(form.maxPages)" @update:model-value="(value) => { form.maxPages = value == null ? '' : String(value) }"  :min="1" :max="5"  controls-position="right" style="width: 100%" /></FormField>
            <FormField for="maxRecords" label="最大记录数"><ElInputNumber id="maxRecords" :model-value="form.maxRecords === '' ? undefined : Number(form.maxRecords)" @update:model-value="(value) => { form.maxRecords = value == null ? '' : String(value) }"  :min="1" :max="500"  controls-position="right" style="width: 100%" /></FormField>
          </div>
          <div class="mt-4 flex flex-wrap justify-end gap-2">
            <ElButton native-type="button" @click="taskDialog = false" plain>取消</ElButton>
            <ElButton native-type="submit" :loading="saving" type="primary">保存</ElButton>
          </div>
        </form>
      </ElDialog>

    <!-- 调度配置 -->
    <ElDialog v-model="scheduleDialog" width="min(448px, calc(100vw - 32px))" append-to-body destroy-on-close class="aacv-form-dialog" body-class="aacv-dialog-body">
        <ElAlert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" show-icon />

          <template #header="{ titleId }"><h2 :id="titleId">配置每日调度</h2></template>
          <p class="mb-4 text-sm text-muted-foreground">按所选时区每日触发。固定复查重复原范围；增量模式每次处理一个窗口，成功后推进，超限时缩小下一次窗口。</p>

        <form class="grid gap-4" novalidate @submit.prevent="saveSchedule">
          <FormField for="localTime" label="本地时间"><ElTimePicker id="localTime" v-model="scheduleForm.localTime"   value-format="HH:mm" format="HH:mm" style="width: 100%" /></FormField>
          <FormField for="timeZone" label="IANA 时区"><ElInput id="timeZone" v-model="scheduleForm.timeZone" placeholder="Asia/Shanghai"  /></FormField>
          <FormField for="scheduleMode" label="采集模式"><ElSelect id="scheduleMode" v-model="scheduleForm.incrementalMode" style="width: 100%">
            <ElOption label="固定范围复查" value="FIXED_SCOPE_REFRESH" />
            <ElOption :label="scheduledSource?.sourceType === 'CROSSREF' ? '按索引更新时间增量采集' : '按出版日期推进采集'" :value="incrementalMode" />
          </ElSelect></FormField>
          <p v-if="scheduleForm.incrementalMode !== 'FIXED_SCOPE_REFRESH'" class="text-sm text-muted-foreground">
            {{ scheduledSource?.sourceType === 'CROSSREF'
              ? '从任务的外部更新时间起开始，持续推进至当前时间前 5 分钟。任务中的外部更新时间止只用于固定复查。'
              : '从任务的出版日期起推进至昨天，出版日期止只用于固定复查。旧论文的后续更新仍需固定范围复查。' }}
            每次最多处理 7 天；最小窗口仍超限时需增加筛选条件。保存后“立即执行”也按此模式推进。
          </p>
          <FormField for="scheduleEnabled" label="启用每日计划"><ElSwitch id="scheduleEnabled" v-model="scheduleForm.enabled" /></FormField>
          <p v-if="savedSchedule?.enabled" class="text-sm text-muted-foreground">下次执行：{{ formatDateTime(savedSchedule.nextFireAt) }}</p>
          <p v-if="scheduleLoading" role="status">正在读取已有计划…</p>
          <ElButton v-if="!scheduleReady && !scheduleLoading && scheduling" @click="openSchedule(scheduling)" plain>重新读取计划</ElButton>
          <div class="mt-4 flex flex-wrap justify-end gap-2">
            <ElButton native-type="button" @click="scheduleDialog = false" plain>取消</ElButton>
            <ElButton v-if="savedSchedule" native-type="button" :disabled="saving || !scheduleReady" @click="deleteSchedule" type="danger" plain>移除计划</ElButton>
            <ElButton native-type="submit" :loading="saving || scheduleLoading" :disabled="!scheduleReady" type="primary">保存调度</ElButton>
          </div>
        </form>
      </ElDialog>

    <ElDialog v-model="historyDialog" title="运行历史" width="min(960px, calc(100vw - 32px))" append-to-body destroy-on-close>
      <ElAlert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" show-icon />
      <p>{{ historyTask?.name }} · {{ historyTask ? sourceName(historyTask.sourceId) : '' }}</p>
      <DataTable :columns="historyColumns" :data="history.items" :loading="historyLoading" :page="history.page" :size="history.size" :total="history.totalElements"
        :get-row-id="(row) => String(row.id)" empty-text="尚无运行记录" @update:page="loadHistory">
        <template #cell-actions="{ row }"><ElButton link type="primary" @click="viewHistoryRun(row)">查看详情</ElButton></template>
      </DataTable>
    </ElDialog>

    <!-- 运行详情 -->
    <ElDialog v-model="runDialog" width="min(896px, calc(100vw - 32px))" append-to-body destroy-on-close class="aacv-form-dialog" body-class="aacv-dialog-body">
        <ElAlert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" show-icon />

          <template #header="{ titleId }"><h2 :id="titleId">采集运行详情</h2></template>
          <p class="mb-4 text-sm text-muted-foreground">查看运行处理量、覆盖边界与恢复安排。</p>

        <template v-if="currentRun">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div class="flex items-center gap-2.5">
              <StatusPill :status="currentRun.status" pulse />
              <strong class="text-sm font-semibold">{{ currentRun.status }}</strong>
              <span class="mono-evidence text-xs text-muted-foreground">{{ currentRun.runNumber }}</span>
            </div>
            <div v-if="canControl" class="flex flex-wrap gap-2">
              <ElButton v-if="currentRun.status === 'RUNNING'" size="small" :loading="controlling === 'pause'" @click="control('pause')" plain>暂停</ElButton>
              <ElButton v-if="currentRun.status === 'PAUSED' && !currentRun.deferredUntil" size="small" :loading="controlling === 'resume'" @click="control('resume')" plain>恢复</ElButton>
              <ElButton v-if="currentRun.status === 'PAUSED' && currentRun.deferredUntil" size="small" :loading="controlling === 'pause'" @click="control('pause')" plain>停止自动恢复</ElButton>
              <ElButton v-if="['PENDING', 'RUNNING', 'PAUSING', 'PAUSED'].includes(currentRun.status)" size="small" :loading="controlling === 'cancel'" @click="control('cancel')" type="danger">取消</ElButton>
              <ElButton v-if="currentRun.status === 'FAILED' && currentRun.completionReason === 'BATCH_FAILED'" size="small" :loading="controlling === 'retry-run'" @click="control('retry-run')" plain>从检查点重试</ElButton>
              <ElButton v-if="currentRun.failureCount > 0 && ['PARTIAL_SUCCESS', 'FAILED'].includes(currentRun.status)" plain size="small" :loading="controlling === 'retry-failures'" @click="control('retry-failures')">重试失败项</ElButton>
            </div>
          </div>

          <ElAlert v-if="completionMessage" type="info" :closable="false" show-icon>
            <template #title>运行说明</template>
            <div>
              <p>{{ completionMessage }}</p>
              <p v-if="currentRun.deferredUntil">预计 {{ formatDateTime(currentRun.deferredUntil) }} 后恢复 · 已安排 {{ currentRun.quotaDeferrals ?? 0 }}/3 次；停用的任务或来源不会自动恢复。</p>
            </div>
          </ElAlert>
          <p v-if="runWindow" class="text-sm text-muted-foreground">本次实际窗口：{{ formatDateTime(runWindow.start) }} — {{ formatDateTime(runWindow.end) }}</p>
          <p class="text-xs text-muted-foreground">下方百分比表示本次任务执行进度，不表示来源数据覆盖率。</p>
          <div class="flex items-center gap-3">
            <ElProgress :show-text="false" :percentage="runProgress" class="flex-1" :aria-label="`采集进度 ${runProgress}%`" />
            <b class="text-sm tabular-nums text-muted-foreground">{{ runProgress }}%</b>
          </div>

          <dl class="run-metrics">
            <div v-for="metric in [
              { label: '读取', value: currentRun.readCount },
              { label: '解析', value: currentRun.parsedCount },
              { label: '新增', value: currentRun.createdCount },
              { label: '更新', value: currentRun.updatedCount },
              { label: '重复', value: currentRun.duplicateCount },
              { label: '失败', value: currentRun.failureCount },
              { label: '请求', value: currentRun.requestCount },
            ]" :key="metric.label" class="p-3">
              <dt class="text-xs text-muted-foreground">{{ metric.label }}</dt>
              <dd class="text-xl font-semibold tabular-nums"><CountUpNumber :value="metric.value" /></dd>
            </div>
            <div class="p-3">
              <dt class="text-xs text-muted-foreground">开始</dt>
              <dd class="text-sm">{{ formatDateTime(currentRun.startedAt) }}</dd>
            </div>
          </dl>

          <div class="space-y-1.5">
            <div class="flex items-center justify-between">
              <strong class="text-sm font-medium">实时活动流</strong>
              <span class="text-xs text-muted-foreground">运行中每 1.5 秒刷新，额度等待每 30 秒刷新 · 最近 80 行</span>
            </div>
            <LiveLogPanel :entries="runLogs" max-height="180px" />
          </div>

          <div class="space-y-2">
            <h3 class="text-sm font-semibold">失败明细</h3>
            <DataTable
              :columns="failureColumns"
              :data="failures.items"
              :page="failures.page"
              :size="failures.size"
              :total="failures.totalElements"
              empty-text="当前运行没有失败记录"
              :get-row-id="(row) => String(row.id)"
              dense
              @update:page="loadFailures"
            />
          </div>
        </template>
      </ElDialog>
  </section>
</template>
