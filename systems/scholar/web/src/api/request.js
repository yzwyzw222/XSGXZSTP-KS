const API_BASE = `${import.meta.env.BASE_URL}api/v1`
import { integrated, redirectToPortal } from '../services/portal-auth.js'
const REQUEST_TIMEOUT = 12000

let csrfToken = null

export function getCsrfToken() {
  return csrfToken
}

export function clearCsrfToken() {
  csrfToken = null
}

async function fetchCsrf() {
  const res = await fetch(`${API_BASE}/auth/csrf`, {
    credentials: 'same-origin',
  })
  const token = res.headers.get('X-CSRF-TOKEN')
  if (token) {
    csrfToken = token
  }
  return res
}

function buildUrl(path, params) {
  const url = new URL(`${API_BASE}${path}`, window.location.origin)
  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null) {
        url.searchParams.set(key, value)
      }
    })
  }
  return url.toString()
}

export class ApiError extends Error {
  constructor(status, problem) {
    super(problem.detail || problem.title || '请求失败')
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
    this.traceId = problem.traceId
  }
}

async function handleResponse(res) {
  if (res.ok) {
    if (res.status === 204) return null
    const contentType = res.headers.get('content-type') || ''
    if (contentType.includes('application/json')) {
      return res.json()
    }
    return res.text()
  }

  // 集成模式回到统一登录，独立运行保持原有入口。
  if (res.status === 401) {
    csrfToken = null
    if (integrated) {
      redirectToPortal()
      throw new ApiError(401, { title: '登录已失效', detail: '请通过统一入口重新登录' })
    }
    const currentPath = window.location.pathname
    const loginPath = `${import.meta.env.BASE_URL}login`
    if (currentPath !== loginPath) {
      window.location.href = loginPath
    }
    throw new ApiError(401, { title: '未登录或会话已过期', detail: '请重新登录' })
  }

  // Try to parse error as application/problem+json
  let problem
  try {
    const contentType = res.headers.get('content-type') || ''
    if (contentType.includes('problem+json') || contentType.includes('application/json')) {
      problem = await res.json()
    }
  } catch {
    problem = { title: res.statusText, detail: await res.text().catch(() => '') }
  }

  throw new ApiError(res.status, problem)
}

export async function get(path, { params, signal } = {}) {
  const res = await fetch(buildUrl(path, params), {
    method: 'GET',
    credentials: 'same-origin',
    headers: { 'Accept': 'application/json' },
    signal,
  })
  return handleResponse(res)
}

export async function post(path, { body, signal } = {}) {
  if (!csrfToken) {
    await fetchCsrf()
  }

  const headers = { 'Accept': 'application/json' }
  if (csrfToken) {
    headers['X-CSRF-TOKEN'] = csrfToken
  }

  let fetchBody = body
  if (body !== undefined && !(body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
    fetchBody = JSON.stringify(body)
  }

  const res = await fetch(buildUrl(path), {
    method: 'POST',
    credentials: 'same-origin',
    headers,
    body: fetchBody,
    signal,
  })
  return handleResponse(res)
}

export async function put(path, { body, signal } = {}) {
  if (!csrfToken) {
    await fetchCsrf()
  }

  const headers = { 'Accept': 'application/json' }
  if (csrfToken) {
    headers['X-CSRF-TOKEN'] = csrfToken
  }

  let fetchBody = body
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    fetchBody = JSON.stringify(body)
  }

  const res = await fetch(buildUrl(path), {
    method: 'PUT',
    credentials: 'same-origin',
    headers,
    body: fetchBody,
    signal,
  })
  return handleResponse(res)
}

export async function del(path, { signal } = {}) {
  if (!csrfToken) {
    await fetchCsrf()
  }

  const res = await fetch(buildUrl(path), {
    method: 'DELETE',
    credentials: 'same-origin',
    headers: {
      'Accept': 'application/json',
      ...(csrfToken ? { 'X-CSRF-TOKEN': csrfToken } : {}),
    },
    signal,
  })
  return handleResponse(res)
}

export async function initCsrf() {
  await fetchCsrf()
}
