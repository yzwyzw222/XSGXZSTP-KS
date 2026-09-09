import { get } from './request'

// 作者列表，search 可选：按姓名模糊匹配
export function fetchAuthors(search) {
  return get('/authors', { params: { search } })
}

// 某作者名下的论文分页列表，page 从 1 开始
export function fetchAuthorPapers(authorId, page = 1, size = 10) {
  return get(`/authors/${encodeURIComponent(authorId)}/papers`, { params: { page, size } })
}
