const BASE = '/api/v1'
const API_TIMEOUT = 12000

let csrfToken = null

export async function initCsrf() {
  try {
    const res = await fetch(`${BASE}/auth/csrf`, { credentials: 'same-origin' })
    if (res.ok) {
      const data = await res.json()
      csrfToken = data.token
    }
  } catch {
    // CSRF not available
  }
}

function getCsrfHeader() {
  return csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}
}

async function request(path, options = {}) {
  const url = `${BASE}${path}`
  const headers = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
    ...getCsrfHeader(),
    ...(options.headers || {})
  }

  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), API_TIMEOUT)

  try {
    const res = await fetch(url, {
      credentials: 'same-origin',
      signal: controller.signal,
      ...options,
      headers
    })

    if (res.status === 403 && options._retry !== true) {
      await initCsrf()
      return request(path, { ...options, _retry: true })
    }

    if (!res.ok) {
      let body
      try {
        body = await res.json()
      } catch {
        body = { detail: res.statusText }
      }
      const error = new Error(body.detail || body.title || `HTTP ${res.status}`)
      error.status = res.status
      error.body = body
      throw error
    }

    if (res.status === 204) return null
    return res.json()
  } catch (err) {
    if (err.name === 'AbortError') {
      throw new Error('Request timed out, please try again')
    }
    throw err
  } finally {
    clearTimeout(timeoutId)
  }
}

export const api = {
  get(path, params) {
    const qs = params ? '?' + new URLSearchParams(
      Object.fromEntries(Object.entries(params).filter(([, v]) => v != null && v !== ''))
    ).toString() : ''
    return request(`${path}${qs}`)
  },
  post(path, body) {
    return request(path, { method: 'POST', body: JSON.stringify(body) })
  },
  put(path, body) {
    return request(path, { method: 'PUT', body: JSON.stringify(body) })
  },
  delete(path) {
    return request(path, { method: 'DELETE' })
  }
}

export default api
