import type { CsrfToken, ProblemDetails } from '@/types/api'

const API_TIMEOUT_MS = 12_000
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly traceId?: string
  readonly fieldErrors?: ProblemDetails['fieldErrors']

  constructor(message: string, status: number, problem: ProblemDetails = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = problem.code ?? problem.errorCode ?? `HTTP_${status}`
    this.traceId = problem.traceId
    this.fieldErrors = problem.fieldErrors
  }
}

type UnauthorizedHandler = () => void

let csrfToken: CsrfToken | null = null
let unauthorizedHandler: UnauthorizedHandler | null = null

export function setUnauthorizedHandler(handler: UnauthorizedHandler): void {
  unauthorizedHandler = handler
}

export function clearCsrfToken(): void {
  csrfToken = null
}

async function readProblem(response: Response): Promise<ProblemDetails> {
  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('json')) {
    return { detail: response.statusText }
  }

  try {
    return (await response.json()) as ProblemDetails
  } catch {
    return { detail: response.statusText }
  }
}

async function getCsrfToken(signal: AbortSignal): Promise<CsrfToken> {
  if (csrfToken) {
    return csrfToken
  }

  const response = await fetch('/api/v1/auth/csrf', {
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
    signal,
  })
  if (!response.ok) {
    const problem = await readProblem(response)
    if (response.status === 401) {
      clearCsrfToken()
      unauthorizedHandler?.()
    }
    throw new ApiError(problem.detail ?? problem.title ?? '无法获取安全令牌', response.status, problem)
  }
  csrfToken = (await response.json()) as CsrfToken
  return csrfToken
}

export interface ApiRequestOptions extends Omit<RequestInit, 'body' | 'signal'> {
  body?: unknown
  responseType?: 'json' | 'blob'
  timeoutMs?: number
  signal?: AbortSignal
}

export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const { body, responseType = 'json', timeoutMs, signal, ...requestOptions } = options
  const method = (requestOptions.method ?? 'GET').toUpperCase()
  const controller = new AbortController()
  const timeoutId = window.setTimeout(() => controller.abort(), timeoutMs ?? API_TIMEOUT_MS)
  const abortFromCaller = () => controller.abort()
  signal?.addEventListener('abort', abortFromCaller, { once: true })

  try {
    const headers = new Headers(requestOptions.headers)
    headers.set('Accept', responseType === 'blob' ? '*/*' : 'application/json')
    if (body !== undefined) {
      headers.set('Content-Type', 'application/json')
    }
    if (!SAFE_METHODS.has(method)) {
      const token = await getCsrfToken(controller.signal)
      headers.set(token.headerName, token.token)
    }

    const response = await fetch(path, {
      ...requestOptions,
      method,
      credentials: 'same-origin',
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    })

    if (!response.ok) {
      const problem = await readProblem(response)
      if (response.status === 401) {
        clearCsrfToken()
        unauthorizedHandler?.()
      }
      if (response.status === 403 && !SAFE_METHODS.has(method)) {
        clearCsrfToken()
      }
      throw new ApiError(
        problem.detail ?? problem.title ?? `请求失败：HTTP ${response.status}`,
        response.status,
        problem,
      )
    }
    if (response.status === 204) {
      return undefined as T
    }
    if (responseType === 'blob') {
      return (await response.blob()) as T
    }
    return (await response.json()) as T
  } catch (error) {
    if (error instanceof ApiError) {
      throw error
    }
    if (controller.signal.aborted && !signal?.aborted) {
      throw new ApiError('请求超时，请稍后重试', 0, { code: 'REQUEST_TIMEOUT' })
    }
    throw new ApiError(error instanceof Error ? error.message : '网络请求失败', 0, {
      code: signal?.aborted ? 'REQUEST_CANCELLED' : 'NETWORK_ERROR',
    })
  } finally {
    window.clearTimeout(timeoutId)
    signal?.removeEventListener('abort', abortFromCaller)
  }
}

export const api = {
  get: <T>(path: string, options?: ApiRequestOptions) => apiRequest<T>(path, options),
  post: <T>(path: string, body?: unknown, options?: ApiRequestOptions) =>
    apiRequest<T>(path, { ...options, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, options?: ApiRequestOptions) =>
    apiRequest<T>(path, { ...options, method: 'PUT', body }),
}

export function toErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return '登录会话已过期，请重新登录'
    if (error.status === 403) return '当前账号没有执行此操作的权限'
    if (error.status === 409) return '数据已被其他操作更新，请刷新后重试'
    return error.message
  }
  return '操作失败，请稍后重试'
}
