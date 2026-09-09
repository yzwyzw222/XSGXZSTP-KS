import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { api, clearCsrfToken } from '@/services/api'
import { registerSessionCleanup, resetSessionCleanupsForTest } from '@/services/session-scope'
import { useSessionStore } from '@/stores/session'
import { installHttpStub, json, problem, type HttpStub, type StubResponse } from '@/test/http-stub'
import type { CurrentUser } from '@/types/api'

const admin: CurrentUser = { id: 1, username: 'admin', roles: ['ADMIN'], permissions: ['ACCOUNT_SELF_READ', 'CATALOG_READ'] }
const researcher: CurrentUser = { id: 8, username: 'researcher', roles: ['RESEARCHER'], permissions: ['CATALOG_READ'] }
const csrfBody = { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'anonymous-token' }
const rotatedCsrf = { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'authenticated-token' }

describe('会话 store', () => {
  let stub: HttpStub | undefined

  beforeEach(() => {
    setActivePinia(createPinia())
    resetSessionCleanupsForTest()
    clearCsrfToken()
    localStorage.clear()
    sessionStorage.clear()
  })

  afterEach(() => {
    vi.useRealTimers()
    stub?.restore()
    stub = undefined
  })

  it('登录成功后刷新 CSRF Token，避免复用匿名会话令牌', async () => {
    let csrfCalls = 0
    stub = installHttpStub((config) => {
      if (config.url === '/api/v1/auth/csrf') {
        csrfCalls += 1
        return json(csrfCalls === 1 ? csrfBody : rotatedCsrf)
      }
      if (config.url === '/api/v1/auth/login') return json(admin)
      return json({ ok: true })
    })
    const store = useSessionStore()

    await store.login('admin', 'long-enough-password')
    await api.post('/api/v1/example', { value: 1 })

    expect(stub.requests).toHaveLength(4)
    expect(stub.requests[1]?.headers['X-CSRF-TOKEN']).toBe('anonymous-token')
    expect(stub.requests[3]?.headers['X-CSRF-TOKEN']).toBe('authenticated-token')
    expect(store.isAuthenticated).toBe(true)
    expect(store.username).toBe('admin')
  })

  it('登录失败不留下会话失效闩锁，错误按 401 单独呈现', async () => {
    stub = installHttpStub((config) =>
      config.url === '/api/v1/auth/csrf'
        ? json(csrfBody)
        : problem({ detail: '账号或密码错误', errorCode: 'BAD_CREDENTIALS' }, 401))
    const store = useSessionStore()

    await expect(store.login('admin', 'wrong-password')).rejects.toMatchObject({ status: 401 })
    expect(store.isAuthenticated).toBe(false)
    expect(store.expired).toBe(false)
  })

  it('并发读取会话只发出一次 /auth/me', async () => {
    stub = installHttpStub((config) =>
      config.url === '/api/v1/auth/me' ? json(researcher) : json({}))
    const store = useSessionStore()

    const [first, second, third] = await Promise.all([
      store.ensureSession(),
      store.ensureSession(),
      store.ensureSession(),
    ])

    expect(stub.requests.filter((request) => request.url === '/api/v1/auth/me')).toHaveLength(1)
    expect(first).toEqual(researcher)
    expect(second).toEqual(researcher)
    expect(third).toEqual(researcher)
  })

  it('已认证后重复读取不再访问后端', async () => {
    stub = installHttpStub(() => json(researcher))
    const store = useSessionStore()

    await store.ensureSession()
    await store.ensureSession()

    expect(stub.requests).toHaveLength(1)
  })

  it('未登录时 401 只回到匿名态，不标记会话过期', async () => {
    stub = installHttpStub(() => json({ title: 'Unauthorized', status: 401 }, 401))
    const store = useSessionStore()

    await expect(store.ensureSession()).resolves.toBeNull()
    expect(store.isAnonymous).toBe(true)
    expect(store.expired).toBe(false)
  })

  it('会话检查失败时保留可读原因，供登录页区分服务不可用', async () => {
    stub = installHttpStub(() => ({ status: 503, statusText: 'Service Unavailable', data: '', headers: {} }))
    const store = useSessionStore()

    await expect(store.ensureSession()).resolves.toBeNull()
    expect(store.status).toBe('unknown')
    expect(store.lastError).not.toBe('')
  })

  it('已认证后业务请求返回 401 时标记过期并清空当前用户', async () => {
    stub = installHttpStub((config) =>
      config.url === '/api/v1/auth/me' ? json(researcher) : json({ title: 'Unauthorized', status: 401 }, 401))
    const store = useSessionStore()
    await store.ensureSession()
    expect(store.isAuthenticated).toBe(true)

    await expect(api.get('/api/v1/catalog/achievements')).rejects.toMatchObject({ status: 401 })

    expect(store.expired).toBe(true)
    expect(store.user).toBeNull()
    expect(store.isAnonymous).toBe(true)
  })

  it('并发 401 只标记一次过期，不反复覆盖会话状态', async () => {
    stub = installHttpStub((config) =>
      config.url === '/api/v1/auth/me' ? json(researcher) : json({}, 401))
    const store = useSessionStore()
    await store.ensureSession()

    await Promise.allSettled([
      api.get('/api/v1/a'),
      api.get('/api/v1/b'),
      api.get('/api/v1/c'),
    ])

    expect(store.expired).toBe(true)
    expect(store.isAnonymous).toBe(true)
  })

  it('登出清理会话与账号关联资源，迟到的会话响应不恢复失效状态', async () => {
    let releaseMe!: () => void
    let meCalls = 0
    stub = installHttpStub((config) => {
      if (config.url === '/api/v1/auth/csrf') return json(csrfBody)
      if (config.url === '/api/v1/auth/me') {
        meCalls += 1
        // 第二次会话确认挂起，用来模拟登出之后才到达的迟到响应。
        return new Promise<StubResponse>((resolve) => {
          releaseMe = () => resolve(json(admin))
        })
      }
      return json({})
    })
    const store = useSessionStore()
    const cleanup = vi.fn()
    registerSessionCleanup(cleanup)

    // 并发：一条在途的会话确认请求 + 一次登出。
    const late = store.ensureSession()
    await store.logout()
    releaseMe()
    await expect(late).resolves.toBeNull()

    expect(meCalls).toBe(1)
    expect(cleanup).toHaveBeenCalledOnce()
    expect(store.isAnonymous).toBe(true)
    expect(store.user).toBeNull()
    expect(store.expired).toBe(false)
  })

  it('会话失效时同样清理账号关联资源', async () => {
    stub = installHttpStub((config) =>
      config.url === '/api/v1/auth/me' ? json(researcher) : json({}, 401))
    const store = useSessionStore()
    const cleanup = vi.fn()
    registerSessionCleanup(cleanup)
    await store.ensureSession()

    await expect(api.get('/api/v1/catalog/achievements')).rejects.toMatchObject({ status: 401 })

    expect(cleanup).toHaveBeenCalledOnce()
  })

  it('权限判定只依赖当前内存中的用户，不读取持久化快照', async () => {
    stub = installHttpStub(() => json(researcher))
    const store = useSessionStore()

    // 未声明权限的路由不做权限门禁，与路由守卫的既有语义一致。
    expect(store.hasPermission()).toBe(true)
    expect(store.hasPermission('CATALOG_READ')).toBe(false)

    await store.ensureSession()

    expect(store.hasPermission('CATALOG_READ')).toBe(true)
    expect(store.hasPermission('USER_LIST')).toBe(false)
    expect(store.hasEveryPermission(['CATALOG_READ', 'USER_LIST'])).toBe(false)
    expect(store.hasEveryPermission(['CATALOG_READ'])).toBe(true)
    // 认证信息与权限快照一律不落盘。
    expect(Object.keys(localStorage)).toHaveLength(0)
    expect(Object.keys(sessionStorage)).toHaveLength(0)
  })

  it('登出请求失败时仍然清理本地会话，避免用户被困在已失效界面', async () => {
    stub = installHttpStub((config) => {
      if (config.url === '/api/v1/auth/me') return json(researcher)
      if (config.url === '/api/v1/auth/csrf') return json(csrfBody)
      return problem({ detail: '服务暂不可用' }, 503)
    })
    const store = useSessionStore()
    await store.ensureSession()

    await expect(store.logout()).rejects.toMatchObject({ status: 503 })
    expect(store.isAnonymous).toBe(true)
    expect(store.user).toBeNull()
  })

  it('登录未完成时退出，迟到登录不得恢复用户且退出请求在登录之后发出', async () => {
    let release!: (value: StubResponse) => void
    stub = installHttpStub((config) => {
      if (config.url === '/api/v1/auth/csrf') return json(csrfBody)
      if (config.url === '/api/v1/auth/login') return new Promise<StubResponse>((resolve) => { release = resolve })
      return json({})
    })
    const store = useSessionStore()
    const login = expect(store.login('admin', 'test-password')).rejects.toMatchObject({ code: 'REQUEST_CANCELLED' })
    await vi.waitFor(() => expect(release).toBeTypeOf('function'))
    const logout = store.logout()
    expect(store.user).toBeNull()
    expect(stub.requests.some((request) => request.url === '/api/v1/auth/logout')).toBe(false)
    release(json(admin))
    await login
    await logout
    expect(store.isAnonymous).toBe(true)
    expect(stub.requests.at(-1)?.url).toBe('/api/v1/auth/logout')
  })

  it('旧账号迟到的 401 不会使新登录账号失效', async () => {
    let release!: (value: StubResponse) => void
    stub = installHttpStub((config) => {
      if (config.url === '/api/v1/auth/csrf') return json(csrfBody)
      if (config.url === '/api/v1/old') return new Promise<StubResponse>((resolve) => { release = resolve })
      return json(researcher)
    })
    const store = useSessionStore()
    await store.ensureSession()
    const old = expect(api.get('/api/v1/old')).rejects.toMatchObject({ code: 'REQUEST_CANCELLED' })
    await vi.waitFor(() => expect(release).toBeTypeOf('function'))
    await store.login('researcher', 'test-password')
    release(json({}, 401))
    await old
    expect(store.user).toEqual(researcher)
    expect(store.expired).toBe(false)
  })
})
