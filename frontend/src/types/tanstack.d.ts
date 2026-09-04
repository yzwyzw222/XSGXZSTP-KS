import '@tanstack/vue-table'

declare module '@tanstack/vue-table' {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  interface ColumnMeta<TData, TValue> {
    /** 列宽（CSS 宽度字符串），用于 DataTable 表头。 */
    width?: string
    /** 单元格对齐方式。 */
    align?: 'left' | 'center' | 'right'
  }
}
