import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import {
  ApiError,
  api,
  clearCsrfToken,
  resetUnauthorizedLatch,
  setUnauthorizedHandler,
  toErrorMessage,
} from '@/services/api'
import { runSessionCleanups } from '@/services/session-scope'
import { cancelSessionRequests } from '@/services/http'
import type { CurrentUser, Permission } from '@/types/api'

export type SessionStatus = 'unknown' | 'loading' | 'authenticated' | 'anonymous'

/**
 * 会话、当前用户与权限的唯一权威状态源。
 *
 * - 认证信息与权限快照一律不持久化，刷新页面后由 `ensureSession` 重新向后端确认；
 * - `setUnauthorizedHandler` 在 store 建立时注册，不在模块加载期访问 store，
 *   避免 Pinia 与路由的初始化顺序问题；
 * - 通过 `generation` 代际号让迟到的响应自我作废，防止登录/登出交错后恢复失效状态。
 */
export const useSessionStore = defineStore('session', () => {
  const status = ref<SessionStatus>('unknown')
  const user = ref<CurrentUser | null>(null)
  const expired = ref(false)
  const lastError = ref('')
  /** 会话代际：建立、注销与失效都会推进。 */
  const generation = ref(0)

  /** 并发的会话读取共用同一个在途请求，避免重复访问 /auth/me。 */
  let pending: Promise<CurrentUser | null> | null = null
  let authQueue: Promise<unknown> = Promise.resolve()

  /** 认证写请求按发起顺序完成，退出必定在先前登录的 Cookie 轮换之后执行。 */
  function enqueueAuth<T>(operation: () => Promise<T>): Promise<T> {
    const next = authQueue.then(operation, operation)
    authQueue = next.then(() => undefined, () => undefined)
    return next
  }

  const isAuthenticated = computed(() => status.value === 'authenticated')
  const isAnonymous = computed(() => status.value === 'anonymous')
  const permissions = computed<readonly Permission[]>(() => user.value?.permissions ?? [])
  const roles = computed<readonly string[]>(() => user.value?.roles ?? [])
  const username = computed(() => user.value?.username ?? '')
  const currentUserId = computed(() => user.value?.id ?? null)

  function hasPermission(permission?: Permission): boolean {
    return permission === undefined || permissions.value.includes(permission)
  }

  function hasEveryPermission(required: readonly Permission[]): boolean {
    return required.every((permission) => permissions.value.includes(permission))
  }

  /** 清理与当前账号关联的数据、请求与轮询，并回到匿名态。 */
  function makeAnonymous(nextExpired: boolean): void {
    status.value = 'anonymous'
    user.value = null
    expired.value = nextExpired
    lastError.value = ''
    generation.value += 1
    pending = null
    cancelSessionRequests()
    runSessionCleanups()
  }

  /**
   * 请求层收到 401 时回调。
   * 已经处于匿名态时直接返回，保证并发 401 不会反复覆盖 `expired` 标记。
   */
  function markUnauthorized(): void {
    if (status.value === 'anonymous') return
    makeAnonymous(status.value === 'authenticated')
  }

  setUnauthorizedHandler(markUnauthorized)

  async function ensureSession(): Promise<CurrentUser | null> {
    if (status.value === 'authenticated') return user.value
    if (status.value === 'anonymous') return null
    if (pending) return pending

    const startedAt = generation.value
    const request = (async (): Promise<CurrentUser | null> => {
      status.value = 'loading'
      try {
        const current = await api.get<CurrentUser>('/api/v1/auth/me')
        // 期间发生登出或会话失效，迟到响应不得恢复已失效的状态。
        if (generation.value !== startedAt) return null
        user.value = current
        status.value = 'authenticated'
        expired.value = false
        lastError.value = ''
        generation.value += 1
        resetUnauthorizedLatch()
        return current
      } catch (error) {
        if (generation.value !== startedAt) return null
        if (error instanceof ApiError && error.status === 401) {
          // 请求层已经通过 markUnauthorized 置为匿名态，这里不重复覆盖 expired。
          makeAnonymous(false)
        } else {
          status.value = 'unknown'
          user.value = null
          lastError.value = toErrorMessage(error)
        }
        return null
      }
    })()

    pending = request
    try {
      return await request
    } finally {
      if (pending === request) pending = null
    }
  }

  async function login(usernameInput: string, password: string): Promise<CurrentUser> {
    // 登录前丢弃匿名会话的 CSRF 令牌，登录后再丢弃一次以强制获取轮换后的令牌。
    makeAnonymous(false)
    const startedAt = generation.value
    return enqueueAuth(async () => {
      if (generation.value !== startedAt) throw new ApiError('请求已取消', 0, { code: 'REQUEST_CANCELLED' })
      clearCsrfToken()
      try {
        const current = await api.post<CurrentUser>('/api/v1/auth/login', {
          username: usernameInput,
          password,
        })
        clearCsrfToken()
        if (generation.value !== startedAt) throw new ApiError('请求已取消', 0, { code: 'REQUEST_CANCELLED' })
        user.value = current
        status.value = 'authenticated'
        expired.value = false
        lastError.value = ''
        generation.value += 1
        return current
      } finally {
        // 登录失败（含 401 凭据错误）不得留下已触发的会话失效闩锁。
        clearCsrfToken()
        if (generation.value === startedAt || user.value) resetUnauthorizedLatch()
      }
    })
  }

  async function logout(): Promise<void> {
    makeAnonymous(false)
    await enqueueAuth(async () => {
      clearCsrfToken()
      try {
        await api.post<void>('/api/v1/auth/logout')
      } finally {
        clearCsrfToken()
        resetUnauthorizedLatch()
      }
    })
  }

  /** 仅用于测试：恢复到未初始化状态。 */
  function resetForTest(): void {
    pending = null
    status.value = 'unknown'
    user.value = null
    expired.value = false
    lastError.value = ''
    generation.value += 1
    clearCsrfToken()
    resetUnauthorizedLatch()
  }

  return {
    status,
    user,
    expired,
    lastError,
    generation,
    isAuthenticated,
    isAnonymous,
    permissions,
    roles,
    username,
    currentUserId,
    hasPermission,
    hasEveryPermission,
    markUnauthorized,
    ensureSession,
    login,
    logout,
    resetForTest,
  }
})
