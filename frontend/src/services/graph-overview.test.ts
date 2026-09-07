import { expect, it, vi } from 'vitest'
import { api } from '@/services/api'
import { graphOverviewApi } from '@/services/graph-overview'

vi.mock('@/services/api', () => ({ api: { get: vi.fn().mockResolvedValue({}) } }))

it('概览和子图复用现有接口、取消信号、两跳与300节点限制', async () => {
  const signal = new AbortController().signal
  await graphOverviewApi.load(undefined, signal)
  expect(api.get).toHaveBeenLastCalledWith('/api/v1/graph/overview', { signal })
  await graphOverviewApi.load({ id: 'AUTHOR:1', type: 'AUTHOR', businessId: '1', name: '作者' }, signal)
  const [path, options] = vi.mocked(api.get).mock.lastCall!
  const url = new URL(path, 'http://localhost')
  expect(url.pathname).toBe('/api/v1/graph/subgraph')
  expect(Object.fromEntries(url.searchParams)).toEqual({ centerType: 'AUTHOR', centerId: '1', depth: '2', nodeLimit: '300', includeCoauthors: 'true' })
  expect(options).toEqual({ signal })
})
