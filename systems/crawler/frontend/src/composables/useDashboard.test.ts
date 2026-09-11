import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { ApiError } from '@/services/api'
import { useDashboard } from './useDashboard'

const mocks = vi.hoisted(() => ({ permissions: ['ANALYTICS_READ'], overview: vi.fn(), trends: vi.fn(), distributions: vi.fn(), collaboration: vi.fn(), imports: vi.fn(), audits: vi.fn() }))
vi.mock('@/stores/session', () => ({ useSessionStore: () => ({ hasPermission: (permission: string) => mocks.permissions.includes(permission) }) }))
vi.mock('@/services/business', () => ({ analyticsApi: mocks }))
vi.mock('@/services/author-import', () => ({ authorImportApi: { recent: mocks.imports } }))
vi.mock('@/services/audits', () => ({ getAudits: mocks.audits }))

function render() {
  let dashboard!: ReturnType<typeof useDashboard>
  const wrapper = mount(defineComponent({ setup() { dashboard = useDashboard(); return () => null } }))
  return { wrapper, dashboard }
}
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(done => { resolve = done })
  return { promise, resolve }
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.permissions = ['ANALYTICS_READ']
  mocks.overview.mockResolvedValue({ achievementCount: 12 })
  mocks.trends.mockResolvedValue({ items: [] })
  mocks.distributions.mockResolvedValue({ topics: [] })
  mocks.collaboration.mockResolvedValue({ authors: [], organizations: [] })
  mocks.imports.mockResolvedValue([])
  mocks.audits.mockResolvedValue({ items: [] })
})

describe('科研大屏读取边界', () => {
  it('科研账号不请求导入或日志接口，受限区域明确标识', async () => {
    const { wrapper, dashboard } = render()
    await flushPromises()
    expect(mocks.overview).toHaveBeenCalledOnce()
    expect(mocks.collaboration).toHaveBeenCalledWith({}, 20)
    expect(mocks.imports).not.toHaveBeenCalled()
    expect(mocks.audits).not.toHaveBeenCalled()
    expect(dashboard.state.imports.allowed).toBe(false)
    expect(dashboard.state.audits.data).toBeNull()
    wrapper.unmount()
  })

  it('运营账号只请求有权访问的区域', async () => {
    mocks.permissions = ['AUTHOR_IMPORT', 'AUDIT_READ']
    const { wrapper, dashboard } = render()
    await flushPromises()
    expect(mocks.overview).not.toHaveBeenCalled()
    expect(mocks.collaboration).not.toHaveBeenCalled()
    expect(mocks.imports).toHaveBeenCalledOnce()
    expect(mocks.audits).toHaveBeenCalledWith({ category: 'OPERATION' }, 0, 5)
    expect(dashboard.loading.value).toBe(false)
    wrapper.unmount()
  })

  it('刷新局部失败保留旧快照，其他区域继续更新且错误不会变成零值', async () => {
    const { wrapper, dashboard } = render()
    await flushPromises()
    mocks.overview.mockRejectedValueOnce(new ApiError('概览读取失败', 503))
    mocks.trends.mockResolvedValueOnce({ items: [{ publicationYear: 2026, achievementCount: 9 }] })
    await dashboard.refresh()
    expect(dashboard.state.overview.data?.achievementCount).toBe(12)
    expect(dashboard.state.overview.error).toContain('概览读取失败')
    expect(dashboard.state.trends.data?.items[0]?.achievementCount).toBe(9)
    expect(dashboard.state.trends.error).toBe('')
    expect(dashboard.loading.value).toBe(false)
    wrapper.unmount()
  })

  it('快速筛选和组件卸载后忽略迟到响应', async () => {
    const old = deferred<{ achievementCount: number }>()
    mocks.overview.mockReturnValueOnce(old.promise)
    const { wrapper, dashboard } = render()
    dashboard.yearRange.value = '5'
    mocks.overview.mockResolvedValueOnce({ achievementCount: 36 })
    await dashboard.refresh()
    old.resolve({ achievementCount: 999 })
    await flushPromises()
    expect(dashboard.state.overview.data?.achievementCount).toBe(36)
    expect(mocks.overview).toHaveBeenLastCalledWith({ publicationYearFrom: new Date().getFullYear() - 4 })
    const late = deferred<{ achievementCount: number }>()
    mocks.overview.mockReturnValueOnce(late.promise)
    const pending = dashboard.refresh()
    wrapper.unmount()
    late.resolve({ achievementCount: 1000 })
    await pending
    expect(dashboard.state.overview.data?.achievementCount).toBe(36)
  })
})
