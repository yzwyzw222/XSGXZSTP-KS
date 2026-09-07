export interface EntityLinkItem {
  id: string | number
  label: string
  to?: string
}

export interface LogEntry {
  id: string | number
  time?: string
  level?: 'info' | 'success' | 'warning' | 'error'
  message: string
}

/** 两点间最短路径查询表单值，ID 以字符串编辑、提交时校验转换。 */
export interface GraphPathQuery {
  sourceType: 'ACHIEVEMENT' | 'AUTHOR' | 'INSTITUTION' | 'VENUE' | 'TOPIC'
  sourceId: string
  targetType: 'ACHIEVEMENT' | 'AUTHOR' | 'INSTITUTION' | 'VENUE' | 'TOPIC'
  targetId: string
  maxHops: string
}

/**
 * 表格列的元信息。
 *
 * `width` 只接受像素值（Element Plus 的列宽算法按像素分配）；
 * 需要按比例占用剩余空间时使用 `minWidth`，
 * Element Plus 会按各列 `min-width` 的比例分配多余宽度。
 */
export interface DataTableColumnMeta {
  width?: string | number
  minWidth?: string | number
  align?: 'left' | 'center' | 'right'
}

/** 单元格插槽 `#cell-<id>` 的作用域参数。 */
export interface DataTableCellContext<T> {
  row: T
  value: unknown
  index: number
}

/**
 * 项目自有的表格列定义，取代原 `@tanstack/vue-table` 的 `ColumnDef`。
 *
 * 只保留业务实际使用的字段：标识、取值、表头、服务端排序开关与列宽元信息。
 * 单元格内容一律通过 `#cell-<id>` 插槽渲染，不在列定义里写渲染函数，
 * 因此这里不需要承载组件渲染上下文。
 */
export interface DataTableColumn<T> {
  /** 列标识，同时是 `#cell-<id>` 插槽名；缺省时取 `accessorKey`。 */
  id?: string
  /** 直接映射行字段名。 */
  accessorKey?: keyof T & string
  /** 由行数据派生显示值。 */
  accessorFn?: (row: T) => unknown
  /** 表头文本。 */
  header?: string
  /**
   * 是否允许排序。开启后为**服务端排序**：DataTable 只发出 `sort` 事件，
   * 由页面重新请求全量数据，绝不对当前页做局部排序冒充全量排序。
   */
  enableSorting?: boolean
  meta?: DataTableColumnMeta
}

/** 服务端排序状态；`desc` 为 true 表示降序。 */
export interface DataTableSort {
  id: string
  desc: boolean
}
