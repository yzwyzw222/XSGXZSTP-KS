import { get } from './request'

// 关键词（研究主题）列表，search 可选：按名称模糊匹配
export function fetchKeywords(search) {
  return get('/keywords', { params: { search } })
}

// 某个关键词下的论文分页列表，page 从 1 开始
export function fetchKeywordPapers(topicId, page = 1, size = 10) {
  return get(`/keywords/${encodeURIComponent(topicId)}/papers`, { params: { page, size } })
}
