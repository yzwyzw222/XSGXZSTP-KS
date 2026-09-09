import { get } from './request'

// 1. 论文发表时间趋势 [{year, count}]；authorId 可选：仅该学者
export function fetchPublicationTrend(authorId) {
  return get('/analytics/publication-trend', { params: { authorId } })
}

// 1b. 年份-作者角色统计 [{year, total, firstAuthor, corresponding}]；authorId 可选
export function fetchAuthorRoleTrend(authorId) {
  return get('/analytics/author-role-trend', { params: { authorId } })
}

// 1c. 研究方向（主题）分布 [{name, count}]；authorId 可选
export function fetchTopicDistribution(topN = 10, authorId) {
  return get('/analytics/topic-distribution', { params: { topN, authorId } })
}

// 2. 研究主题演化 {years, topics: [{name, counts}]}；authorId 可选
export function fetchTopicEvolution(topN = 8, authorId) {
  return get('/analytics/topic-evolution', { params: { topN, authorId } })
}

// 3. 关键词共现网络 {nodes, links}；authorId 可选
export function fetchKeywordCooccurrence(topN = 30, minWeight = 2, authorId) {
  return get('/analytics/keyword-cooccurrence', { params: { topN, minWeight, authorId } })
}

// 4. 论文相似网络 {nodes, links}；authorId 可选
export function fetchPaperSimilarity(topN = 30, minShared = 2, authorId) {
  return get('/analytics/paper-similarity', { params: { topN, minShared, authorId } })
}

// 5. 合作作者网络 {nodes, links}；authorId 可选
export function fetchCoauthorNetwork(topN = 30, minWeight = 1, authorId) {
  return get('/analytics/coauthor-network', { params: { topN, minWeight, authorId } })
}

// 6. 机构合作网络 {nodes, links}；authorId 可选
export function fetchInstitutionNetwork(topN = 15, minWeight = 1, authorId) {
  return get('/analytics/institution-network', { params: { topN, minWeight, authorId } })
}

// 7. 期刊分布 [{name, count}]；authorId 可选
export function fetchVenueDistribution(topN = 15, authorId) {
  return get('/analytics/venue-distribution', { params: { topN, authorId } })
}

// 8. 被引统计 {totalCitations, avgPerPaper, citedPapers, topCited}；authorId 可选
export function fetchCitationStats(topN = 10, authorId) {
  return get('/analytics/citation-stats', { params: { topN, authorId } })
}

// 9. 论文类型分布 [{name, count}]；authorId 可选
export function fetchPaperTypeDistribution(authorId) {
  return get('/analytics/paper-type-distribution', { params: { authorId } })
}
