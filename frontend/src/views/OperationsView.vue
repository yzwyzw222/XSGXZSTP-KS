<script setup lang="ts">
import type { ColumnDef } from '@tanstack/vue-table'
import { AlertOctagon, RefreshCw } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { DataTable, PageHeader, StatCard, StatusPill } from '@/components/business'
import { Alert, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { FormItem, FormLabel } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import { toast } from '@/components/ui/sonner'
import { toErrorMessage } from '@/services/api'
import { graphApi, operationsApi } from '@/services/business'
import {
  fetchGraphHealth, fetchLiveness, fetchReadiness, type HealthResponse, type HealthStatus,
} from '@/services/health'
import { hasPermission } from '@/services/session'
import type {
  AlertEvent, AlertStatus, AlertType, AuditLog, GraphEvent, GraphMaintenanceRun,
  GraphOutboxStatus, GraphSyncStatus, OperationsOverview, PageResponse,
} from '@/types/api'
import { formatDateTime, formatNumber } from '@/utils/format'

type ProbeKey = 'liveness' | 'readiness' | 'graph'
interface ProbeState { status: HealthStatus; detail: string }

function emptyPage<T>(): PageResponse<T> {
  return { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
}

const loading = ref(false)
const actionLoading = ref('')
const partialError = ref('')
const activeTab = ref('alerts')
const overview = ref<OperationsOverview | null>(null)
const syncStatus = ref<GraphSyncStatus | null>(null)
const alerts = ref<PageResponse<AlertEvent>>(emptyPage())
const graphEvents = ref<PageResponse<GraphEvent>>(emptyPage())
const maintenanceRuns = ref<PageResponse<GraphMaintenanceRun>>(emptyPage())
const audits = ref<PageResponse<AuditLog>>(emptyPage())
const probes = reactive<Record<ProbeKey, ProbeState>>({
  liveness: { status: 'UNKNOWN', detail: '尚未检查' },
  readiness: { status: 'UNKNOWN', detail: '尚未检查' },
  graph: { status: 'UNKNOWN', detail: '尚未检查' },
})
const alertFilters = reactive<{ status: string; type: string }>({ status: 'OPEN', type: 'ALL' })
const graphEventStatus = ref<string>('ALL')
const acknowledgeVisible = ref(false)
const selectedAlert = ref<AlertEvent | null>(null)
const acknowledgeReason = ref('')
const rebuildVisible = ref(false)
const rebuildConfirmation = ref('')

const alertTypes: Array<{ value: AlertType; label: string }> = [
  { value: 'CRAWL_CONSECUTIVE_FAILURES', label: '连续采集失败' },
  { value: 'PARSE_SUCCESS_RATE_DROP', label: '解析成功率下降' },
  { value: 'GRAPH_SYNC_BACKLOG', label: '图同步积压' },
]
const graphStatuses: GraphOutboxStatus[] = ['PENDING', 'PROCESSING', 'SUCCEEDED', 'DEAD']

const healthCards: Array<{ key: ProbeKey; label: string }> = [
  { key: 'liveness', label: '应用存活' },
  { key: 'readiness', label: 'MySQL 就绪' },
  { key: 'graph', label: 'Neo4j 独立状态' },
]

const metricCards = computed(() => [
  { label: '活动采集', value: overview.value?.activeCrawlRunCount ?? 0, note: '运行中或暂停请求处理中', tone: 'blue' as const },
  { label: '近期失败', value: overview.value?.recentCrawlFailureCount ?? 0, note: '近 24 小时未解决', tone: 'amber' as const },
  { label: '待处理图事件', value: overview.value?.graphPendingCount ?? 0, note: `最老 ${formatNumber(syncStatus.value?.oldestPendingAgeSeconds)} 秒`, tone: 'cyan' as const },
  { label: '处理中图事件', value: overview.value?.graphProcessingCount ?? 0, note: '受租约保护', tone: 'green' as const },
  { label: '图死信', value: overview.value?.graphDeadCount ?? 0, note: '需要人工判断后重放', tone: 'rose' as const },
  { label: '未确认告警', value: overview.value?.openAlertCount ?? 0, note: '系统内事件', tone: 'rose' as const },
])

const alertColumns: ColumnDef<AlertEvent, any>[] = [
  { accessorKey: 'severity', header: '级别', enableSorting: false, meta: { width: '90px' } },
  { id: 'alert', accessorFn: (row) => `${alertTypeLabel(row.type)} · ${row.summary}`, header: '告警', enableSorting: false },
  { id: 'subject', accessorFn: (row) => `${row.subjectType} ${row.subjectId ?? ''}`, header: '主体', enableSorting: false, meta: { width: '150px' } },
  { accessorKey: 'occurrenceCount', header: '次数', enableSorting: false, meta: { width: '70px' } },
  { id: 'lastDetected', accessorFn: (row) => formatDateTime(row.lastDetectedAt), header: '末次检测', enableSorting: false, meta: { width: '170px' } },
  { id: 'statusAction', header: '状态/操作', enableSorting: false, meta: { width: '140px' } },
]
const eventColumns: ColumnDef<GraphEvent, any>[] = [
  { accessorKey: 'status', header: '状态', enableSorting: false, meta: { width: '110px' } },
  { accessorKey: 'eventId', header: '事件', enableSorting: false },
  { accessorKey: 'attempts', header: '尝试', enableSorting: false, meta: { width: '70px' } },
  { id: 'error', accessorFn: (row) => row.errorSummary ?? row.errorCode ?? '—', header: '错误摘要', enableSorting: false },
  { id: 'next', accessorFn: (row) => formatDateTime(row.nextAttemptAt), header: '下次执行', enableSorting: false, meta: { width: '170px' } },
  { id: 'actions', header: '操作', enableSorting: false, meta: { width: '90px' } },
]
const maintenanceColumns: ColumnDef<GraphMaintenanceRun, any>[] = [
  { id: 'run', accessorFn: (row) => `#${row.id} · ${maintenanceTypeLabel(row.runType)}`, header: '运行', enableSorting: false },
  { accessorKey: 'status', header: '状态', enableSorting: false, meta: { width: '110px' } },
  { accessorKey: 'scannedCount', header: '扫描', enableSorting: false, meta: { width: '90px' } },
  { accessorKey: 'repairedCount', header: '修复', enableSorting: false, meta: { width: '90px' } },
  { accessorKey: 'differenceCount', header: '差异', enableSorting: false, meta: { width: '90px' } },
  { id: 'updatedAt', accessorFn: (row) => formatDateTime(row.updatedAt), header: '更新时间', enableSorting: false, meta: { width: '170px' } },
  { accessorKey: 'errorCode', header: '错误码', enableSorting: false },
]
const auditColumns: ColumnDef<AuditLog, any>[] = [
  { accessorKey: 'action', header: '操作', enableSorting: false },
  { id: 'actor', accessorFn: (row) => row.actorUserId ?? '系统', header: '操作人', enableSorting: false, meta: { width: '90px' } },
  { id: 'target', accessorFn: (row) => `${row.targetType} ${row.targetId ?? ''}`, header: '目标', enableSorting: false },
  { accessorKey: 'result', header: '结果', enableSorting: false, meta: { width: '95px' } },
  { id: 'summary', accessorFn: (row) => auditSummary(row.summary), header: '摘要', enableSorting: false },
  { id: 'createdAt', accessorFn: (row) => formatDateTime(row.createdAt), header: '时间', enableSorting: false, meta: { width: '170px' } },
  { accessorKey: 'traceId', header: 'Trace ID', enableSorting: false },
]

async function refreshAll(): Promise<void> {
  loading.value = true
  partialError.value = ''
  const results = await Promise.allSettled([
    fetchLiveness(),
    fetchReadiness(),
    fetchGraphHealth(),
    operationsApi.overview(),
    operationsApi.alerts(statusArg(alertFilters.status), typeArg(alertFilters.type), alerts.value.page),
    graphApi.syncStatus(),
    operationsApi.graphEvents(graphEventArg(graphEventStatus.value), graphEvents.value.page),
    operationsApi.maintenanceRuns(maintenanceRuns.value.page),
    operationsApi.audits(audits.value.page),
  ] as const)

  const failures: string[] = []
  applyProbe('liveness', '应用存活', results[0], failures)
  applyProbe('readiness', 'MySQL 就绪', results[1], failures)
  applyProbe('graph', 'Neo4j 独立状态', results[2], failures)
  applyResult(results[3], (value) => { overview.value = value }, '运维总览', failures)
  applyResult(results[4], (value) => { alerts.value = value }, '系统内告警', failures)
  applyResult(results[5], (value) => { syncStatus.value = value }, '图同步摘要', failures)
  applyResult(results[6], (value) => { graphEvents.value = value }, '图同步事件', failures)
  applyResult(results[7], (value) => { maintenanceRuns.value = value }, '维护运行', failures)
  applyResult(results[8], (value) => { audits.value = value }, '审计记录', failures)
  partialError.value = failures.length ? `部分区域暂不可用：${failures.join('；')}` : ''
  loading.value = false
}

function statusArg(value: string): AlertStatus | undefined {
  return value === 'ALL' || !value ? undefined : value as AlertStatus
}
function typeArg(value: string): AlertType | undefined {
  return value === 'ALL' || !value ? undefined : value as AlertType
}
function graphEventArg(value: string): GraphOutboxStatus | undefined {
  return value === 'ALL' || !value ? undefined : value as GraphOutboxStatus
}

function applyProbe(key: ProbeKey, label: string, result: PromiseSettledResult<HealthResponse>, failures: string[]): void {
  if (result.status === 'fulfilled') {
    probes[key].status = result.value.status
    probes[key].detail = result.value.status === 'UP' ? '端点报告正常' : `端点报告 ${result.value.status}`
    return
  }
  probes[key].status = 'UNKNOWN'
  probes[key].detail = errorText(result.reason)
  failures.push(label)
}

function applyResult<T>(result: PromiseSettledResult<T>, apply: (value: T) => void, label: string, failures: string[]): void {
  if (result.status === 'fulfilled') apply(result.value)
  else failures.push(label)
}

async function loadAlerts(page = 0): Promise<void> {
  await loadSection('系统内告警', async () => {
    alerts.value = await operationsApi.alerts(statusArg(alertFilters.status), typeArg(alertFilters.type), page, alerts.value.size)
  })
}
async function loadGraphEvents(page = 0): Promise<void> {
  await loadSection('图同步事件', async () => {
    graphEvents.value = await operationsApi.graphEvents(graphEventArg(graphEventStatus.value), page, graphEvents.value.size)
  })
}
async function loadMaintenanceRuns(page = 0): Promise<void> {
  await loadSection('维护运行', async () => {
    maintenanceRuns.value = await operationsApi.maintenanceRuns(page, maintenanceRuns.value.size)
  })
}
async function loadAudits(page = 0): Promise<void> {
  await loadSection('审计记录', async () => {
    audits.value = await operationsApi.audits(page, audits.value.size)
  })
}
async function loadSection(label: string, request: () => Promise<void>): Promise<void> {
  loading.value = true
  partialError.value = ''
  try {
    await request()
  } catch (error) {
    partialError.value = `${label}加载失败：${toErrorMessage(error)}`
  } finally {
    loading.value = false
  }
}

function openAcknowledge(alert: AlertEvent): void {
  selectedAlert.value = alert
  acknowledgeReason.value = ''
  acknowledgeVisible.value = true
}

async function acknowledgeAlert(): Promise<void> {
  const alert = selectedAlert.value
  const reason = acknowledgeReason.value.trim()
  if (!alert || !reason) {
    partialError.value = '请输入告警确认原因。'
    return
  }
  actionLoading.value = `alert-${alert.id}`
  partialError.value = ''
  try {
    const updated = await operationsApi.acknowledgeAlert(alert, reason)
    if (alertFilters.status === 'OPEN') {
      alerts.value.items = alerts.value.items.filter((item) => item.id !== updated.id)
      alerts.value.totalElements = Math.max(0, alerts.value.totalElements - 1)
    } else {
      alerts.value.items = alerts.value.items.map((item) => item.id === updated.id ? updated : item)
    }
    if (overview.value) overview.value.openAlertCount = Math.max(0, overview.value.openAlertCount - 1)
    acknowledgeVisible.value = false
    toast.success('告警已确认并写入审计')
  } catch (error) {
    partialError.value = toErrorMessage(error)
  } finally {
    actionLoading.value = ''
  }
}

async function replayEvent(event: GraphEvent): Promise<void> {
  actionLoading.value = `event-${event.eventId}`
  partialError.value = ''
  try {
    await operationsApi.replayGraphEvent(event.eventId)
    toast.success('死信事件已提交重放')
    await loadGraphEvents(graphEvents.value.page)
  } catch (error) {
    partialError.value = toErrorMessage(error)
  } finally {
    actionLoading.value = ''
  }
}

async function startMaintenance(type: 'backfill' | 'reconcile' | 'rebuild'): Promise<void> {
  actionLoading.value = type
  partialError.value = ''
  try {
    const run = type === 'backfill'
      ? await operationsApi.startBackfill()
      : type === 'reconcile'
        ? await operationsApi.startReconciliation()
        : await operationsApi.startRebuild()
    maintenanceRuns.value.items = [run, ...maintenanceRuns.value.items.filter((item) => item.id !== run.id)]
    maintenanceRuns.value.totalElements = Math.max(maintenanceRuns.value.totalElements, maintenanceRuns.value.items.length)
    rebuildVisible.value = false
    rebuildConfirmation.value = ''
    toast.success('图维护运行已提交')
  } catch (error) {
    partialError.value = toErrorMessage(error)
  } finally {
    actionLoading.value = ''
  }
}

function submitRebuild(): void {
  if (rebuildConfirmation.value !== 'REBUILD_AACV_MANAGED_GRAPH') {
    partialError.value = '请输入完整确认值 REBUILD_AACV_MANAGED_GRAPH。'
    return
  }
  void startMaintenance('rebuild')
}

function healthLabel(status: HealthStatus | undefined): string {
  return status === 'UP' ? '正常' : status === 'DOWN' ? '异常' : status === 'OUT_OF_SERVICE' ? '停止服务' : '未知'
}
function alertTypeLabel(type: AlertType): string {
  return alertTypes.find((item) => item.value === type)?.label ?? type
}
function maintenanceTypeLabel(type: GraphMaintenanceRun['runType']): string {
  return type === 'INITIAL_BACKFILL' ? '初始回填' : type === 'RECONCILE' ? '对账修复' : '全量重建'
}
function evidenceText(evidence: Record<string, unknown>): string {
  return Object.entries(evidence).slice(0, 4)
    .map(([key, value]) => `${key}=${typeof value === 'object' ? JSON.stringify(value) : String(value)}`)
    .join(' · ') || '无补充证据'
}
function auditSummary(summary: Record<string, string | null>): string {
  return Object.entries(summary).map(([key, value]) => `${key}=${value ?? '—'}`).join(' · ') || '—'
}
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '请求失败'
}

onMounted(refreshAll)
</script>

<template>
  <section class="page-stack">
    <PageHeader
      title="运行监控"
      description="从健康端点、MySQL 运行状态和受控运维记录定位问题。Neo4j 降级不会遮蔽目录、统计与其他权威数据能力。"
      divided
    >
      <template #actions>
        <div class="flex items-center gap-3">
          <span class="text-xs text-muted-foreground">最近汇总 {{ formatDateTime(overview?.generatedAt) }}</span>
          <Button variant="outline" size="icon" :loading="loading" aria-label="刷新全部" @click="refreshAll">
            <RefreshCw class="size-4" />
          </Button>
        </div>
      </template>
    </PageHeader>

    <Alert v-if="partialError" variant="warning"><AlertTitle>{{ partialError }}</AlertTitle></Alert>

    <!-- 依赖健康 -->
    <div class="grid grid-cols-1 gap-3 md:grid-cols-3" aria-label="依赖健康状态">
      <article
        v-for="item in healthCards"
        :key="item.key"
        class="flex items-start gap-3 rounded-xl border p-4"
        :class="{
          'border-success/35 bg-success/8': probes[item.key].status === 'UP',
          'border-destructive/35 bg-destructive/8': probes[item.key].status === 'DOWN' || probes[item.key].status === 'OUT_OF_SERVICE',
          'border-warning/35 bg-warning/8': probes[item.key].status !== 'UP' && probes[item.key].status !== 'DOWN' && probes[item.key].status !== 'OUT_OF_SERVICE',
          'border-border bg-card': false,
        }"
      >
        <StatusPill :status="probes[item.key].status" :label="healthLabel(probes[item.key].status)" pulse class="mt-0.5 shrink-0" />
        <div class="min-w-0 space-y-0.5">
          <strong class="block text-sm font-semibold">{{ item.label }} · {{ healthLabel(probes[item.key].status) }}</strong>
          <span class="block text-xs text-muted-foreground">{{ probes[item.key].detail }}</span>
        </div>
      </article>
    </div>

    <!-- 运行计数 -->
    <div class="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6" aria-label="运行计数">
      <StatCard v-for="metric in metricCards" :key="metric.label" :label="metric.label" :value="metric.value" :note="metric.note" :tone="metric.tone" />
    </div>

    <!-- 上下文提示 -->
    <div class="flex flex-col gap-3 rounded-lg border-l-4 border-l-warning bg-warning/8 p-4 sm:flex-row sm:items-center sm:justify-between">
      <div class="space-y-1">
        <strong class="text-sm font-semibold">采集失败定位</strong>
        <p class="text-xs text-muted-foreground">总览只提供冻结的近 24 小时聚合计数；失败阶段、有限摘要与重试入口保留在具体采集运行中。</p>
      </div>
      <RouterLink to="/crawl" class="inline-flex shrink-0 items-center gap-1 text-sm font-medium text-primary hover:underline">进入采集任务 →</RouterLink>
    </div>

    <!-- 选项卡 -->
    <Tabs v-model="activeTab">
      <TabsList class="w-full justify-start overflow-x-auto">
        <TabsTrigger value="alerts">系统内告警 {{ alerts.totalElements }}</TabsTrigger>
        <TabsTrigger value="events">图同步事件 {{ graphEvents.totalElements }}</TabsTrigger>
        <TabsTrigger value="maintenance">维护运行 {{ maintenanceRuns.totalElements }}</TabsTrigger>
        <TabsTrigger value="audits">审计记录 {{ audits.totalElements }}</TabsTrigger>
      </TabsList>

      <!-- 告警 -->
      <TabsContent value="alerts">
        <div class="mb-3 flex flex-wrap items-center gap-2">
          <Select v-model="alertFilters.status">
            <SelectTrigger class="w-40" placeholder="全部状态" aria-label="告警状态" />
            <SelectContent>
              <SelectItem value="ALL">全部状态</SelectItem>
              <SelectItem value="OPEN">未确认</SelectItem>
              <SelectItem value="ACKNOWLEDGED">已确认</SelectItem>
            </SelectContent>
          </Select>
          <Select v-model="alertFilters.type">
            <SelectTrigger class="w-44" placeholder="全部类型" aria-label="告警类型" />
            <SelectContent>
              <SelectItem value="ALL">全部类型</SelectItem>
              <SelectItem v-for="item in alertTypes" :key="item.value" :value="item.value">{{ item.label }}</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="outline" size="sm" @click="loadAlerts(0)">筛选告警</Button>
          <span class="ml-auto text-xs text-muted-foreground">只展示有限安全证据</span>
        </div>
        <DataTable
          :columns="alertColumns" :data="alerts.items" :loading="loading"
          :page="alerts.page" :size="alerts.size" :total="alerts.totalElements"
          empty-text="暂无系统内告警" :get-row-id="(row) => String(row.id)" @update:page="loadAlerts"
        >
          <template #cell-severity="{ row }">
            <Badge :variant="row.severity === 'CRITICAL' ? 'destructive' : 'warning'">{{ row.severity }}</Badge>
          </template>
          <template #cell-alert="{ row }">
            <strong class="block text-sm font-medium text-foreground">{{ alertTypeLabel(row.type) }} · {{ row.summary }}</strong>
            <small class="block text-xs text-muted-foreground">{{ evidenceText(row.evidence) }}</small>
          </template>
          <template #cell-statusAction="{ row }">
            <StatusPill v-if="row.status === 'ACKNOWLEDGED'" status="SUCCEEDED" label="已确认" />
            <Button
              v-else-if="hasPermission('ALERT_MANAGE')"
              variant="link" size="sm" class="h-auto p-0"
              :loading="actionLoading === `alert-${row.id}`" @click="openAcknowledge(row)"
            >确认告警</Button>
          </template>
        </DataTable>
      </TabsContent>

      <!-- 图同步事件 -->
      <TabsContent value="events">
        <div class="mb-3 flex flex-wrap items-center gap-2">
          <Select v-model="graphEventStatus">
            <SelectTrigger class="w-44" placeholder="全部状态" aria-label="图事件状态" />
            <SelectContent>
              <SelectItem value="ALL">全部状态</SelectItem>
              <SelectItem v-for="status in graphStatuses" :key="status" :value="status">{{ status }}</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="outline" size="sm" @click="loadGraphEvents(0)">筛选事件</Button>
          <span class="ml-auto text-xs text-muted-foreground">
            Neo4j {{ syncStatus?.neo4jAvailable ? '可用' : '已降级' }} · 重建 {{ syncStatus?.rebuildInProgress ? '进行中' : '未进行' }}
          </span>
        </div>
        <DataTable
          :columns="eventColumns" :data="graphEvents.items" :loading="loading"
          :page="graphEvents.page" :size="graphEvents.size" :total="graphEvents.totalElements"
          empty-text="暂无图同步事件" :get-row-id="(row) => row.eventId" @update:page="loadGraphEvents"
        >
          <template #cell-status="{ row }"><StatusPill :status="row.status" /></template>
          <template #cell-eventId="{ row }">
            <strong class="mono-evidence block text-xs font-medium text-foreground">{{ row.eventId }}</strong>
            <small class="block text-xs text-muted-foreground">成果 #{{ row.achievementId }} · 目标版本 {{ row.desiredVersion }}</small>
          </template>
          <template #cell-actions="{ row }">
            <Button
              v-if="row.status === 'DEAD' && hasPermission('GRAPH_SYNC_MANAGE')"
              variant="link" size="sm" class="h-auto p-0"
              :loading="actionLoading === `event-${row.eventId}`" @click="replayEvent(row)"
            >重放</Button>
          </template>
        </DataTable>
      </TabsContent>

      <!-- 维护运行 -->
      <TabsContent value="maintenance">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
          <strong class="text-sm font-medium">MySQL 驱动的受控图维护</strong>
          <div v-if="hasPermission('GRAPH_SYNC_MANAGE')" class="flex flex-wrap gap-2">
            <Button variant="outline" size="sm" :loading="actionLoading === 'backfill'" @click="startMaintenance('backfill')">启动回填</Button>
            <Button variant="outline" size="sm" :loading="actionLoading === 'reconcile'" @click="startMaintenance('reconcile')">启动对账</Button>
            <Button variant="destructive" size="sm" @click="rebuildVisible = true">全量重建</Button>
          </div>
        </div>
        <DataTable
          :columns="maintenanceColumns" :data="maintenanceRuns.items" :loading="loading"
          :page="maintenanceRuns.page" :size="maintenanceRuns.size" :total="maintenanceRuns.totalElements"
          empty-text="暂无维护运行" :get-row-id="(row) => String(row.id)" @update:page="loadMaintenanceRuns"
        >
          <template #cell-status="{ row }"><StatusPill :status="row.status" /></template>
        </DataTable>
      </TabsContent>

      <!-- 审计记录 -->
      <TabsContent value="audits">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
          <strong class="text-sm font-medium">关键操作安全摘要</strong>
          <span class="text-xs text-muted-foreground">不展示凭据、路径、SQL 或 Cypher</span>
        </div>
        <DataTable
          :columns="auditColumns" :data="audits.items" :loading="loading"
          :page="audits.page" :size="audits.size" :total="audits.totalElements"
          empty-text="暂无审计记录" :get-row-id="(row) => String(row.id)" @update:page="loadAudits"
        >
          <template #cell-result="{ row }">
            <Badge :variant="row.result === 'SUCCESS' ? 'success' : 'destructive'">{{ row.result }}</Badge>
          </template>
          <template #cell-traceId="{ row }"><span class="mono-evidence text-xs">{{ row.traceId }}</span></template>
        </DataTable>
      </TabsContent>
    </Tabs>

    <!-- 确认告警 -->
    <Dialog v-model:open="acknowledgeVisible">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle class="flex items-center gap-2"><AlertOctagon class="size-5 text-warning" />确认系统内告警</DialogTitle>
          <DialogDescription>确认不会关闭触发条件；只有晚于确认时间的新信号才会重新产生未确认事件。</DialogDescription>
        </DialogHeader>
        <FormItem>
          <FormLabel for="ackReason">告警确认原因</FormLabel>
          <Textarea
            id="ackReason" v-model="acknowledgeReason" :rows="4" :maxlength="1000"
            placeholder="填写处置结论或转交说明" aria-label="告警确认原因"
          />
        </FormItem>
        <DialogFooter>
          <Button variant="outline" @click="acknowledgeVisible = false">取消</Button>
          <Button :loading="actionLoading.startsWith('alert-')" @click="acknowledgeAlert">提交确认</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- 全量重建确认 -->
    <Dialog v-model:open="rebuildVisible">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>确认全量重建 AACV 图投影</DialogTitle>
        </DialogHeader>
        <Alert variant="warning"><AlertTitle>该操作会重建所有 AACV 受管图数据；不会删除非 AACV 数据。</AlertTitle></Alert>
        <FormItem>
          <FormLabel for="rebuildConfirm">
            请输入 <code class="mono-evidence rounded bg-muted px-1.5 py-0.5 text-xs">REBUILD_AACV_MANAGED_GRAPH</code> 继续
          </FormLabel>
          <Input id="rebuildConfirm" v-model="rebuildConfirmation" autocomplete="off" aria-label="全量重建确认值" />
        </FormItem>
        <DialogFooter>
          <Button variant="outline" @click="rebuildVisible = false">取消</Button>
          <Button variant="destructive" :loading="actionLoading === 'rebuild'" @click="submitRebuild">确认重建</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </section>
</template>
