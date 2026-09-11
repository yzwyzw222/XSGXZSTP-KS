import { createMemoryHistory } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { createAppRouter } from '@/router'
import type { CurrentUser } from '@/types/api'

const { ensureSession, hasPermission, cancelSessionRequests, storeState } = vi.hoisted(() => ({
  ensureSession: vi.fn<() => Promise<CurrentUser | null>>(),
  hasPermission: vi.fn<(permission?: string) => boolean>(),
  cancelSessionRequests: vi.fn(),
  storeState: { lastError: '' },
}))

vi.mock('@/services/http', async (importOriginal) => ({
  ...await importOriginal<typeof import('@/services/http')>(),
  cancelSessionRequests,
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
    cancelSessionRequests.mockReset()
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
    expect(router.currentRoute.value.query.redirect).toBe('/logs')
    expect(router.currentRoute.value.query.reason).toBe('unavailable')
  })

  it('缺少页面权限时进入 403 页面', async () => {
    ensureSession.mockResolvedValue({} as CurrentUser)
    hasPermission.mockReturnValue(false)
    const router = createAppRouter(createMemoryHistory())

    await router.push('/author-import')

    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(hasPermission).toHaveBeenCalledWith('AUTHOR_IMPORT')
  })

  it('具备权限时允许进入业务页面', async () => {
    ensureSession.mockResolvedValue({} as CurrentUser)
    hasPermission.mockReturnValue(true)
    const router = createAppRouter(createMemoryHistory())

    await router.push('/author-import')

    expect(router.currentRoute.value.name).toBe('author-import')
  })

  it.each(['/operations', '/operations/alerts', '/operations/events', '/operations/maintenance', '/operations/audits'])('旧入口 %s 转到日志，并按日志权限校验', async (path) => {
    ensureSession.mockResolvedValue({} as CurrentUser)
    hasPermission.mockImplementation((permission) => permission === 'OPERATIONS_READ')
    const router = createAppRouter(createMemoryHistory())
    await router.push(path)
    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(hasPermission).toHaveBeenCalledWith('AUDIT_READ')
    hasPermission.mockImplementation((permission) => permission === 'AUDIT_READ')
    await router.push(path)
    expect(router.currentRoute.value.name).toBe('logs')
  })

  it.each(['/sources', '/crawl', '/governance', '/quality'])('移除的业务入口 %s 返回不存在页面', async path => {
    ensureSession.mockResolvedValue({} as CurrentUser)
    hasPermission.mockReturnValue(true)
    const router = createAppRouter(createMemoryHistory())
    await router.push(path)
    expect(router.currentRoute.value.name).toBe('not-found')
  })

  it('重复的工作台研究入口合并到合作分析', async () => {
    ensureSession.mockResolvedValue({} as CurrentUser)
    hasPermission.mockImplementation((permission) => permission === 'ANALYTICS_READ')
    const router = createAppRouter(createMemoryHistory())
    await router.push('/overview/research')
    expect(router.currentRoute.value.name).toBe('analytics-collaboration')
  })

  it.each([
    ['/analytics/coverage', 'ANALYTICS_READ', 'analytics-coverage'],
    ['/analytics/distributions', 'ANALYTICS_READ', 'analytics-distributions'],
    ['/analytics/research', 'ANALYTICS_READ', 'analytics-research'],
    ['/analytics/collaboration', 'ANALYTICS_READ', 'analytics-collaboration'],
    ['/users/overview', 'USER_LIST', 'users-overview'],
    ['/academic-relations', 'GRAPH_READ', 'academic-relations'],
    ['/academic-achievements', 'GRAPH_READ', 'academic-achievements'],
    ['/academic-background', 'GRAPH_READ', 'academic-background'],
  ])('子模块 %s 继承父模块权限 %s', async (path, permission, name) => {
    ensureSession.mockResolvedValue({} as CurrentUser)
    hasPermission.mockReturnValue(false)
    const router = createAppRouter(createMemoryHistory())

    await router.push(path)
    expect(router.currentRoute.value.name).toBe('forbidden')
    expect(hasPermission).toHaveBeenCalledWith(permission)

    hasPermission.mockImplementation((required) => required === permission)
    await router.push(path)
    expect(router.currentRoute.value.name).toBe(name)
    expect(router.currentRoute.value.meta.permission).toBe(permission)
  })

  it.each([
    ['/analytics', '/analytics/coverage', '/analytics/collaboration'],
    ['/users', '/users/overview', '/users'],
    ['/academic-relations', '/academic-achievements', '/academic-background'],
  ])('从 %s 切换同模块子页保留请求，离开模块才取消', async (initial, next, last) => {
    ensureSession.mockResolvedValue({} as CurrentUser)
    hasPermission.mockReturnValue(true)
    const router = createAppRouter(createMemoryHistory())
    await router.push(initial)
    cancelSessionRequests.mockClear()

    await router.push(next)
    expect(router.currentRoute.value.path).toBe(next)
    await router.push(last)
    expect(router.currentRoute.value.path).toBe(last)
    expect(cancelSessionRequests).not.toHaveBeenCalled()

    await router.push('/logs')
    expect(router.currentRoute.value.name).toBe('logs')
    expect(cancelSessionRequests).toHaveBeenCalledTimes(1)
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
    await router.push('/academic-relations')
    expect(router.currentRoute.value.name).toBe('academic-relations')
    await router.push('/graph/settings/edges')
    expect(router.currentRoute.value.name).toBe('graph-relations')

    await router.push('/graph/queries')
    expect(router.currentRoute.value.name).toBe('graph-queries')
  })

  it.each(['relations', 'achievements', 'background'])('旧图谱地址 %s 保留作者、分类与分页', async (mode) => {
    ensureSession.mockResolvedValue({} as CurrentUser)
    hasPermission.mockReturnValue(true)
    const router = createAppRouter(createMemoryHistory())
    await router.push(`/graph/${mode}?authorId=12&category=PATENT&page=2`)
    expect(router.currentRoute.value.path).toBe(`/academic-${mode}`)
    expect(router.currentRoute.value.query).toEqual({ authorId: '12', category: 'PATENT', page: '2' })
  })

  it('作者旧链接进入新模块，作品旧链接仍进入高级查询', async () => {
    ensureSession.mockResolvedValue({} as CurrentUser)
    hasPermission.mockReturnValue(true)
    const router = createAppRouter(createMemoryHistory())
    await router.push('/graph?centerType=AUTHOR&centerId=12')
    expect(router.currentRoute.value.path).toBe('/academic-relations')
    expect(router.currentRoute.value.query).toMatchObject({ authorId: '12' })
    await router.push('/graph?centerType=ACHIEVEMENT&centerId=42')
    expect(router.currentRoute.value.path).toBe('/graph/explore')
    expect(router.currentRoute.value.query).toMatchObject({ centerType: 'ACHIEVEMENT', centerId: '42' })
  })
})
