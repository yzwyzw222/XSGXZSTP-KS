<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElInput,
  ElOption,
  ElPagination,
  ElSelect,
  ElTabPane,
  ElTable,
  ElTableColumn,
  ElTabs,
  ElTag,
  ElMessage,
  vLoading,
} from 'element-plus'

import { toErrorMessage } from '@/services/api'
import { graphApi, operationsApi } from '@/services/business'
import {
  fetchGraphHealth,
  fetchLiveness,
  fetchReadiness,
  type HealthResponse,
  type HealthStatus,
} from '@/services/health'
import { hasPermission } from '@/services/session'
import type {
  AlertEvent,
  AlertStatus,
  AlertType,
  AuditLog,
  GraphEvent,
  GraphMaintenanceRun,
  GraphOutboxStatus,
  GraphSyncStatus,
  OperationsOverview,
  PageResponse,
} from '@/types/api'
import { formatDateTime, formatNumber } from '@/utils/format'

type ProbeKey = 'liveness' | 'readiness' | 'graph'

interface ProbeState {
  status: HealthStatus
  detail: string
}

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
const alertFilters = reactive<{ status: AlertStatus | ''; type: AlertType | '' }>({
  status: 'OPEN',
  type: '',
})
const graphEventStatus = ref<GraphOutboxStatus | ''>('')
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

