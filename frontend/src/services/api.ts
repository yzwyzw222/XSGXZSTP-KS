import { AxiosError, CanceledError, type AxiosResponse } from 'axios'

import { DEFAULT_TIMEOUT_MS, httpClient, remainingBudget, requestLifetime } from '@/services/http'
import type { CsrfToken, ProblemDetails } from '@/types/api'

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

/** CSRF 获取接口的固定路径；不额外拼接 /api/v1 前缀。 */
const CSRF_PATH = '/api/v1/auth/csrf'

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
/** 正在进行的 CSRF 请求，避免并发写操作重复获取令牌。 */
let csrfInFlight: Promise<CsrfToken> | null = null
/** 令牌代际：清理后仍在途的响应不得再写回缓存。 */
let csrfGeneration = 0
let csrfController: AbortController | null = null
let unauthorizedHandler: UnauthorizedHandler | null = null
/**
 * 会话失效闩锁：并发请求同时收到 401 时只通知会话层一次，
 * 避免重复退出与提示轰炸。建立新的已认证会话后由 `resetUnauthorizedLatch` 释放。
 */
let unauthorizedNotified = false

export function setUnauthorizedHandler(handler: UnauthorizedHandler): void {
  unauthorizedHandler = handler
}

/** 登录成功或会话恢复成功后调用，允许下一次会话失效再次通知。 */
export function resetUnauthorizedLatch(): void {
  unauthorizedNotified = false
}

/** 统一的会话失效处理：清理令牌并按闩锁通知一次。 */
function notifyUnauthorized(): void {
  clearCsrfToken()
  if (unauthorizedNotified) return
  unauthorizedNotified = true
  unauthorizedHandler?.()
}

/** 登录前后、退出登录与会话过期时调用，丢弃缓存令牌与在途请求的写回资格。 */
export function clearCsrfToken(): void {
  csrfController?.abort()
  csrfController = null
  csrfToken = null
  csrfInFlight = null
  csrfGeneration += 1
}

export interface ApiRequestOptions {
  method?: string
  body?: unknown
  headers?: Record<string, string>
  responseType?: 'json' | 'blob'
  /** 请求预算，覆盖 CSRF 获取与业务请求的总时限。 */
  timeoutMs?: number
  signal?: AbortSignal
}

/** 从响应中取出已解析的业务负载；空响应体统一视为 undefined。 */
function payloadOf<T>(response: AxiosResponse<unknown>): T {
  const data = response.data
  if (data === '' || data === null) return undefined as T
  return data as T
}

/**
 * 解析 application/problem+json 错误体。
 * Blob 下载失败时服务端仍返回 JSON 问题体，需要先读出文本再解析，
 * 避免把错误体当作文件内容交给调用方。
 */
async function readProblem(
  response: AxiosResponse<unknown>,
  responseType: 'json' | 'blob',
): Promise<ProblemDetails> {
  let data: unknown = response.data
  if (responseType === 'blob' && typeof Blob !== 'undefined' && data instanceof Blob) {
    data = await data.text()
  }

  const contentType = String(response.headers?.['content-type'] ?? '')
  if (!contentType.includes('json')) {
    return { detail: response.statusText }
  }
  if (typeof data === 'string') {
    try {
      data = JSON.parse(data)
    } catch {
      return { detail: response.statusText }
    }
  }
  if (data && typeof data === 'object') {
    return data as ProblemDetails
  }
  return { detail: response.statusText }
}

/** 把传输层异常翻译为对外的 ApiError，并区分取消、超时与网络故障。 */
function toApiError(error: unknown, signal?: AbortSignal): ApiError {
  if (error instanceof ApiError) return error
  // 调用方主动取消：不呈现为操作失败。
  if (signal?.aborted) return new ApiError('请求已取消', 0, { code: 'REQUEST_CANCELLED' })
  if (error instanceof CanceledError || (error instanceof AxiosError && error.code === 'ERR_CANCELED')) {
    return new ApiError('请求已取消', 0, { code: 'REQUEST_CANCELLED' })
  }
  if (error instanceof AxiosError) {
    if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
      return new ApiError('请求超时，请稍后重试', 0, { code: 'REQUEST_TIMEOUT' })
    }
    if (error.code === 'ERR_NETWORK') {
      return new ApiError('网络请求失败', 0, { code: 'NETWORK_ERROR' })
    }
    return new ApiError(error.message || '网络请求失败', 0, { code: 'NETWORK_ERROR' })
  }
  return new ApiError(error instanceof Error ? error.message : '网络请求失败', 0, {
    code: 'NETWORK_ERROR',
  })
}

/** 让共享的 CSRF 请求可以被单个调用方取消，而不影响其他等待者。 */
function raceWithSignal<T>(promise: Promise<T>, signal?: AbortSignal): Promise<T> {
  if (!signal) return promise
  if (signal.aborted) {
    return Promise.reject(new ApiError('请求已取消', 0, { code: 'REQUEST_CANCELLED' }))
  }
  return new Promise<T>((resolve, reject) => {
    const onAbort = (): void => reject(new ApiError('请求已取消', 0, { code: 'REQUEST_CANCELLED' }))
    signal.addEventListener('abort', onAbort, { once: true })
    promise.then(
      (value) => {
        signal.removeEventListener('abort', onAbort)
        resolve(value)
      },
      (error) => {
        signal.removeEventListener('abort', onAbort)
        reject(error)
      },
    )
  })
}

