import api from './client.js'

export function searchAuthors(params) {
  const qs = new URLSearchParams({
    name: params.name || '',
    page: params.page || 0,
    size: params.size || 20
  })
  if (params.fetchRemote) {
    qs.set('fetchRemote', 'true')
  }
  return api.get(`/authors?${qs.toString()}`)
}

export function getAuthor(id) {
  return api.get(`/authors/${id}`)
}

export function getAuthorGraph(id) {
  return api.get(`/authors/${id}/graph`)
}
