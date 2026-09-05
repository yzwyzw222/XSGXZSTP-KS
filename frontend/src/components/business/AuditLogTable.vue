<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import DataTable from '@/components/business/DataTable.vue'
import StatusPill from '@/components/business/StatusPill.vue'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import type { AuditLog } from '@/types/api'
import { formatDateTime } from '@/utils/format'
import { auditActionLabel, browserLabel } from '@/utils/audit'

const props = withDefaults(defineProps<{ items: AuditLog[]; loading?: boolean; page?: number; size?: number; total?: number; compact?: boolean }>(), { page: 0, size: 20, total: 0 })
defineEmits<{ 'update:page': [page: number] }>()
const selected = ref<AuditLog | null>(null)
const columns = computed<ColumnDef<AuditLog, any>[]>(() => [
  { id: 'createdAt', accessorFn: (row) => formatDateTime(row.createdAt), header: '时间', enableSorting: false },
  { id: 'username', accessorFn: (row) => row.username || (row.actorUserId ? `用户 #${row.actorUserId}` : '--'), header: '账号', enableSorting: false },
  { id: 'action', accessorFn: auditActionLabel, header: '事件', enableSorting: false },
  { accessorKey: 'result', header: '结果', enableSorting: false },
  ...(!props.compact ? [
    { id: 'clientIp', accessorFn: (row: AuditLog) => row.clientIp || '--', header: '来源 IP', enableSorting: false },
    { id: 'browser', accessorFn: (row: AuditLog) => browserLabel(row.userAgent), header: '浏览器', enableSorting: false },
  ] : []),
  { id: 'details', header: '详情', enableSorting: false },
])
</script>

<template>
  <DataTable class="[&_table]:min-w-[640px] [&_td]:whitespace-nowrap" :columns="columns" :data="items" :loading="loading" :dense="compact" :page="page" :size="size" :total="compact ? 0 : total"
    empty-text="暂无日志记录" :get-row-id="(row) => String(row.id)" @update:page="$emit('update:page', $event)">
    <template #cell-result="{ row }"><StatusPill :status="row.result" :label="row.result === 'SUCCESS' ? '成功' : '失败'" /></template>
    <template #cell-details="{ row }"><Button size="sm" variant="link" :aria-label="`查看日志 ${row.id} 详情`" @click="selected = row">查看</Button></template>
  </DataTable>
  <Dialog :open="Boolean(selected)" @update:open="(open) => { if (!open) selected = null }">
    <DialogContent class="max-h-[85dvh] overflow-y-auto sm:max-w-xl">
      <DialogHeader><DialogTitle>日志详情</DialogTitle><DialogDescription>{{ selected ? auditActionLabel(selected) : '' }}</DialogDescription></DialogHeader>
      <dl v-if="selected" class="grid grid-cols-[5rem_1fr] gap-x-4 gap-y-3 text-sm">
        <dt class="text-muted-foreground">账号</dt><dd>{{ selected.username || '--' }}</dd>
        <dt class="text-muted-foreground">时间</dt><dd>{{ formatDateTime(selected.createdAt) }}</dd>
        <dt class="text-muted-foreground">来源 IP</dt><dd class="break-all font-mono">{{ selected.clientIp || '--' }}</dd>
        <dt class="text-muted-foreground">浏览器</dt><dd>{{ browserLabel(selected.userAgent) }}</dd>
        <dt class="text-muted-foreground">客户端声明</dt><dd class="break-all">{{ selected.userAgent || '--' }}</dd>
        <dt class="text-muted-foreground">操作目标</dt><dd class="break-all">{{ selected.targetType }} · {{ selected.targetId || '--' }}</dd>
        <dt class="text-muted-foreground">Trace ID</dt><dd class="break-all font-mono">{{ selected.traceId }}</dd>
        <dt class="text-muted-foreground">安全摘要</dt><dd class="whitespace-pre-wrap break-all">{{ Object.entries(selected.summary ?? {}).map(([key, value]) => `${key}: ${value ?? '--'}`).join('\n') || '--' }}</dd>
      </dl>
    </DialogContent>
  </Dialog>
</template>