async function requestCsrfToken(signal: AbortSignal): Promise<CsrfToken> {
  const generation = csrfGeneration
  const response = await httpClient.request<unknown>({
    url: CSRF_PATH,
    method: 'GET',
    headers: { Accept: 'application/json' },
    timeout: DEFAULT_TIMEOUT_MS,
    signal,
  })

  if (signal.aborted || generation !== csrfGeneration) throw new CanceledError()
  if (response.status === 401) {
    notifyUnauthorized()
  }
  if (response.status < 200 || response.status >= 300) {
    const problem = await readProblem(response, 'json')
    throw new ApiError(
      problem.detail ?? problem.title ?? '无法获取安全令牌',
      response.status,
      problem,
    )
  }

  const token = payloadOf<CsrfToken>(response)
  if (!token || typeof token.headerName !== 'string' || !/^[!#$%&'*+.^_`|~\w-]+$/.test(token.headerName)
    || typeof token.token !== 'string' || !token.token.trim()) {
    throw new ApiError('安全令牌响应格式无效', response.status, { code: 'INVALID_CSRF_TOKEN' })
  }
  // 期间发生过清理（登录、退出、401）时不写回缓存，避免复活已失效的令牌。
  if (generation === csrfGeneration) {
    csrfToken = token
  }
  return token
}

function getCsrfToken(): Promise<CsrfToken> {
  if (csrfToken) return Promise.resolve(csrfToken)
  if (csrfInFlight) return csrfInFlight
  csrfController = new AbortController()
  const request = requestCsrfToken(csrfController.signal).finally(() => {
    if (csrfInFlight === request) {
      csrfInFlight = null
      csrfController = null
    }
  })
  csrfInFlight = request
  return request
}

/** 统一处理 4xx/5xx：解析问题体、清理令牌、通知会话层，并抛出 ApiError。 */
async function throwForStatus(
  response: AxiosResponse<unknown>,
  method: string,
  responseType: 'json' | 'blob',
  signal: AbortSignal,
): Promise<never> {
  const problem = await raceWithSignal(readProblem(response, responseType), signal)
  if (signal.aborted) throw new CanceledError()
  if (response.status === 401) {
    notifyUnauthorized()
  }
  // 写请求被拒绝时令牌可能已随会话轮换，清理后由下一次写请求重新获取。
  if (response.status === 403 && !SAFE_METHODS.has(method)) {
    clearCsrfToken()
  }
  throw new ApiError(
    problem.detail ?? problem.title ?? `请求失败：HTTP ${response.status}`,
    response.status,
    problem,
  )
}

export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const { body, headers, method: rawMethod, responseType = 'json', signal, timeoutMs } = options
  const method = (rawMethod ?? 'GET').toUpperCase()
  const lifetime = requestLifetime(timeoutMs, signal, !/^\/api\/v1\/auth\/(login|logout)$/.test(path))
  const { deadline } = lifetime

  try {
    if (lifetime.signal.aborted) throw new CanceledError()
    const requestHeaders: Record<string, string> = {
      Accept: responseType === 'blob' ? '*/*' : 'application/json',
      ...headers,
    }
    if (!SAFE_METHODS.has(method)) {
      const token = await raceWithSignal(getCsrfToken(), lifetime.signal)
      requestHeaders[token.headerName] = token.token
    }

    const response = await httpClient.request<unknown>({
      url: path,
      method,
      headers: requestHeaders,
      data: body,
      responseType: responseType === 'blob' ? 'blob' : 'json',
      timeout: remainingBudget(deadline),
      signal: lifetime.signal,
    })

    if (lifetime.signal.aborted) throw new CanceledError()
    if (response.status < 200 || response.status >= 300) {
      await throwForStatus(response, method, responseType, lifetime.signal)
    }
    if (response.status === 204) {
      return undefined as T
    }
    if (responseType === 'blob') {
      return response.data as T
    }
    return payloadOf<T>(response)
  } catch (error) {
    // 不打印原始 Axios 配置，避免泄漏密码、Cookie、CSRF 令牌或请求体。
    if (lifetime.timedOut) throw new ApiError('请求超时，请稍后重试', 0, { code: 'REQUEST_TIMEOUT' })
    throw toApiError(error, lifetime.signal)
  } finally {
    lifetime.dispose()
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
    if (error.code === 'REQUEST_CANCELLED') return ''
    if (error.status === 401) return '登录会话已过期，请重新登录'
    if (error.status === 403) return '当前账号没有执行此操作的权限'
    if (error.status === 409) return '数据已被其他操作更新，请刷新后重试'
    return error.message
  }
  return '操作失败，请稍后重试'
}
