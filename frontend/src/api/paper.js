import api from './client.js'

export function searchPapers(params) {
  return api.get('/papers', params)
}

export function getPaper(id) {
  return api.get(`/papers/${id}`)
}
