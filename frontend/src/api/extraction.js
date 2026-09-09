import api from './client.js'

export function triggerExtraction(paperId) {
  return api.post('/extraction/trigger', { paperId })
}

export function getExtractionStatus(paperId) {
  return api.get(`/extraction/status/${paperId}`)
}
