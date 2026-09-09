import { get } from './request'

// 按姓名模糊搜索学者 [{id, name, paperCount, institution}]
export function searchScholars(name) {
  return get('/scholars', { params: { name } })
}

// 学者完整画像
export function fetchScholarProfile(id) {
  return get(`/scholars/${id}/profile`)
}
