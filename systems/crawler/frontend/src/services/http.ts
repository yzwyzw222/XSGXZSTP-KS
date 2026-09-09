import axios, { type AxiosInstance } from 'axios'

/**
 * 全站唯一的 Axios 传输实例。
 *
 * 设计约束（与后端契约一一对应）：
 * - `withCredentials` 保持 HttpOnly Cookie 会话，等价于原 `credentials: 'same-origin'`，
 *   前端不保存任何 JWT 或会话令牌；
 * - `validateStatus` 恒为 true：HTTP 状态一律由业务层判定。
 *   这样健康检查在 503 时仍能解析出合法的 `DOWN` 状态，
 *   而普通业务请求的错误判定集中在 `services/api.ts`，不受此处影响；
 * - 不在实例上注册任何请求/响应拦截器，从根本上避免拦截器递归请求 CSRF；
 * - 超时不在实例上固定，由调用方按"剩余预算"逐次指定，
 *   使 CSRF 获取与业务请求共享同一个截止时间。
 */
export const httpClient: AxiosInstance = axios.create({
  withCredentials: true,
  validateStatus: () => true,
  timeout: 0,
})

/** 默认请求预算：CSRF 获取与业务请求共用这 12 秒。 */
export const DEFAULT_TIMEOUT_MS = 12_000

let sessionController = new AbortController()

/** 会话切换立即取消旧账号的业务请求；认证写入由会话层串行处理。 */
export function cancelSessionRequests(): void {
  sessionController.abort()
  sessionController = new AbortController()
}

/** 请求生命周期独立于 store，句柄只存在于闭包中。 */
export function requestLifetime(timeoutMs?: number, signal?: AbortSignal, sessionScoped = true) {
  const budget = timeoutMs ?? DEFAULT_TIMEOUT_MS
  if (!Number.isFinite(budget) || budget <= 0) throw new RangeError('请求预算必须为正数')
  const controller = new AbortController()
  const sources = [signal, sessionScoped ? sessionController.signal : undefined].filter(
    (value): value is AbortSignal => value !== undefined,
  )
  let timedOut = false
  const cancel = () => controller.abort()
  sources.forEach((source) => {
    if (source.aborted) cancel()
    else source.addEventListener('abort', cancel, { once: true })
  })
  const timer = setTimeout(() => { timedOut = true; controller.abort() }, budget)
  return {
    signal: controller.signal,
    deadline: Date.now() + budget,
    get timedOut() { return timedOut },
    dispose() {
      clearTimeout(timer)
      sources.forEach((source) => source.removeEventListener('abort', cancel))
    },
  }
}

/** 计算距离截止时间还剩多少毫秒；至少 1ms，避免 0 被 Axios 解释为"不超时"。 */
export function remainingBudget(deadline: number): number {
  return Math.max(1, deadline - Date.now())
}

