import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import WorkbenchView from './WorkbenchView.vue'
import { ApiError } from '@/services/api'
import type { ImportSummary } from '@/services/author-import'
import type { AnalyticsOverview } from '@/types/api'

const mocks = vi.hoisted(() => ({ permissions: [] as string[], overview: vi.fn(), recent: vi.fn() }))
vi.mock('@/stores/session', () => ({ useSessionStore: () => ({ hasPermission: (permission: string) => mocks.permissions.includes(permission) }) }))
vi.mock('@/services/business', () => ({ analyticsApi: { overview: mocks.overview } }))
vi.mock('@/services/author-import', async original => ({ ...await original<typeof import('@/services/author-import')>(), authorImportApi: { recent: mocks.recent } }))

const overview: AnalyticsOverview = {
  achievementCount: 1286, authorCount: 423, organizationCount: 98, sourceCount: 2,
  scope: { source: 'MYSQL', filters: {} }, updatedAt: '2026-09-12T04:00:00Z',
  coverage: { withDoiCount: 800, withPublicationYearCount: 1200, withAbstractCount: 1107, withCitationCount: 900,
    withOpenAccessStatusCount: 500, withRetractionStatusCount: 0, authorshipsMayBeIncompleteCount: 0 },
}
const batch: ImportSummary = {
  id: 9, authorId: 12, scholarName: '测试学者', fileName: '研究成果.csv', sheetName: '学者资料', importMode: 'AUTHOR',
  totalRows: 4, importedCount: 3, linkedCount: 0, skippedCount: 1, createdAt: '2026-09-12T03:00:00Z',
}
let wrapper: VueWrapper | undefined

async function render() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }] })
  await router.push('/')
  wrapper = mount(WorkbenchView, { global: { plugins: [router] } })
  return { view: wrapper, router }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}

beforeEach(() => {
  vi.resetAllMocks()
  mocks.permissions = ['CATALOG_READ', 'AUTHOR_IMPORT', 'GRAPH_READ', 'ANALYTICS_READ']
  mocks.overview.mockResolvedValue(structuredClone(overview))
  mocks.recent.mockResolvedValue([structuredClone(batch)])
})
afterEach(() => { wrapper?.unmount(); wrapper = undefined })

describe('工作台平台统计读取', () => {
  it('无统计权限时不请求概览、不显示指标，仍可查看已授权的导入记录', async () => {
    mocks.permissions = ['CATALOG_READ', 'AUTHOR_IMPORT']
    const { view } = await render()
    await flushPromises()
    expect(mocks.overview).not.toHaveBeenCalled()
    expect(view.find('[aria-label="平台数据概览"]').exists()).toBe(false)
    expect(mocks.recent).toHaveBeenCalledOnce()
    expect(view.get('.workbench-table').text()).toContain('测试学者')
    expect(view.get('.workbench-table').text()).toContain('新增 3 项 · 已存在 1 项')
  })

  it('统计失败提供独立重试，导入列表继续展示且重试不重复读取导入接口', async () => {
    mocks.overview.mockRejectedValueOnce(new ApiError('统计服务暂不可用', 503))
    const { view } = await render()
    await flushPromises()
    expect(view.get('.workbench-overview [role="status"]').text()).toContain('统计服务暂不可用')
    expect(view.get('.workbench-overview').attributes('aria-busy')).toBe('false')
    expect(view.get('.workbench-table').text()).toContain('研究成果.csv')
    expect(view.findAll('.workbench-overview__metrics strong').map(item => item.text())).toEqual(['—', '—', '—', '—'])
    await view.get('.workbench-overview button').trigger('click')
    await flushPromises()
    expect(mocks.overview).toHaveBeenCalledTimes(2)
    expect(mocks.recent).toHaveBeenCalledOnce()
    expect(view.find('.workbench-overview__error').exists()).toBe(false)
    expect(view.get('.workbench-overview__metrics').text()).toContain('1,286')
    expect(view.get('.workbench-table').text()).toContain('测试学者')
  })

  it('使用接口真实统计，统计详情、作者图谱与题名检索仍跳转到现有业务地址', async () => {
    const { view, router } = await render()
    await flushPromises()
    expect(mocks.overview).toHaveBeenCalledExactlyOnceWith({})
    expect(view.findAll('.workbench-overview__metrics strong').map(item => item.text())).toEqual(['1,286', '423', '98', '1,107'])
    await view.get('a[aria-label="查看平台统计详情"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/analytics')
    await view.get('.workbench-table__action').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/academic-achievements')
    expect(router.currentRoute.value.query).toEqual({ authorId: '12' })
    await view.get('#workbench-search').setValue('  开放科学  ')
    await view.get('form').trigger('submit')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/catalog')
    expect(router.currentRoute.value.query).toEqual({ title: '开放科学' })
  })

  it('空统计与缺失摘要覆盖按接口含义区分零值和未知，无导入权限时不读取导入接口', async () => {
    mocks.permissions = ['ANALYTICS_READ']
    mocks.overview.mockResolvedValue({ ...overview, achievementCount: 0, authorCount: 0, organizationCount: 0, coverage: null })
    const { view } = await render()
    await flushPromises()
    expect(view.findAll('.workbench-overview__metrics strong').map(item => item.text())).toEqual(['0', '0', '0', '—'])
    expect(mocks.recent).not.toHaveBeenCalled()
    expect(view.find('.workbench-recent').exists()).toBe(false)
  })

  it.each(['success', 'failure'] as const)('卸载后迟到的统计响应不再写入页面状态：%s', async result => {
    const pending = deferred<AnalyticsOverview>()
    mocks.overview.mockReturnValueOnce(pending.promise)
    const { view } = await render()
    await flushPromises()
    const state = view.vm as unknown as { overview: AnalyticsOverview | null; overviewError: string }
    expect(state.overview).toBeNull()
    expect(state.overviewError).toBe('')
    view.unmount()
    wrapper = undefined
    if (result === 'success') pending.resolve(overview)
    else pending.reject(new ApiError('迟到的统计失败', 503))
    await flushPromises()
    expect(state.overview).toBeNull()
    expect(state.overviewError).toBe('')
  })
})
