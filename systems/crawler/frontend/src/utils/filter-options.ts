export interface FilterOption {
  value: string
  label: string
}

/** 中文名称复用于筛选选项与统计展示，类型编码与后端 achievement_type 保持一致。 */
const achievementTypeLabels = new Map<string, string>([
  ['article', '期刊论文'],
  ['patent', '专利'],
  ['scientific-result', '科技成果'],
  ['master-thesis', '硕士学位论文'],
  ['doctoral-thesis', '博士学位论文'],
  ['review', '综述'],
  ['preprint', '预印本'],
  ['proceedings-article', '会议论文'],
  ['book-chapter', '图书章节'],
  ['book', '专著'],
  ['dataset', '数据集'],
  ['report', '报告'],
])

export const achievementTypeOptions: FilterOption[] = Array.from(achievementTypeLabels, ([value, label]) => ({
  value, label: `${value} · ${label}`,
}))

export function achievementTypeLabel(value: string, fallback = value): string {
  return achievementTypeLabels.get(value) ?? fallback
}

/** 来源筛选覆盖当前知网文件和历史远程来源，与目录及导出口径一致。 */
export const sourceCodeOptions: FilterOption[] = [
  { value: 'CNKI', label: '知网信息表' },
  { value: 'OPENALEX', label: 'OpenAlex' },
  { value: 'CROSSREF', label: 'Crossref' },
]
