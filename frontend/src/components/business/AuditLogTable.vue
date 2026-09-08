<script setup lang="ts">
import { ElButton, ElDialog } from 'element-plus'
import { computed, ref } from 'vue'

import DataTable from '@/components/business/DataTable.vue'
import StatusPill from '@/components/business/StatusPill.vue'
import type { DataTableColumn } from '@/components/business/types'
import type { AuditLog } from '@/types/api'
import { formatDateTime } from '@/utils/format'
import { auditActionLabel, browserLabel } from '@/utils/audit'

const props = withDefaults(defineProps<{
  items: AuditLog[]
  loading?: boolean
  page?: number
  size?: number
  total?: number
  compact?: boolean
  fill?: boolean
}>(), { page: 0, size: 20, total: 0 })

defineEmits<{ 'update:page': [page: number] }>()

const selected = ref<AuditLog | null>(null)
const detailVisible = computed({
  get: () => selected.value !== null,
  set: (value: boolean) => { if (!value) selected.value = null },
})

const columns = computed<DataTableColumn<AuditLog>[]>(() => [
  { id: 'createdAt', accessorFn: (row) => formatDateTime(row.createdAt), header: '时间', enableSorting: false, meta: { width: '170px' } },
  { id: 'username', accessorFn: (row) => row.username || (row.actorUserId ? `用户 #${row.actorUserId}` : '--'), header: '账号', enableSorting: false },
  { id: 'action', accessorFn: (row) => auditActionLabel(row), header: '事件', enableSorting: false },
  { accessorKey: 'result', header: '结果', enableSorting: false, meta: { width: '90px' } },
  ...(!props.compact ? [
    { id: 'clientIp', accessorFn: (row: AuditLog) => row.clientIp || '--', header: '来源 IP', enableSorting: false, meta: { width: '140px' } },
    { id: 'browser', accessorFn: (row: AuditLog) => browserLabel(row.userAgent), header: '浏览器', enableSorting: false, meta: { width: '150px' } },
  ] : []),
  { id: 'details', header: '详情', enableSorting: false, meta: { width: '80px' } },
])

/** 安全摘要只展示服务端已脱敏的键值，缺失值统一显示 --。 */
function summaryLines(log: AuditLog): string {
  return Object.entries(log.summary ?? {})
    .map(([key, value]) => `${key}: ${value ?? '--'}`)
    .join('\n') || '--'
}
</script>

<template>
  <DataTable
    :fill="fill"
    :columns="columns"
    :data="items"
    :loading="loading"
    :dense="compact"
    :page="page"
    :size="size"
    :total="compact ? 0 : total"
    empty-text="暂无日志记录"
    empty-description="调整筛选条件或时间范围后重新查询。"
    :get-row-id="(row) => String(row.id)"
    @update:page="$emit('update:page', $event)"
  >
    <template #cell-result="{ row }">
      <StatusPill :status="row.result" :label="row.result === 'SUCCESS' ? '成功' : '失败'" />
    </template>
    <template #cell-details="{ row }">
      <ElButton link type="primary" :aria-label="`查看日志 ${row.id} 详情`" @click="selected = row">查看</ElButton>
    </template>
  </DataTable>

  <ElDialog
    v-model="detailVisible"
    title="日志详情"
    width="min(620px, calc(100vw - 32px))"
    append-to-body
    class="aacv-form-dialog"
  >
    <p v-if="selected" class="-mt-1 mb-4 text-sm text-muted-foreground">{{ auditActionLabel(selected) }}</p>
    <dl v-if="selected" class="grid grid-cols-[6rem_minmax(0,1fr)] gap-x-4 gap-y-3 text-sm">
      <dt class="text-muted-foreground">账号</dt>
      <dd class="min-w-0 break-words">{{ selected.username || '--' }}</dd>
      <dt class="text-muted-foreground">时间</dt>
      <dd class="min-w-0 tabular-nums">{{ formatDateTime(selected.createdAt) }}</dd>
      <dt class="text-muted-foreground">来源 IP</dt>
      <dd class="mono-evidence min-w-0 break-all">{{ selected.clientIp || '--' }}</dd>
      <dt class="text-muted-foreground">浏览器</dt>
      <dd class="min-w-0 break-words">{{ browserLabel(selected.userAgent) }}</dd>
      <dt class="text-muted-foreground">客户端声明</dt>
      <dd class="mono-evidence min-w-0 break-all">{{ selected.userAgent || '--' }}</dd>
      <dt class="text-muted-foreground">操作目标</dt>
      <dd class="min-w-0 break-all">{{ selected.targetType }} · {{ selected.targetId || '--' }}</dd>
      <dt class="text-muted-foreground">Trace ID</dt>
      <dd class="mono-evidence min-w-0 break-all">{{ selected.traceId }}</dd>
      <dt class="text-muted-foreground">安全摘要</dt>
      <dd class="mono-evidence min-w-0 break-all whitespace-pre-wrap">{{ summaryLines(selected) }}</dd>
    </dl>
  </ElDialog>
</template>
