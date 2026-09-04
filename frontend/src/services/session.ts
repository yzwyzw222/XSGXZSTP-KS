import { reactive } from 'vue'

import { ApiError, api, clearCsrfToken, setUnauthorizedHandler, toErrorMessage } from '@/services/api'
import type { CurrentUser, Permission } from '@/types/api'

type SessionStatus = 'unknown' | 'loading' | 'authenticated' | 'anonymous'

export const session = reactive<{
  status: SessionStatus
  user: CurrentUser | null
  expired: boolean
  lastError: string
}>({
  status: 'unknown',
  user: null,
  expired: false,
  lastError: '',
})

function makeAnonymous(expired: boolean): void {
  session.status = 'anonymous'
  session.user = null
  session.expired = expired
  session.lastError = ''
}

setUnauthorizedHandler(() => makeAnonymous(session.status === 'authenticated'))

export async function ensureSession(): Promise<CurrentUser | null> {
  if (session.status === 'authenticated') return session.user
  if (session.status === 'anonymous') return null

  session.status = 'loading'
  try {
    const user = await api.get<CurrentUser>('/api/v1/auth/me')
    session.user = user
    session.status = 'authenticated'
    session.expired = false
    session.lastError = ''
    return user
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      makeAnonymous(false)
    } else {
      session.status = 'unknown'
      session.user = null
      session.lastError = toErrorMessage(error)
    }
    return null
  }
}

export async function login(username: string, password: string): Promise<CurrentUser> {
  clearCsrfToken()
  const user = await api.post<CurrentUser>('/api/v1/auth/login', { username, password })
  clearCsrfToken()
  session.user = user
  session.status = 'authenticated'
  session.expired = false
  session.lastError = ''
  return user
}

export async function logout(): Promise<void> {
  try {
    await api.post<void>('/api/v1/auth/logout')
  } finally {
    clearCsrfToken()
    makeAnonymous(false)
  }
}

export function hasPermission(permission?: Permission): boolean {
  return permission === undefined || session.user?.permissions.includes(permission) === true
}

export function resetSessionForTest(): void {
  session.status = 'unknown'
  session.user = null
  session.expired = false
  session.lastError = ''
  clearCsrfToken()
}
