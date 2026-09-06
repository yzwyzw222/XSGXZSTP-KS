import { clearSession, getCsrfToken, setCsrfToken } from '../session'

const TIMEOUT_MS = 12_000

export interface ProblemDetail {
  status: number
  title: string
  detail: string
  instance: string
  errorCode: string
  traceId: string
  fieldErrors?: { field: string; message: string }[]
}

export class ApiError extends Error {
  status: number
  problem: ProblemDetail | null

  constructor(status: number, problem: ProblemDetail | null, fallback: string) {
    super(problem?.detail ?? fallback)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

export async function request<T>(
  path: string,
  options: { method?: string; body?: unknown; signal?: AbortSignal } = {}
): Promise<T> {
  const { method = 'GET', body, signal } = options
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS)
  if (signal) {
    signal.addEventListener('abort', () => controller.abort(), { once: true })
  }

  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  const unsafe = !['GET', 'HEAD', 'OPTIONS'].includes(method)
  const csrf = getCsrfToken()
  if (unsafe && csrf) {
    headers['X-CSRF-TOKEN'] = csrf
  }

  try {
    const res = await fetch(path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      credentials: 'same-origin',
      signal: controller.signal
    })
    const token = res.headers.get('X-CSRF-TOKEN')
    if (token) {
      setCsrfToken(token)
    }
    if (res.status === 204) {
      return undefined as T
    }
    const text = await res.text()
    const data = text ? (JSON.parse(text) as unknown) : null
    if (!res.ok) {
      const problem =
        data && typeof data === 'object' && 'errorCode' in (data as object)
          ? (data as ProblemDetail)
          : null
      if (res.status === 401) {
        clearSession()
      }
      throw new ApiError(res.status, problem, `请求失败（${res.status}）`)
    }
    return data as T
  } catch (err) {
    if (err instanceof ApiError) {
      throw err
    }
    if ((err as Error).name === 'AbortError') {
      throw new ApiError(0, null, '请求超时或已取消')
    }
    throw new ApiError(0, null, '网络异常，请稍后重试')
  } finally {
    clearTimeout(timeout)
  }
}

export function get<T>(path: string, signal?: AbortSignal): Promise<T> {
  return request<T>(path, { method: 'GET', signal })
}

export function post<T>(path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  return request<T>(path, { method: 'POST', body, signal })
}

export function put<T>(path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  return request<T>(path, { method: 'PUT', body, signal })
}

export function del<T>(path: string, signal?: AbortSignal): Promise<T> {
  return request<T>(path, { method: 'DELETE', signal })
}
