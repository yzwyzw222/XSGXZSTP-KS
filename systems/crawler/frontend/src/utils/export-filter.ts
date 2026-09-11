import type { AchievementQuery } from '@/services/business'
import type { ExportFilter } from '@/types/api'

/** 导出沿用已提交的目录条件；选中的实体直接使用 ID，自由输入保留模糊匹配。 */
export function resolveExportFilter(query: AchievementQuery): ExportFilter {
  const { page: _page, size: _size, publicationYear, ...filters } = query
  return Object.fromEntries(Object.entries({ ...filters, publicationYearFrom: publicationYear, publicationYearTo: publicationYear })
    .filter(([, value]) => value !== undefined && value !== ''))
}
