import { describe, expect, it } from 'vitest'
import { analyticsCsv, type AnalyticsSnapshot } from './analytics-export'

const scope = { source: 'MYSQL' as const, filters: { publicationYearFrom: 2025, topicId: 17 } }
const snapshot: AnalyticsSnapshot = {
  overview: { achievementCount: 3, authorCount: 4, organizationCount: 2, sourceCount: 1, scope, updatedAt: '2026-09-11T00:00:00Z' },
  trends: { items: [{ publicationYear: 2025, achievementCount: 3 }], scope, updatedAt: '' },
  distributions: { achievementTypes: [{ key: 'article', label: 'article', achievementCount: 3 }], sources: [], organizations: [{ key: '9', label: '=SUM(1,2)\n"单位"', achievementCount: 3 }], topics: [], scope, updatedAt: '' },
  collaboration: { authors: [{ leftId: 1, leftLabel: '作者甲', rightId: 2, rightLabel: '作者乙', sharedAchievementCount: 1 }], organizations: [], scope, updatedAt: '' },
}
describe('统计结果导出', () => {
  it('导出已应用范围、中文类型、内部标识和真实聚合计数', () => {
    const csv = analyticsCsv(snapshot)
    expect(csv.startsWith('\uFEFF')).toBe(true)
    expect(csv).toContain('""publicationYearFrom"":2025,""topicId"":17')
    expect(csv).toContain('"年度趋势","2025","","3"')
    expect(csv).toContain('"成果类型","期刊论文","article","3"')
    expect(csv).toContain('"作者合作","作者甲 × 作者乙","1 × 2","1"')
  })
  it('引用换行和双引号，并防止表格公式注入', () => {
    expect(analyticsCsv(snapshot)).toContain('"\'=SUM(1,2)\n""单位""","9","3"')
  })
  it('空分类与覆盖信息不伪造记录', () => {
    const csv = analyticsCsv({ ...snapshot, trends: { ...snapshot.trends, items: [] }, distributions: { ...snapshot.distributions, achievementTypes: [], organizations: [] }, collaboration: { ...snapshot.collaboration, authors: [] } })
    expect(csv).not.toContain('年度趋势')
    expect(csv).not.toContain('字段覆盖')
    expect(csv).toContain('"总览","成果","","3"')
  })
})
