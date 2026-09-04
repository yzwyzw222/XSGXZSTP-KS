<script setup lang="ts" generic="T extends Record<string, any>">
import {
  FlexRender,
  getCoreRowModel,
  useVueTable,
  type ColumnDef,
  type SortingState,
  type Updater,
} from '@tanstack/vue-table'
import { ChevronDown, ChevronUp, ChevronsUpDown } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'

import EmptyState from '@/components/business/EmptyState.vue'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import { cn } from '@/lib/utils'

const props = withDefaults(defineProps<{
  columns: ColumnDef<T, any>[]
  data: T[]
  loading?: boolean
  page?: number
  size?: number
  total?: number
  emptyText?: string
  emptyDescription?: string
  getRowId?: (row: T, index: number) => string
  manualSorting?: boolean
  initialSorting?: SortingState
  dense?: boolean
  class?: string
  onRowClick?: (row: T) => void
}>(), {
  loading: false,
  page: 0,
  size: 20,
  total: 0,
  emptyText: '暂无数据',
  manualSorting: true,
})

const emit = defineEmits<{
  (e: 'update:page', page: number): void
  (e: 'sort', sorting: SortingState): void
}>()

const sorting = ref<SortingState>(props.initialSorting ?? [])

const table = useVueTable({
  get data() { return props.data },
  get columns() { return props.columns },
  getCoreRowModel: getCoreRowModel(),
  manualPagination: true,
  manualSorting: props.manualSorting,
  getRowId: props.getRowId,
  state: {
    get sorting() { return sorting.value },
  },
  onSortingChange: (updater: Updater<SortingState>) => {
    const next = typeof updater === 'function' ? updater(sorting.value) : updater
    sorting.value = next
    emit('sort', next)
  },
})

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
const fromIndex = computed(() => (props.total === 0 ? 0 : props.page * props.size + 1))
const toIndex = computed(() => Math.min(props.total, (props.page + 1) * props.size))

watch(() => props.initialSorting, (v) => { if (v) sorting.value = v })

function goPage(p: number): void {
  const clamped = Math.min(Math.max(0, p), totalPages.value - 1)
  emit('update:page', clamped)
}

function sortIcon(columnId: string) {
  const state = table.getColumn(columnId)?.getIsSorted()
  if (state === 'asc') return ChevronUp
  if (state === 'desc') return ChevronDown
  return ChevronsUpDown
}
</script>

<template>
  <div :class="cn('w-full', props.class)">
    <!-- 加载骨架 -->
    <div v-if="loading && data.length === 0" class="space-y-2">
      <Skeleton class="h-9 w-full rounded-md" />
      <Skeleton v-for="i in 5" :key="i" class="h-11 w-full rounded-md" />
    </div>

    <!-- 空态 -->
    <EmptyState
      v-else-if="!loading && data.length === 0"
      :title="emptyText"
      :description="emptyDescription"
    >
      <template v-if="$slots.empty" #action><slot name="empty" /></template>
    </EmptyState>

    <!-- 表格 -->
    <div v-else :class="cn('rounded-lg border border-border overflow-hidden transition-opacity', loading && 'opacity-60')">
      <Table>
        <TableHeader>
          <TableRow v-for="headerGroup in table.getHeaderGroups()" :key="headerGroup.id" class="hover:bg-transparent">
            <TableHead
              v-for="header in headerGroup.headers"
              :key="header.id"
              :colspan="header.colSpan"
              :style="header.column.columnDef.meta?.width ? { width: header.column.columnDef.meta.width } : undefined"
              :class="header.column.getCanSort() ? 'cursor-pointer select-none' : ''"
              :aria-sort="header.column.getIsSorted() === 'asc' ? 'ascending' : header.column.getIsSorted() === 'desc' ? 'descending' : 'none'"
              @click="header.column.getToggleSortingHandler()?.($event)"
            >
              <span v-if="!header.isPlaceholder" class="flex items-center gap-1">
                <FlexRender :render="header.column.columnDef.header" :props="header.getContext()" />
                <component :is="sortIcon(header.id)" v-if="header.column.getCanSort()" class="size-3.5 opacity-60" />
              </span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow
            v-for="row in table.getRowModel().rows"
            :key="row.id"
            :data-state="row.getIsSelected() && 'selected'"
            :class="cn(onRowClick && 'cursor-pointer')"
            @click="onRowClick?.(row.original)"
          >
            <TableCell
              v-for="cell in row.getVisibleCells()"
              :key="cell.id"
              :class="cn(dense ? 'py-1.5' : 'py-2.5')"
            >
              <slot :name="`cell-${cell.column.id}`" :row="row.original" :value="cell.getValue()" :cell="cell">
                <FlexRender :render="cell.column.columnDef.cell" :props="cell.getContext()" />
              </slot>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>

    <!-- 分页 -->
    <div
      v-if="total > 0"
      class="mt-3 flex flex-wrap items-center justify-between gap-3 text-sm"
    >
      <span class="text-xs text-muted-foreground">
        显示 {{ fromIndex }}–{{ toIndex }}，共 {{ total }} 条
      </span>
      <div class="flex items-center gap-2">
        <Button variant="outline" size="sm" :disabled="page <= 0 || loading" @click="goPage(page - 1)">上一页</Button>
        <span class="text-xs tabular-nums text-muted-foreground">{{ page + 1 }} / {{ totalPages }}</span>
        <Button variant="outline" size="sm" :disabled="page >= totalPages - 1 || loading" @click="goPage(page + 1)">下一页</Button>
      </div>
    </div>
  </div>
</template>