async function refreshAll(): Promise<void> {
  loading.value = true
  partialError.value = ''
  const results = await Promise.allSettled([
    fetchLiveness(),
    fetchReadiness(),
    fetchGraphHealth(),
    operationsApi.overview(),
    operationsApi.alerts(alertFilters.status || undefined, alertFilters.type || undefined, alerts.value.page),
    graphApi.syncStatus(),
    operationsApi.graphEvents(graphEventStatus.value || undefined, graphEvents.value.page),
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

function applyProbe(
  key: ProbeKey,
  label: string,
  result: PromiseSettledResult<HealthResponse>,
  failures: string[],
): void {
  if (result.status === 'fulfilled') {
    probes[key].status = result.value.status
    probes[key].detail = result.value.status === 'UP' ? '端点报告正常' : `端点报告 ${result.value.status}`
    return
  }
  probes[key].status = 'UNKNOWN'
  probes[key].detail = errorText(result.reason)
  failures.push(label)
}

function applyResult<T>(
  result: PromiseSettledResult<T>,
  apply: (value: T) => void,
  label: string,
  failures: string[],
): void {
  if (result.status === 'fulfilled') {
    apply(result.value)
  } else {
    failures.push(label)
  }
}

async function loadAlerts(page = 0): Promise<void> {
  await loadSection('系统内告警', async () => {
    alerts.value = await operationsApi.alerts(
      alertFilters.status || undefined,
      alertFilters.type || undefined,
      page,
      alerts.value.size,
    )
  })
}

async function loadGraphEvents(page = 0): Promise<void> {
  await loadSection('图同步事件', async () => {
    graphEvents.value = await operationsApi.graphEvents(
      graphEventStatus.value || undefined,
      page,
      graphEvents.value.size,
    )
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
    if (overview.value) {
      overview.value.openAlertCount = Math.max(0, overview.value.openAlertCount - 1)
    }
    acknowledgeVisible.value = false
    ElMessage.success('告警已确认并写入审计')
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
    ElMessage.success('死信事件已提交重放')
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
    ElMessage.success('图维护运行已提交')
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
  return Object.entries(evidence)
    .slice(0, 4)
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
  <section class="page-stack operations-page" v-loading="loading">
    <header class="page-heading operations-heading">
      <div>
        <span class="eyebrow">OPERATIONS / CONTROL DESK</span>
        <h1>运行监控</h1>
        <p>从健康端点、MySQL 运行状态和受控运维记录定位问题。Neo4j 降级不会遮蔽目录、统计与其他权威数据能力。</p>
      </div>
      <div class="operations-stamp">
        <span class="eyebrow">最近汇总</span>
        <strong>{{ formatDateTime(overview?.generatedAt) }}</strong>
        <ElButton plain :loading="loading" @click="refreshAll">刷新全部</ElButton>
      </div>
    </header>

    <ElAlert v-if="partialError" :title="partialError" type="warning" :closable="false" show-icon />

    <div class="operations-health-grid" aria-label="依赖健康状态">
      <article v-for="item in [
        { key: 'liveness', label: '应用存活', caption: 'LIVENESS' },
        { key: 'readiness', label: 'MySQL 就绪', caption: 'READINESS' },
        { key: 'graph', label: 'Neo4j 独立状态', caption: 'GRAPH GROUP' },
      ]" :key="item.key" class="operations-health" :data-state="probes[item.key as ProbeKey].status">
        <span class="status-dot" aria-hidden="true"></span>
        <div>
          <small>{{ item.caption }}</small>
          <strong>{{ item.label }} · {{ healthLabel(probes[item.key as ProbeKey].status) }}</strong>
          <span>{{ probes[item.key as ProbeKey].detail }}</span>
        </div>
      </article>
    </div>

    <div class="operations-metrics" aria-label="运行计数">
      <article><span>活动采集</span><strong>{{ formatNumber(overview?.activeCrawlRunCount) }}</strong><small>运行中或暂停请求处理中</small></article>
      <article><span>近期失败</span><strong>{{ formatNumber(overview?.recentCrawlFailureCount) }}</strong><small>近 24 小时未解决</small></article>
      <article><span>待处理图事件</span><strong>{{ formatNumber(overview?.graphPendingCount) }}</strong><small>最老 {{ formatNumber(syncStatus?.oldestPendingAgeSeconds) }} 秒</small></article>
      <article><span>处理中图事件</span><strong>{{ formatNumber(overview?.graphProcessingCount) }}</strong><small>受租约保护</small></article>
      <article class="operations-metric-critical"><span>图死信</span><strong>{{ formatNumber(overview?.graphDeadCount) }}</strong><small>需要人工判断后重放</small></article>
      <article class="operations-metric-critical"><span>未确认告警</span><strong>{{ formatNumber(overview?.openAlertCount) }}</strong><small>系统内事件</small></article>
    </div>

    <div class="operations-context">
      <div>
        <strong>采集失败定位</strong>
        <span>总览只提供冻结的近 24 小时聚合计数；失败阶段、有限摘要与重试入口保留在具体采集运行中。</span>
      </div>
      <RouterLink to="/crawl" class="text-link">进入采集任务 →</RouterLink>
    </div>

    <ElTabs v-model="activeTab" class="content-panel operations-tabs">
      <ElTabPane :label="`系统内告警 ${alerts.totalElements}`" name="alerts">
        <div class="toolbar operations-toolbar">
          <div class="operations-filters">
            <ElSelect v-model="alertFilters.status" placeholder="全部状态" clearable aria-label="告警状态">
              <ElOption label="未确认" value="OPEN" /><ElOption label="已确认" value="ACKNOWLEDGED" />
            </ElSelect>
            <ElSelect v-model="alertFilters.type" placeholder="全部类型" clearable aria-label="告警类型">
              <ElOption v-for="item in alertTypes" :key="item.value" :label="item.label" :value="item.value" />
            </ElSelect>
            <ElButton @click="loadAlerts(0)">筛选告警</ElButton>
          </div>
          <span class="meta-line">只展示有限安全证据</span>
        </div>
        <ElTable :data="alerts.items" empty-text="暂无系统内告警">
          <ElTableColumn label="级别" width="90"><template #default="{ row }"><ElTag :type="row.severity === 'CRITICAL' ? 'danger' : 'warning'" effect="plain">{{ row.severity }}</ElTag></template></ElTableColumn>
          <ElTableColumn label="告警" min-width="330"><template #default="{ row }"><strong class="table-title">{{ alertTypeLabel(row.type) }} · {{ row.summary }}</strong><small>{{ evidenceText(row.evidence) }}</small></template></ElTableColumn>
          <ElTableColumn label="主体" width="150"><template #default="{ row }">{{ row.subjectType }} {{ row.subjectId ?? '' }}</template></ElTableColumn>
          <ElTableColumn prop="occurrenceCount" label="次数" width="75" />
          <ElTableColumn label="末次检测" width="180"><template #default="{ row }">{{ formatDateTime(row.lastDetectedAt) }}</template></ElTableColumn>
          <ElTableColumn label="状态/操作" width="150"><template #default="{ row }"><ElTag v-if="row.status === 'ACKNOWLEDGED'" type="success" effect="plain">已确认</ElTag><ElButton v-else-if="hasPermission('ALERT_MANAGE')" link type="primary" :loading="actionLoading === `alert-${row.id}`" @click="openAcknowledge(row as AlertEvent)">确认告警</ElButton></template></ElTableColumn>
        </ElTable>
        <div v-if="alerts.totalPages > 1" class="pagination-row"><ElPagination :current-page="alerts.page + 1" :page-size="alerts.size" :total="alerts.totalElements" layout="prev, pager, next" @current-change="(page: number) => loadAlerts(page - 1)" /></div>
      </ElTabPane>

      <ElTabPane :label="`图同步事件 ${graphEvents.totalElements}`" name="events">
        <div class="toolbar operations-toolbar">
          <div class="operations-filters"><ElSelect v-model="graphEventStatus" placeholder="全部状态" clearable aria-label="图事件状态"><ElOption v-for="status in graphStatuses" :key="status" :label="status" :value="status" /></ElSelect><ElButton @click="loadGraphEvents(0)">筛选事件</ElButton></div>
          <span class="meta-line">Neo4j {{ syncStatus?.neo4jAvailable ? '可用' : '已降级' }} · 重建 {{ syncStatus?.rebuildInProgress ? '进行中' : '未进行' }}</span>
        </div>
        <ElTable :data="graphEvents.items" empty-text="暂无图同步事件">
          <ElTableColumn label="状态" width="105"><template #default="{ row }"><ElTag :type="row.status === 'DEAD' ? 'danger' : row.status === 'SUCCEEDED' ? 'success' : 'warning'" effect="plain">{{ row.status }}</ElTag></template></ElTableColumn>
          <ElTableColumn label="事件" min-width="250"><template #default="{ row }"><strong class="table-title">{{ row.eventId }}</strong><small>成果 #{{ row.achievementId }} · 目标版本 {{ row.desiredVersion }}</small></template></ElTableColumn>
          <ElTableColumn prop="attempts" label="尝试" width="75" />
          <ElTableColumn label="错误摘要" min-width="180"><template #default="{ row }">{{ row.errorSummary ?? row.errorCode ?? '—' }}</template></ElTableColumn>
          <ElTableColumn label="下次执行" width="180"><template #default="{ row }">{{ formatDateTime(row.nextAttemptAt) }}</template></ElTableColumn>
          <ElTableColumn label="操作" width="100"><template #default="{ row }"><ElButton v-if="row.status === 'DEAD' && hasPermission('GRAPH_SYNC_MANAGE')" link type="primary" :loading="actionLoading === `event-${row.eventId}`" @click="replayEvent(row as GraphEvent)">重放</ElButton></template></ElTableColumn>
        </ElTable>
        <div v-if="graphEvents.totalPages > 1" class="pagination-row"><ElPagination :current-page="graphEvents.page + 1" :page-size="graphEvents.size" :total="graphEvents.totalElements" layout="prev, pager, next" @current-change="(page: number) => loadGraphEvents(page - 1)" /></div>
      </ElTabPane>

      <ElTabPane :label="`维护运行 ${maintenanceRuns.totalElements}`" name="maintenance">
        <div class="toolbar operations-toolbar">
          <strong>MySQL 驱动的受控图维护</strong>
          <div v-if="hasPermission('GRAPH_SYNC_MANAGE')" class="operations-actions"><ElButton :loading="actionLoading === 'backfill'" @click="startMaintenance('backfill')">启动回填</ElButton><ElButton :loading="actionLoading === 'reconcile'" @click="startMaintenance('reconcile')">启动对账</ElButton><ElButton type="danger" plain @click="rebuildVisible = true">全量重建</ElButton></div>
        </div>
        <ElTable :data="maintenanceRuns.items" empty-text="暂无维护运行">
          <ElTableColumn label="运行" min-width="170"><template #default="{ row }"><strong class="table-title">#{{ row.id }} · {{ maintenanceTypeLabel(row.runType) }}</strong><small>请求人 #{{ row.requestedBy }}</small></template></ElTableColumn>
          <ElTableColumn label="状态" width="105"><template #default="{ row }"><ElTag :type="row.status === 'FAILED' ? 'danger' : row.status === 'SUCCEEDED' ? 'success' : 'warning'" effect="plain">{{ row.status }}</ElTag></template></ElTableColumn>
          <ElTableColumn prop="scannedCount" label="扫描" width="100" />
          <ElTableColumn prop="repairedCount" label="修复" width="100" />
          <ElTableColumn prop="differenceCount" label="差异" width="100" />
          <ElTableColumn label="更新时间" width="180"><template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template></ElTableColumn>
          <ElTableColumn prop="errorCode" label="错误码" min-width="140" />
        </ElTable>
        <div v-if="maintenanceRuns.totalPages > 1" class="pagination-row"><ElPagination :current-page="maintenanceRuns.page + 1" :page-size="maintenanceRuns.size" :total="maintenanceRuns.totalElements" layout="prev, pager, next" @current-change="(page: number) => loadMaintenanceRuns(page - 1)" /></div>
      </ElTabPane>

      <ElTabPane :label="`审计记录 ${audits.totalElements}`" name="audits">
        <div class="toolbar operations-toolbar"><strong>关键操作安全摘要</strong><span class="meta-line">不展示凭据、路径、SQL 或 Cypher</span></div>
        <ElTable :data="audits.items" empty-text="暂无审计记录">
          <ElTableColumn prop="action" label="操作" min-width="190" />
          <ElTableColumn label="操作人" width="100"><template #default="{ row }">{{ row.actorUserId ?? '系统' }}</template></ElTableColumn>
          <ElTableColumn label="目标" min-width="180"><template #default="{ row }">{{ row.targetType }} {{ row.targetId ?? '' }}</template></ElTableColumn>
          <ElTableColumn label="结果" width="95"><template #default="{ row }"><ElTag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" effect="plain">{{ row.result }}</ElTag></template></ElTableColumn>
          <ElTableColumn label="摘要" min-width="230"><template #default="{ row }">{{ auditSummary(row.summary) }}</template></ElTableColumn>
          <ElTableColumn label="时间" width="180"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></ElTableColumn>
          <ElTableColumn prop="traceId" label="Trace ID" min-width="210" />
        </ElTable>
        <div v-if="audits.totalPages > 1" class="pagination-row"><ElPagination :current-page="audits.page + 1" :page-size="audits.size" :total="audits.totalElements" layout="prev, pager, next" @current-change="(page: number) => loadAudits(page - 1)" /></div>
      </ElTabPane>
    </ElTabs>

    <ElDialog v-model="acknowledgeVisible" title="确认系统内告警" width="520px">
      <p class="meta-line">确认不会关闭触发条件；只有晚于确认时间的新信号才会重新产生未确认事件。</p>
      <ElInput v-model="acknowledgeReason" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="填写处置结论或转交说明" aria-label="告警确认原因" />
      <template #footer><ElButton @click="acknowledgeVisible = false">取消</ElButton><ElButton type="primary" :loading="actionLoading.startsWith('alert-')" @click="acknowledgeAlert">提交确认</ElButton></template>
    </ElDialog>

    <ElDialog v-model="rebuildVisible" title="确认全量重建 AACV 图投影" width="560px">
      <ElAlert title="该操作会重建所有 AACV 受管图数据；不会删除非 AACV 数据。" type="warning" :closable="false" show-icon />
      <p class="meta-line rebuild-confirmation">请输入 <code>REBUILD_AACV_MANAGED_GRAPH</code> 继续。</p>
      <ElInput v-model="rebuildConfirmation" aria-label="全量重建确认值" autocomplete="off" />
      <template #footer><ElButton @click="rebuildVisible = false">取消</ElButton><ElButton type="danger" :loading="actionLoading === 'rebuild'" @click="submitRebuild">确认重建</ElButton></template>
    </ElDialog>
  </section>
</template>
