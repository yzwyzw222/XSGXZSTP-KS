import api from './client.js'

export function getPaperGraph(id) {
  return api.get(`/graph/paper/${id}`)
}

export function getAuthorGraph(id) {
  return api.get(`/graph/author/${id}`)
}

export function getStatistics() {
  return api.get('/graph/statistics')
}
