/**
 * 统一 HTTP 请求层（规范要求使用原生 fetch，不引入 Axios）。
 * 内置机制：
 *  - 超时控制：AbortController + 12 秒定时器，支持调用方传入外部 signal 级联取消；
 *  - 同源凭证：credentials: 'same-origin'，让浏览器自动携带 SESSION Cookie；
 *  - CSRF：非安全方法自动附加 X-CSRF-TOKEN 请求头（Token 来自 session.ts 内存）；
 *  - 错误模型：解析后端 application/problem+json，封装为 ApiError（含 errorCode/traceId）；
 *  - 401 处理：自动清空会话状态，由页面路由跳回登录。
 */
import { clearSession, getCsrfToken, setCsrfToken } from '../session'

const TIMEOUT_MS = 12_000

/**
 * 从 Cookie 中读取 XSRF-TOKEN（Spring Security 的 CookieCsrfTokenRepository 每次响应都会写入）。
 * 仅当内存态 Token 为空时（如页面刷新后）作为兜底，避免每次请求都解析 Cookie。
 */
function readCsrfCookie(): string | null {
  const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

/** 与后端 problem+json 错误模型一一对应（见 docs/openapi.yaml 的 Problem schema） */
export interface ProblemDetail {
  status: number
  title: string
  detail: string
  instance: string
  errorCode: string
  traceId: string
  fieldErrors?: { field: string; message: string }[]
}

/** 业务/网络错误的统一异常类型：status 为 0 表示网络层失败（超时/断网） */
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

/**
 * 核心请求函数：拼接请求头 → 发送 → 解析响应 → 归一化错误。
 * 所有页面只通过本函数及其便捷方法（get/post/put/del/upload）访问后端。
 * body（JSON）与 formData（multipart 文件上传）二选一：文件上传时不手动设置
 * Content-Type，让浏览器自动生成带 boundary 的 multipart 头。
 */
export async function request<T>(
  path: string,
  options: { method?: string; body?: unknown; formData?: FormData; signal?: AbortSignal } = {}
): Promise<T> {
  const { method = 'GET', body, formData, signal } = options

  // 1) 超时与取消：内部定时器 + 外部 signal 双重 AbortController
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS)
  if (signal) {
    signal.addEventListener('abort', () => controller.abort(), { once: true })
  }

  // 2) 请求头：JSON 格式约定 + CSRF Header（仅非安全方法）
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined && formData === undefined) {
    headers['Content-Type'] = 'application/json'
  }
  const unsafe = !['GET', 'HEAD', 'OPTIONS'].includes(method)
  if (unsafe) {
    const csrf = getCsrfToken() ?? readCsrfCookie()
    if (csrf) {
      headers['X-CSRF-TOKEN'] = csrf
    }
  }

  try {
    const res = await fetch(path, {
      method,
      headers,
      body: formData !== undefined ? formData : (body !== undefined ? JSON.stringify(body) : undefined),
      credentials: 'same-origin',
      signal: controller.signal
    })

    // 3) 后端每次响应都会回传最新 CSRF Token（响应 Header），同步到内存
    const token = res.headers.get('X-CSRF-TOKEN')
    if (token) {
      setCsrfToken(token)
    }

    if (res.status === 204) {
      return undefined as T
    }

    // 4) 响应解析：成功返回 JSON 数据；失败提取 problem+json 并抛出 ApiError
    const text = await res.text()
    const data = text ? (JSON.parse(text) as unknown) : null
    if (!res.ok) {
      const problem =
        data && typeof data === 'object' && 'errorCode' in (data as object)
          ? (data as ProblemDetail)
          : null
      if (res.status === 401) {
        // 会话失效：清空内存态会话，路由守卫会引导回登录页
        clearSession()
      }
      throw new ApiError(res.status, problem, `请求失败（${res.status}）`)
    }
    return data as T
  } catch (err) {
    // 5) 错误归一化：把超时/断网等原生异常也转成 ApiError，页面只处理一种错误类型
    if (err instanceof ApiError) {
      throw err
    }
    if ((err as Error).name === 'AbortError') {
      throw new ApiError(0, null, '请求超时或已取消')
    }
    throw new ApiError(0, null, '网络异常，请稍后重试')
  } finally {
    // 6) 无论成败都清理定时器，避免计时器泄漏
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

/** 文件上传（multipart/form-data）：复用 request 的超时/CSRF/错误归一化逻辑 */
export function upload<T>(path: string, formData: FormData, signal?: AbortSignal): Promise<T> {
  return request<T>(path, { method: 'POST', formData, signal })
}
