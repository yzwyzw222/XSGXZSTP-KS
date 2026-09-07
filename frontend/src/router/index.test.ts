import { createMemoryHistory } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { createAppRouter } from '@/router'
import type { CurrentUser } from '@/types/api'

const { ensureSession, hasPermission, storeState } = vi.hoisted(() => ({
  ensureSession: vi.fn<() => Promise<CurrentUser | null>>(),
  hasPermission: vi.fn<(permission?: string) => boolean>(),
  storeState: { lastError: '' },
}))

// 路由守卫只依赖会话 store 的这三项契约；store 自身的行为由 stores/session.test.ts 覆盖。
vi.mock('@/stores/session', () => ({
  useSessionStore: () => ({
    ensureSession,
    hasPermission,
    get lastError() {
      return storeState.lastError
    },
  }),
}))

describe('权限路由', () => {
  beforeEach(() => {
    ensureSession.mockReset()
    hasPermission.mockReset()
    hasPermission.mockImplementation((permission) => permission === undefined)
    storeState.lastError = ''
  })

  it('未登录时保留目标地址并跳转登录页', async () => {
    ensureSession.mockResolvedValue(null)
    const router = createAppRouter(createMemoryHistory())

    await router.push('/catalog')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/catalog')
    expect(router.currentRoute.value.query.reason).toBeUndefined()
  })

  it('会话检查失败时把不可用原因带给登录页', async () => {
    ensureSession.mockResolvedValue(null)
    storeState.lastError = '网络请求失败'
    const router = createAppRouter(createMemoryHistory())

    await router.push('/operations')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/operations')
    expect(router.currentRoute.value.query.reason).toBe('unavailable')
  })

  it('缺少页面权限时进入 403 页面', async () => {
    ensureSession.mockResolvedValue({} as CurrentUser)
    hasPermission.mockReturnValue(false)
    const router = createAppRouter(createMemoryHistory())

    await router.push('/sources')

    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(hasPermission).toHaveBeenCalledWith('SOURCE_READ')
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

  it('已登录访问登录页时直接进入工作台', async () => {
    ensureSession.mockResolvedValue({} as CurrentUser)
    const router = createAppRouter(createMemoryHistory())

    await router.push('/login')

    expect(router.currentRoute.value.name).toBe('overview')
  })

  it('公开页面不做权限门禁', async () => {
    const router = createAppRouter(createMemoryHistory())

    await router.push('/forbidden')

    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(ensureSession).not.toHaveBeenCalled()
    expect(hasPermission).not.toHaveBeenCalled()
  })

  it('图谱子路由沿用GRAPH_READ权限门禁', async () => {
    ensureSession.mockResolvedValue({} as CurrentUser)
    hasPermission.mockImplementation((permission) => permission === 'GRAPH_READ')
    const router = createAppRouter(createMemoryHistory())

    await router.push('/graph/path')
    expect(router.currentRoute.value.name).toBe('graph-path')
    await router.push('/graph/entities')
    expect(router.currentRoute.value.name).toBe('graph-entities')
    await router.push('/graph/relations')
    expect(router.currentRoute.value.name).toBe('graph-relations')

    await router.push('/graph/queries')
    expect(router.currentRoute.value.name).toBe('graph-queries')
  })
})
