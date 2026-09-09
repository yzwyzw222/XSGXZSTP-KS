import api from './client.js'

export function searchAuthors(params) {
  return api.get('/authors', params)
}

export function getAuthor(id) {
  return api.get(`/authors/${id}`)
}

export function getAuthorGraph(id) {
  return api.get(`/authors/${id}/graph`)
}
