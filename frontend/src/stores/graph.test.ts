import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, expect, it } from 'vitest'
import { useGraphStore } from '@/stores/graph'
import { useSessionStore } from '@/stores/session'
import type { GraphResponse } from '@/types/api'

const result: GraphResponse = {
  nodes: [{ id: 'AUTHOR:1', businessId: '1', type: 'AUTHOR', label: '林研究员', properties: {} }],
  edges: [], rootNodeId: 'AUTHOR:1', truncated: false, narrowingSuggestion: null,
  appliedLimits: { depth: 1, nodeLimit: 100, maxHops: 6 },
  syncedAt: null, projectionLagSeconds: null, traceId: 'graph-test',
}
beforeEach(() => setActivePinia(createPinia()))

it('页面离开后迟到图结果不得恢复，账号切换立即清除结果', async () => {
  const graph = useGraphStore()
  let release!: (value: GraphResponse) => void
  const pending = graph.load(() => new Promise((resolve) => { release = resolve }), false)
  graph.reset()
  release(result)
  await pending
  expect(graph.graph).toBeNull()
  await graph.load(async () => result, false)
  expect(graph.graph?.nodes).toHaveLength(1)
  useSessionStore().user = { id: 8, username: 'new-user', roles: ['RESEARCHER'], permissions: [] }
  expect(graph.graph).toBeNull()
  expect(graph.focus).toBeNull()
})

it('较早查询不能覆盖较新查询的图与加载状态', async () => {
  const graph = useGraphStore()
  let release!: (value: GraphResponse) => void
  const old = graph.load(() => new Promise((resolve) => { release = resolve }), false)
  await graph.load(async () => result, false)
  release({ ...result, nodes: [], rootNodeId: '' })
  await old
  expect(graph.graph?.nodes).toHaveLength(1)
  expect(graph.loading).toBe(false)
})
