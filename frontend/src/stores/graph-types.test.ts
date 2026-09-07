import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, expect, it, vi } from 'vitest'
import { graphTypesApi } from '@/services/graph-types'
import { ApiError } from '@/services/api'
import { useGraphTypesStore } from '@/stores/graph-types'
import { useSessionStore } from '@/stores/session'
import type { GraphTypeDefinition } from '@/types/api'

vi.mock('@/services/graph-types', () => ({ graphTypesApi: { list: vi.fn(), update: vi.fn() } }))
const definition: GraphTypeDefinition = { kind: 'NODE', code: 'AUTHOR', displayName: '作者', color: '#258ca3', size: 30, reviewStatus: 'PENDING', version: 0 }
beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

it('账号切换和页面卸载使迟到的类型响应失效', async () => {
  let release!: (value: GraphTypeDefinition[]) => void
  vi.mocked(graphTypesApi.list).mockImplementation(() => new Promise(resolve => { release = resolve }))
  const store = useGraphTypesStore()
  const pending = store.load()
  useSessionStore().user = { id: 2, username: 'reader', permissions: [], roles: ['RESEARCHER'] }
  release([definition])
  await pending
  expect(store.definitions).toEqual([])
  expect(store.loading).toBe(false)
})

it('冲突不覆盖原始配置，成功保存采用服务端的新版本', async () => {
  const store = useGraphTypesStore()
  vi.mocked(graphTypesApi.list).mockResolvedValue([definition])
  await store.load()
  vi.mocked(graphTypesApi.update).mockRejectedValue(new ApiError('版本冲突', 409))
  expect(await store.save(definition)).toBe(false)
  expect(store.definitions[0]?.version).toBe(0)
  expect(store.error).toBe('数据已被其他操作更新，请刷新后重试')
  vi.mocked(graphTypesApi.update).mockResolvedValue({ ...definition, version: 1, size: 52 })
  expect(await store.save({ ...definition, size: 52 })).toBe(true)
  expect(store.definitions[0]?.version).toBe(1)
  expect(store.definitions[0]?.size).toBe(52)
})
