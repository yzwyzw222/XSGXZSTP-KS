import { createMemoryHistory } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { createAppRouter } from '@/router'
import type { CurrentUser } from '@/types/api'

const { ensureSession, hasPermission } = vi.hoisted(() => ({
  ensureSession: vi.fn<() => Promise<CurrentUser | null>>(),
  hasPermission: vi.fn<(permission?: string) => boolean>(),
}))

vi.mock('@/services/session', () => ({
  ensureSession,
  hasPermission,
  session: { lastError: '' },
}))

describe('权限路由', () => {
  beforeEach(() => {
    ensureSession.mockReset()
    hasPermission.mockReset()
  })

  it('未登录时保留目标地址并跳转登录页', async () => {
    ensureSession.mockResolvedValue(null)
    const router = createAppRouter(createMemoryHistory())

    await router.push('/catalog')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/catalog')
  })

  it('缺少页面权限时进入 403 页面', async () => {
    ensureSession.mockResolvedValue({} as CurrentUser)
    hasPermission.mockReturnValue(false)
    const router = createAppRouter(createMemoryHistory())

    await router.push('/sources')

    expect(router.currentRoute.value.name).toBe('forbidden')
  })

  it('具备权限时允许进入业务页面', async () => {
    ensureSession.mockResolvedValue({} as CurrentUser)
    hasPermission.mockReturnValue(true)
    const router = createAppRouter(createMemoryHistory())

    await router.push('/crawl')

    expect(router.currentRoute.value.name).toBe('crawl-tasks')
  })

  it('运行监控路由沿用OPERATIONS_READ权限门禁', async () => {
    ensureSession.mockResolvedValue({} as CurrentUser)
    hasPermission.mockImplementation((permission) => permission === 'OPERATIONS_READ')
    const router = createAppRouter(createMemoryHistory())

    await router.push('/operations')

    expect(router.currentRoute.value.name).toBe('operations')
    expect(hasPermission).toHaveBeenCalledWith('OPERATIONS_READ')
  })
})
