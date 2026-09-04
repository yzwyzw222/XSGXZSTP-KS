import { afterEach, describe, expect, it, vi } from 'vitest'

import { api } from '@/services/api'
import { login, resetSessionForTest } from '@/services/session'
import type { CurrentUser } from '@/types/api'

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('登录会话', () => {
  afterEach(() => {
    resetSessionForTest()
    vi.unstubAllGlobals()
  })

  it('登录成功后刷新 CSRF Token，避免复用匿名会话令牌', async () => {
    const user: CurrentUser = {
      id: 1,
      username: 'admin',
      roles: ['ADMIN'],
      permissions: ['ACCOUNT_SELF_READ'],
    }
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'anonymous-token' }))
      .mockResolvedValueOnce(jsonResponse(user))
      .mockResolvedValueOnce(jsonResponse({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'authenticated-token' }))
      .mockResolvedValueOnce(jsonResponse({ ok: true }))
    vi.stubGlobal('fetch', fetchMock)

    await login('admin', 'long-enough-password')
    await api.post('/api/v1/example', { value: 1 })

    expect(fetchMock).toHaveBeenCalledTimes(4)
    expect(new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get('X-CSRF-TOKEN')).toBe('anonymous-token')
    expect(new Headers(fetchMock.mock.calls[3]?.[1]?.headers).get('X-CSRF-TOKEN')).toBe('authenticated-token')
  })
})
