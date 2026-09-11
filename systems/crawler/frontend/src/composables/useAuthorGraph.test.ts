import { flushPromises } from '@vue/test-utils'
import { effectScope, nextTick, ref } from 'vue'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { useAuthorGraph } from '@/composables/useAuthorGraph'
import { ApiError } from '@/services/api'
import { academicGraphApi, type AuthorGraphQuery } from '@/services/academic-graph'

vi.mock('@/services/academic-graph', () => ({ academicGraphApi: { load: vi.fn() } }))
const load = vi.mocked(academicGraphApi.load)
let scope = effectScope()
const request: AuthorGraphQuery = { authorId: 1, collaborationsOnly: false, chronological: false, page: 0, size: 20 }
const response = (id = 1) => ({ graph: {
  nodes: [{ id: `AUTHOR:${id}`, businessId: String(id), type: 'AUTHOR', label: `作者${id}`, properties: {} }],
  edges: [], rootNodeId: `AUTHOR:${id}`, truncated: false, appliedLimits: { depth: 1, nodeLimit: 300, maxHops: 0 },
}, page: 0, size: 20, totalWorks: 0 })
beforeEach(() => { scope = effectScope(); load.mockReset(); load.mockResolvedValue(response()) })
afterEach(() => scope.stop())

it('未选作者时不请求；快速切换会取消旧请求并丢弃迟到响应', async () => {
  const query = ref<AuthorGraphQuery | null>(null)
  const state = scope.run(() => useAuthorGraph(() => query.value))!
  expect(load).not.toHaveBeenCalled()
  let resolve!: (value: unknown) => void
  load.mockImplementationOnce(() => new Promise(done => { resolve = done }))
  query.value = request
  await nextTick()
  const firstSignal = load.mock.calls[0]![1]
  load.mockResolvedValueOnce(response(2))
  query.value = { ...request, authorId: 2 }
  await flushPromises()
  expect(firstSignal.aborted).toBe(true)
  resolve(response())
  await flushPromises()
  expect(state.result.value?.graph.rootNodeId).toBe('AUTHOR:2')
  expect(state.loading.value).toBe(false)
})

it('换作者失败清除旧数据并显示错误，重试成功后恢复；卸载取消请求', async () => {
  const query = ref(request)
  const state = scope.run(() => useAuthorGraph(() => query.value))!
  await flushPromises()
  expect(state.result.value?.graph.rootNodeId).toBe('AUTHOR:1')
  load.mockRejectedValueOnce(new ApiError('图谱暂不可用', 503))
  query.value = { ...request, authorId: 2 }
  await flushPromises()
  expect(state.result.value).toBeNull()
  expect(state.error.value).toContain('图谱暂不可用')
  load.mockResolvedValueOnce(response(2))
  await state.load()
  expect(state.error.value).toBe('')
  expect(state.result.value?.graph.rootNodeId).toBe('AUTHOR:2')
  scope.stop()
  expect(load.mock.lastCall![1].aborted).toBe(true)
})
