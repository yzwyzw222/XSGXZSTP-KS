import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import { ApiError } from '@/services/api'
import { graphOverviewApi } from '@/services/graph-overview'
import { useGraphOverviewStore } from '@/stores/graph-overview'
import { useSessionStore } from '@/stores/session'
import type { GraphResponse } from '@/types/api'

vi.mock('@/services/graph-overview', () => ({ graphOverviewApi: { load: vi.fn() } }))
const load = vi.mocked(graphOverviewApi.load)
const result: GraphResponse = {
  nodes: [
    { id: 'a', businessId: '1', type: 'AUTHOR', label: '作者甲', properties: {} },
    { id: 'b', businessId: '2', type: 'ACHIEVEMENT', label: '作品乙', properties: {} },
  ], edges: [{ id: 'ab', type: 'AUTHORED', source: 'a', target: 'b', properties: {} }],
  rootNodeId: '', truncated: false, narrowingSuggestion: null, appliedLimits: { depth: 2, nodeLimit: 300, maxHops: 0 },
  syncedAt: null, projectionLagSeconds: null, traceId: 'store-test',
}
beforeEach(() => { setActivePinia(createPinia()); vi.useFakeTimers(); load.mockReset(); load.mockResolvedValue(result) })
afterEach(() => { useGraphOverviewStore().reset(); vi.useRealTimers() })

it('加载期间修改筛选不取消请求，本地筛选始终采用当前输入且不增加请求', async () => {
  const store = useGraphOverviewStore()
  let release!: (value: unknown) => void
  load.mockImplementationOnce(() => new Promise(resolve => { release = resolve }))
  const pending = store.refresh()
  store.filters.keyword = '不存在'
  expect(load.mock.calls[0]![1].aborted).toBe(false)
  release(result)
  await pending
  expect(store.graph?.nodes).toHaveLength(2)
  expect(store.visible?.nodes).toHaveLength(0)
  store.filters.keyword = '作品乙'
  await vi.advanceTimersByTimeAsync(500)
  expect(load).toHaveBeenCalledTimes(1)
  expect(store.visible?.nodes).toHaveLength(2)
  expect(store.loading).toBe(false)
})

it('双击中心、历史截断、返回与全部使用后端请求，刷新保持当前中心', async () => {
  const store = useGraphOverviewStore()
  await store.refresh()
  await store.enterNode(result.nodes[0]!)
  await store.enterNode(result.nodes[1]!)
  expect(store.history.map(item => item.id)).toEqual(['a', 'b'])
  await store.refresh()
  expect(load.mock.lastCall?.[0]).toMatchObject({ id: 'b', businessId: '2' })
  await store.goTo(0)
  expect(store.history.map(item => item.id)).toEqual(['a'])
  await store.enterNode(result.nodes[1]!)
  await store.enterNode(result.nodes[0]!)
  expect(store.history.map(item => item.id)).toEqual(['a'])
  await store.goTo(-1)
  expect(store.history).toEqual([])
  expect(load.mock.lastCall?.[0]).toBeUndefined()
  const calls = load.mock.calls.length
  await store.goTo(999)
  expect(load).toHaveBeenCalledTimes(calls)
})

it('失败或坏数据保留图与导航；再次成功才提交中心及统计', async () => {
  const store = useGraphOverviewStore()
  await store.refresh()
  load.mockRejectedValueOnce(new ApiError('查询失败', 503))
  await store.enterNode(result.nodes[0]!)
  expect(store.history).toEqual([])
  expect(store.graph?.nodes).toHaveLength(2)
  expect(store.errorMessage).toBe('查询失败')
  load.mockResolvedValueOnce({ ...result, nodes: [...result.nodes, result.nodes[0]] })
  await store.refresh()
  expect(store.errorMessage).toContain('ID 重复')
  expect(store.graph?.nodes).toHaveLength(2)
  await store.enterNode(result.nodes[0]!)
  expect(store.history).toHaveLength(1)
  expect(store.errorMessage).toBe('')
})

it('较早中心响应、离开页面和账号切换不能恢复旧数据，取消旧读取', async () => {
  const store = useGraphOverviewStore()
  let release!: (value: unknown) => void
  load.mockImplementationOnce(() => new Promise(resolve => { release = resolve }))
  const old = store.enterNode(result.nodes[0]!)
  await store.enterNode(result.nodes[1]!)
  release({ ...result, nodes: [], edges: [] })
  await old
  expect(store.history[0]?.id).toBe('b')
  store.filters.keyword = '作者'
  store.reset()
  const calls = load.mock.calls.length
  await vi.advanceTimersByTimeAsync(500)
  expect(load).toHaveBeenCalledTimes(calls)
  expect(store.graph).toBeNull()
  load.mockImplementationOnce(() => new Promise(resolve => { release = resolve }))
  const pending = store.refresh()
  useSessionStore().user = { id: 8, username: 'new-user', roles: ['RESEARCHER'], permissions: [] }
  release(result)
  await pending
  expect(store.graph).toBeNull()
  expect(store.history).toEqual([])
})

it('子图保留机构等非概览类型，关系和节点移除时同步清理选择', async () => {
  const store = useGraphOverviewStore()
  const institution = { id: 'i', businessId: '3', type: 'INSTITUTION', label: '机构', properties: {} } as const
  load.mockResolvedValueOnce({ ...result, nodes: [...result.nodes, institution] })
  await store.enterNode(result.nodes[0]!)
  expect(store.visible?.nodes).toHaveLength(3)
  store.select('node', institution.id)
  await store.refresh()
  expect(store.selection).toBeNull()
  store.select('edge', 'ab')
  load.mockResolvedValueOnce({ ...result, edges: [] })
  await store.refresh()
  expect(store.selection).toBeNull()
})
