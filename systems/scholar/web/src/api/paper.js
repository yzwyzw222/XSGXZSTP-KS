import { get } from './request'

// 论文列表，search 可选：按标题模糊匹配
export function fetchPapers(search) {
  return get('/papers', { params: { search } })
}

// 某篇论文包含的研究主题
export function fetchPaperTopics(paperId) {
  return get(`/papers/${encodeURIComponent(paperId)}/topics`)
}
