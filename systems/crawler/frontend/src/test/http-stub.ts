import { AxiosError, CanceledError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'

import { httpClient } from '@/services/http'

/** 桩响应；`data` 为字符串时会经过 Axios 真实的 transformResponse 解析。 */
export interface StubResponse {
  status?: number
  statusText?: string
  data?: unknown
  headers?: Record<string, string>
}

export type StubHandler = (
  config: InternalAxiosRequestConfig,
) => StubResponse | Promise<StubResponse>

/** 记录实际发出的请求，供断言 URL、方法、请求头与请求体。 */
export interface StubRequest {
  url: string
  method: string
  headers: Record<string, string>
  data: unknown
  withCredentials: boolean
  timeout?: number
}

export interface HttpStub {
  requests: StubRequest[]
  restore: () => void
}

function normalizeHeaders(config: InternalAxiosRequestConfig): Record<string, string> {
  const source = config.headers as unknown as Record<string, unknown> | undefined
  const result: Record<string, string> = {}
  Object.entries(source ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null) result[key] = String(value)
  })
  return result
}

/**
 * 用可控 adapter 替换真实网络传输。
 *
 * `adapter` 是 Axios 的官方配置项，因此请求头组装、`transformResponse`、
 * 取消判定、`validateStatus` 等真实管线仍然会执行；
 * 桩本身额外实现超时与取消，行为与内置 xhr/fetch adapter 一致。
 * 这样测试验证的是 `services/api.ts` 的真实契约，而不只是 mock 自身。
 */
export function installHttpStub(handler: StubHandler): HttpStub {
  const requests: StubRequest[] = []
  const previous = httpClient.defaults.adapter

  httpClient.defaults.adapter = (config: InternalAxiosRequestConfig) =>
    new Promise<AxiosResponse>((resolve, reject) => {
      requests.push({
        url: String(config.url ?? ''),
        method: String(config.method ?? 'get').toUpperCase(),
        headers: normalizeHeaders(config),
        data: config.data,
        withCredentials: config.withCredentials === true,
        timeout: config.timeout,
      })

      let settled = false
      let timer: ReturnType<typeof setTimeout> | undefined
      const cleanup = (): void => {
        if (timer !== undefined) clearTimeout(timer)
        config.signal?.removeEventListener?.('abort', onAbort)
      }
      function onAbort(): void {
        if (settled) return
        settled = true
        cleanup()
        reject(new CanceledError('canceled', config))
      }

      if (config.signal) {
        if (config.signal.aborted) {
          onAbort()
          return
        }
        config.signal.addEventListener?.('abort', onAbort, { once: true })
      }
      if (typeof config.timeout === 'number' && config.timeout > 0) {
        timer = setTimeout(() => {
          if (settled) return
          settled = true
          cleanup()
          reject(new AxiosError(
            `timeout of ${config.timeout}ms exceeded`,
            AxiosError.ECONNABORTED,
            config,
            {},
          ))
        }, config.timeout)
      }

      Promise.resolve()
        .then(() => handler(config))
        .then((stub) => {
          if (settled) return
          settled = true
          cleanup()
          const response: AxiosResponse = {
            data: stub.data ?? '',
            status: stub.status ?? 200,
            statusText: stub.statusText ?? '',
            headers: stub.headers ?? { 'content-type': 'application/json' },
            config,
            request: {},
          }
          const validate = config.validateStatus
          if (!validate || validate(response.status)) {
            resolve(response)
            return
          }
          reject(new AxiosError(
            `Request failed with status code ${response.status}`,
            response.status >= 500 ? AxiosError.ERR_BAD_RESPONSE : AxiosError.ERR_BAD_REQUEST,
            config,
            {},
            response,
          ))
        }, (error: unknown) => {
          if (settled) return
          settled = true
          cleanup()
          reject(error)
        })
    })

  return {
    requests,
    restore: () => {
      httpClient.defaults.adapter = previous
    },
  }
}

/** 构造 JSON 响应桩。 */
export function json(body: unknown, status = 200): StubResponse {
  return {
    status,
    statusText: status === 200 ? 'OK' : `HTTP ${status}`,
    data: JSON.stringify(body),
    headers: { 'content-type': 'application/json' },
  }
}

/** 构造 problem+json 错误响应桩。 */
export function problem(body: Record<string, unknown>, status: number): StubResponse {
  return {
    status,
    statusText: `HTTP ${status}`,
    data: JSON.stringify(body),
    headers: { 'content-type': 'application/problem+json' },
  }
}

/** 构造空响应（204 或无响应体）。 */
export function empty(status = 204): StubResponse {
  return { status, statusText: status === 204 ? 'No Content' : '', data: '', headers: {} }
}

/** 构造二进制下载响应桩。 */
export function blob(content: string, type = 'text/csv', status = 200): StubResponse {
  return {
    status,
    statusText: status === 200 ? 'OK' : `HTTP ${status}`,
    data: new Blob([content], { type }),
    headers: { 'content-type': type },
  }
}

/** 模拟网络不可达。 */
export function networkError(): AxiosError {
  return new AxiosError('Network Error', AxiosError.ERR_NETWORK, undefined, {})
}
