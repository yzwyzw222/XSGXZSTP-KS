<script setup lang="ts" generic="T extends Record<string, any>">
import { ElLoading, ElPagination, ElSkeleton, ElSkeletonItem, ElTable, ElTableColumn } from 'element-plus'
import { useMediaQuery } from '@vueuse/core'
import { computed } from 'vue'

import EmptyState from '@/components/business/EmptyState.vue'
import type { DataTableColumn, DataTableSort } from '@/components/business/types'
import { cn } from '@/lib/utils'

// 局部注册 v-loading，避免全量引入 Element Plus 指令。
const vLoading = ElLoading.directive

const props = withDefaults(defineProps<{
  columns: DataTableColumn<T>[]
  data: T[]
  loading?: boolean
  /** 后端页码，从 0 开始。 */
  page?: number
  size?: number
  total?: number
  emptyText?: string
  emptyDescription?: string
  getRowId: (row: T, index: number) => string
  /** 初始服务端排序状态。 */
  initialSorting?: DataTableSort[]
  dense?: boolean
  /** 在固定高度面板内仅滚动表体，分页保持可见。 */
  fill?: boolean
  class?: string
  onRowClick?: (row: T) => void
}>(), {
  loading: false,
  page: 0,
  size: 20,
  total: 0,
  emptyText: '暂无数据',
})

const emit = defineEmits<{
  (e: 'update:page', page: number): void
  (e: 'sort', sorting: DataTableSort[]): void
}>()

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
const compactPagination = useMediaQuery('(max-width: 639px)')
const fromIndex = computed(() => (props.total === 0 ? 0 : props.page * props.size + 1))
const toIndex = computed(() => Math.min(props.total, (props.page + 1) * props.size))

/**
 * 页码转换集中在此处：Element Plus 的 `current-page` 从 1 开始，
 * 后端分页从 0 开始，对外事件仍然发出 0 基页码。
 */
const currentPage = computed({
  get: () => props.page + 1,
  set: (value: number) => {
    const next = Math.min(Math.max(0, value - 1), totalPages.value - 1)
    if (next !== props.page) emit('update:page', next)
  },
})

const defaultSort = computed(() => {
  const first = props.initialSorting?.[0]
  return first ? { prop: first.id, order: (first.desc ? 'descending' : 'ascending') as 'ascending' | 'descending' } : undefined
})

function columnId(column: DataTableColumn<T>, index: number): string {
  return column.id ?? column.accessorKey ?? `column-${index}`
}

function cellValue(row: T, column: DataTableColumn<T>): unknown {
  if (column.accessorFn) return column.accessorFn(row)
  if (column.accessorKey) return (row as Record<string, unknown>)[column.accessorKey]
  return undefined
}

/** Element Plus 只按像素分配列宽；百分比写法降级为等价的 min-width。 */
function toPixels(value: string | number | undefined, percentReference = 960): number | undefined {
  if (value === undefined || value === null || value === '') return undefined
  if (typeof value === 'number') return value
  if (value.endsWith('%')) {
    const percent = Number.parseFloat(value)
    return Number.isFinite(percent) ? Math.round((percent / 100) * percentReference) : undefined
  }
  const pixels = Number.parseFloat(value)
  return Number.isFinite(pixels) ? pixels : undefined
}

function rowKey(row: T): string {
  return props.getRowId(row, props.data.indexOf(row))
}

function handleRowClick(row: T): void {
  props.onRowClick?.(row)
}

/** 排序完全交给服务端：只发出事件，由页面重新请求全量数据。 */
function handleSortChange(payload: { prop: string | null; order: 'ascending' | 'descending' | null }): void {
  emit('sort', payload.order && payload.prop ? [{ id: payload.prop, desc: payload.order === 'descending' }] : [])
}
</script>

<template>
  <div :class="cn('data-table', fill && 'data-table--fill', props.class)" :aria-busy="loading">
    <span class="sr-only" role="status">{{ loading ? '正在加载数据' : '' }}</span>
    <!-- 首次加载：骨架行，避免整表闪烁 -->
    <ElSkeleton v-if="loading && data.length === 0" animated class="space-y-2">
      <template #template>
        <ElSkeletonItem variant="rect" style="width: 100%; height: 36px; border-radius: var(--radius-md)" />
        <ElSkeletonItem
          v-for="i in 5"
          :key="i"
          variant="rect"
          style="width: 100%; height: 44px; border-radius: var(--radius-md)"
        />
      </template>
    </ElSkeleton>

    <!-- 表格：刷新时保留已渲染数据，仅降低不透明度 -->
    <div
      v-else
      v-loading="loading"
      :class="cn('data-table__content', loading && 'data-table__content--loading')"
      :element-loading-text="loading ? '正在加载' : undefined"
    >
      <ElTable
        :height="fill ? '100%' : undefined"
        :data="data"
        :row-key="rowKey"
        :size="dense ? 'small' : 'default'"
        :default-sort="defaultSort"
        :class="cn('aacv-table', onRowClick && 'cursor-pointer')"
        :cell-style="{ padding: dense ? '6px 0' : '12px 0', verticalAlign: 'top' }"
        :header-cell-style="{ padding: '10px 0', fontWeight: 500, fontSize: 'var(--font-size-sm)' }"
        style="width: 100%"
        @row-click="handleRowClick"
        @sort-change="handleSortChange"
      >
        <ElTableColumn
          v-for="(column, index) in columns"
          :key="columnId(column, index)"
          :prop="columnId(column, index)"
          :label="column.header"
          :width="toPixels(column.meta?.width)"
          :min-width="toPixels(column.meta?.minWidth)"
          :align="column.meta?.align"
          :sortable="column.enableSorting ? 'custom' : false"
        >
          <template #default="{ row, $index }">
            <slot
              :name="`cell-${columnId(column, index)}`"
              :row="row as T"
              :value="cellValue(row as T, column)"
              :index="$index as number"
            >{{ cellValue(row as T, column) }}</slot>
          </template>
        </ElTableColumn>

        <template #empty>
          <EmptyState :title="emptyText" :description="emptyDescription">
            <template v-if="$slots.empty" #action><slot name="empty" /></template>
          </EmptyState>
        </template>
      </ElTable>
    </div>

    <!-- 分页：左侧保留区间文案，右侧为 Element Plus 翻页控件 -->
    <div v-if="total > 0" class="data-table__pagination">
      <span class="text-xs tabular-nums text-muted-foreground">
        显示 {{ fromIndex }}–{{ toIndex }}，共 {{ total }} 条
      </span>
      <ElPagination
        v-model:current-page="currentPage"
        :page-size="size"
        :total="total"
        :pager-count="7"
        :layout="compactPagination ? 'prev, jumper, next' : 'prev, pager, next'"
        size="small"
        background
        :disabled="loading"
      />
    </div>
  </div>
</template>
