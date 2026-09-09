export interface FilterOption {
  value: string
  label: string
}

/** 成果类型取值与后端 achievement_type 精确匹配保持一致，标签附中文释义便于选择。 */
export const achievementTypeOptions: FilterOption[] = [
  { value: 'article', label: 'article · 期刊论文' },
  { value: 'review', label: 'review · 综述' },
  { value: 'preprint', label: 'preprint · 预印本' },
  { value: 'proceedings-article', label: 'proceedings-article · 会议论文' },
  { value: 'book-chapter', label: 'book-chapter · 图书章节' },
  { value: 'book', label: 'book · 专著' },
  { value: 'dataset', label: 'dataset · 数据集' },
  { value: 'report', label: 'report · 报告' },
]

/** 来源代码仅支持两个已接入的学术来源，与导出校验口径一致。 */
export const sourceCodeOptions: FilterOption[] = [
  { value: 'OPENALEX', label: 'OpenAlex' },
  { value: 'CROSSREF', label: 'Crossref' },
]
