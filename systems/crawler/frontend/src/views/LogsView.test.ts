import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import LogsView from './LogsView.vue'
import { getAudits } from '@/services/audits'
import type { AuditLog, PageResponse } from '@/types/api'

vi.mock('@/services/audits', () => ({ getAudits: vi.fn() }))

describe('日志查询请求生命周期', () => {
  beforeEach(() => vi.clearAllMocks())

  it('切换分类后忽略迟到的旧响应，卸载时取消在途请求', async () => {
    let resolveOld!: (value: PageResponse<AuditLog>) => void
    let resolveNew!: (value: PageResponse<AuditLog>) => void
    vi.mocked(getAudits).mockImplementationOnce(() => new Promise((resolve) => { resolveOld = resolve }))
      .mockImplementationOnce(() => new Promise((resolve) => { resolveNew = resolve }))
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/logs', component: LogsView }] })
    await router.push('/logs')
    const wrapper = mount(LogsView, { global: { plugins: [router], stubs: {
      AuditLogTable: { props: ['items'], template: '<div data-testid="rows">{{ items.map(row => row.id).join() }}</div>' },
    } } })
    await router.replace('/logs?category=LOGIN')
    await flushPromises()
    expect(vi.mocked(getAudits).mock.calls[0]?.[3]?.aborted).toBe(true)
    const page = (id: number): PageResponse<AuditLog> => ({ items: [{ id } as AuditLog], page: 0, size: 20, totalElements: 1, totalPages: 1 })
    resolveNew(page(2)); await flushPromises()
    resolveOld(page(1)); await flushPromises()
    expect(wrapper.get('[data-testid="rows"]').text()).toBe('2')
    wrapper.unmount()
    expect(vi.mocked(getAudits).mock.calls[1]?.[3]?.aborted).toBe(true)
  })
})
