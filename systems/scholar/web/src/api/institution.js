import { get } from './request'

// 机构列表，search 可选：按名称模糊匹配
export function fetchInstitutions(search) {
  return get('/institutions', { params: { search } })
}

// 某机构参与的论文分页列表，page 从 1 开始
export function fetchInstitutionPapers(instId, page = 1, size = 10) {
  return get(`/institutions/${encodeURIComponent(instId)}/papers`, { params: { page, size } })
}
