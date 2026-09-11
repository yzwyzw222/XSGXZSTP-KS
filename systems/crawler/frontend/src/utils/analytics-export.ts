import type { AnalyticsOverview, AnalyticsTrendResponse, AnalyticsDistributionResponse, AnalyticsCollaborationResponse } from '@/types/api'
import { achievementTypeLabel } from './filter-options'

export interface AnalyticsSnapshot {
  overview: AnalyticsOverview
  trends: AnalyticsTrendResponse
  distributions: AnalyticsDistributionResponse
  collaboration: AnalyticsCollaborationResponse
}

/** 导出已加载的同一查询范围；合作排行保留接口返回的前二十项口径。 */
export function analyticsCsv(snapshot: AnalyticsSnapshot): string {
  const { overview, trends, distributions, collaboration } = snapshot
  const rows: (string | number)[][] = [
    ['统计项', '名称', '内部标识或条件', '数值'],
    ['范围', '筛选条件', JSON.stringify(overview.scope.filters), ''],
    ['范围', '数据更新时间', overview.updatedAt, ''],
    ['口径', '分类完整计数不可相加；合作为共同署名成果，排行最多二十项', '', ''],
    ...(['achievementCount', 'authorCount', 'organizationCount', 'sourceCount'] as const).map((key, index) =>
      ['总览', ['成果', '作者', '机构', '来源'][index]!, '', overview[key]]),
    ...trends.items.map(item => ['年度趋势', item.publicationYear, '', item.achievementCount]),
  ]
  for (const [key, label] of [['achievementTypes', '成果类型'], ['sources', '数据来源'], ['organizations', '机构分布'], ['topics', '主题分布']] as const) {
    for (const item of distributions[key]) rows.push([label, key === 'achievementTypes' ? achievementTypeLabel(item.key, item.label) : item.label, item.key, item.achievementCount])
  }
  for (const [key, label] of [['authors', '作者合作'], ['organizations', '机构合作']] as const) {
    for (const item of collaboration[key]) rows.push([label, `${item.leftLabel} × ${item.rightLabel}`, `${item.leftId} × ${item.rightId}`, item.sharedAchievementCount])
  }
  for (const [key, value] of Object.entries(overview.coverage ?? {})) rows.push(['字段覆盖', key, '', value])
  // 文本统一引用并阻止电子表格将来源名称识别为公式。
  return '\uFEFF' + rows.map(row => row.map(value => {
    const text = String(value)
    const safe = typeof value === 'string' && /^\s*[=+@-]/.test(text) ? `'${text}` : text
    return `"${safe.replaceAll('"', '""')}"`
  }).join(',')).join('\r\n') + '\r\n'
}
