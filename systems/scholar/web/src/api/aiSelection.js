import { get } from './request'

// 数据来源列表（来源库 + 论文数）
export function fetchAiSources() {
  return get('/ai-selection/sources')
}

// 来源内论文搜索：search 匹配作者名、关键词或标题，page 从 1 开始
export function fetchAiPapers({ source, search, page = 1, size = 10 }) {
  return get('/ai-selection/papers', { params: { source, search, page, size } })
}
