import { mount } from '@vue/test-utils'
import { defineComponent, h, KeepAlive, nextTick, ref } from 'vue'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import type { DataSet } from 'vis-data'
import type { Edge, Node, Options } from 'vis-network/peer'

import GraphCanvas from './GraphCanvas.vue'
import type { CanvasGraph } from '@/utils/graph-vis'

interface TestNetwork {
  data: { nodes: DataSet<Node>; edges: DataSet<Edge> }
  options: Options
  handlers: Map<string, (value?: unknown) => void>
  on: ReturnType<typeof vi.fn>
  off: ReturnType<typeof vi.fn>
  destroy: ReturnType<typeof vi.fn>
  fit: ReturnType<typeof vi.fn>
  redraw: ReturnType<typeof vi.fn>
  setOptions: ReturnType<typeof vi.fn>
  setSelection: ReturnType<typeof vi.fn>
  stopSimulation: ReturnType<typeof vi.fn>
  disableEditMode: ReturnType<typeof vi.fn>
  addEdgeMode: ReturnType<typeof vi.fn>
  getNodeAt: ReturnType<typeof vi.fn>
  getEdgeAt: ReturnType<typeof vi.fn>
}
const mocks = vi.hoisted(() => ({ instances: [] as TestNetwork[] }))
vi.mock('vis-network/peer', () => ({ Network: class {
  constructor(_container: HTMLElement, data: TestNetwork['data'], options: Options) {
    const handlers = new Map()
    const instance = { data, options, handlers,
      on: vi.fn((name, handler) => handlers.set(name, handler)),
      off: vi.fn((name) => handlers.delete(name)),
      destroy: vi.fn(), fit: vi.fn(), redraw: vi.fn(), setOptions: vi.fn(), setSelection: vi.fn(),
      stopSimulation: vi.fn(), disableEditMode: vi.fn(), addEdgeMode: vi.fn(), getNodeAt: vi.fn(), getEdgeAt: vi.fn(),
      getPositions: vi.fn(() => ({ a: { x: 42, y: 80 } })), stabilize: vi.fn(),
      getViewPosition: vi.fn(() => ({ x: 0, y: 0 })), getScale: vi.fn(() => 1), moveTo: vi.fn(),
    }
    mocks.instances.push(instance)
    return instance
  }
} }))
vi.mock('@/composables/useTheme', () => ({ useTheme: () => ({ isDark: ref(false) }) }))
vi.mock('@/composables/useMotion', () => ({ useMotion: () => ({ reducedMotion: ref(false), duration: () => 280 }) }))
const data: CanvasGraph = {
  nodes: ['a', 'b'].map(id => ({ id, name: '完整名称', label: '完整名称', title: '<img src=x onerror=alert(1)>', type: 'AUTHOR',
    typeName: '作者', color: '#123456', widthConstraint: 50, heightConstraint: 50, extend: {} })),
  edges: [],
}
const edge: CanvasGraph['edges'][number] = { id: 'persisted-edge', from: 'a', to: 'b', label: '创作', title: '创作', relation: 'AUTHORED', color: '#7690a8', width: 2, directed: true }
const disconnect = vi.fn()
let resize!: () => void
let frame!: FrameRequestCallback
beforeEach(() => {
  mocks.instances.length = 0
  vi.useFakeTimers()
  vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(640)
  vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(520)
  vi.stubGlobal('ResizeObserver', class { constructor(callback: () => void) { resize = callback }; observe() {}; disconnect = disconnect })
  vi.stubGlobal('requestAnimationFrame', vi.fn((callback: FrameRequestCallback) => { frame = callback; return 1 }))
  vi.stubGlobal('cancelAnimationFrame', vi.fn())
})
afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); vi.unstubAllGlobals() })

it('一次挂载只创建一个实例，更新和切换范围复用 DataSet 并保存位置', async () => {
  const wrapper = mount(GraphCanvas, { props: { data, label: '图谱', scopeKey: 'all' } })
  const instance = mocks.instances[0]!
  const rendered = instance.data.nodes
  expect((rendered.get('a')!.title as HTMLElement).textContent).toBe(data.nodes[0]!.title)
  expect((rendered.get('a')!.title as HTMLElement).querySelector('img')).toBeNull()
  await wrapper.setProps({ data: { ...data, nodes: data.nodes.map(node => ({ ...node, label: '新名称' })), edges: [edge] } })
  expect(instance.data.nodes).toBe(rendered)
  expect(rendered.get('a')).toMatchObject({ label: '新名称', x: 42, y: 80 })
  expect(instance.data.edges.get(edge.id)).not.toBeNull()
  await wrapper.setProps({ data: { nodes: [data.nodes[0]!], edges: [] }, scopeKey: 'a' })
  expect(mocks.instances).toHaveLength(1)
  expect(instance.data.edges.length).toBe(0)
  expect(rendered.get('b')).toBeNull()
  wrapper.unmount()
  expect(instance.destroy).toHaveBeenCalledTimes(1)
  expect(instance.handlers.size).toBe(0)
})

it('300节点首次分散铺开、范围切换重新展开，合作专用坐标优先', async () => {
  const dense = { nodes: Array.from({ length: 300 }, (_, index) => ({ ...data.nodes[0]!, id: `node-${index}` })), edges: [] }
  const wrapper = mount(GraphCanvas, { props: { data: dense, label: '密集图谱', scopeKey: 'all' } })
  const instance = mocks.instances[0]!
  const positions = instance.data.nodes.get().map(node => ({ x: node.x!, y: node.y! }))
  expect(new Set(positions.map(point => `${point.x}:${point.y}`)).size).toBe(300)
  for (let i = 0; i < positions.length; i++) for (let j = i + 1; j < positions.length; j++) {
    expect(Math.hypot(positions[i]!.x - positions[j]!.x, positions[i]!.y - positions[j]!.y)).toBeGreaterThanOrEqual(180)
  }
  expect(Math.max(...positions.map(point => point.x)) - Math.min(...positions.map(point => point.x))).toBeGreaterThan(2000)
  await wrapper.setProps({ data, scopeKey: 'subgraph', positions: { a: { x: 10, y: 20 }, b: { x: 30, y: 40 } } })
  expect(instance.data.nodes.get('a')).toMatchObject({ x: 10, y: 20 })
  await wrapper.setProps({ scopeKey: 'all', positions: undefined })
  expect(instance.data.nodes.get('a')).not.toMatchObject({ x: 42, y: 80 })
  expect(mocks.instances).toHaveLength(1)
  wrapper.unmount()
})

it('新增的持久化关系立即可选，双击不触发单击详情，右键返回命中项', async () => {
  const wrapper = mount(GraphCanvas, { props: { data, label: '图谱', scopeKey: 'all' } })
  const instance = mocks.instances[0]!
  await wrapper.setProps({ data: { ...data, edges: [edge] } })
  instance.handlers.get('click')!({ nodes: [], edges: [edge.id] })
  await vi.advanceTimersByTimeAsync(250)
  expect(wrapper.emitted('select-edge')).toEqual([[edge.id]])
  instance.handlers.get('click')!({ nodes: ['a'], edges: [] })
  instance.handlers.get('doubleClick')!({ nodes: ['a'], edges: [] })
  await vi.advanceTimersByTimeAsync(300)
  expect(wrapper.emitted('select-node')).toBeUndefined()
  expect(wrapper.emitted('double-click-node')).toEqual([['a']])
  instance.getNodeAt.mockReturnValue('a')
  const event = { preventDefault: vi.fn() }
  instance.handlers.get('oncontext')!({ event, pointer: { DOM: { x: 20, y: 30 } } })
  expect(event.preventDefault).toHaveBeenCalled()
  expect(wrapper.emitted('context-menu')?.[0]).toEqual([{ kind: 'node', id: 'a', x: 20, y: 30 }])
  instance.getNodeAt.mockReturnValue(undefined)
  instance.getEdgeAt.mockReturnValue(edge.id)
  instance.handlers.get('oncontext')!({ event, pointer: { DOM: { x: 50, y: 60 } } })
  expect(wrapper.emitted('context-menu')?.[1]).toEqual([{ kind: 'edge', id: edge.id, x: 50, y: 60 }])
  await wrapper.setProps({ data })
  expect(instance.data.edges.length).toBe(0)
  wrapper.unmount()
})

it('建边默认关闭；取消、换图及卸载均结束临时回调且不会写入渲染数据', async () => {
  const wrapper = mount(GraphCanvas, { props: { data, label: '图谱', scopeKey: 'all' } })
  const instance = mocks.instances[0]!
  wrapper.vm.startRelation()
  expect(instance.addEdgeMode).not.toHaveBeenCalled()
  await wrapper.setProps({ allowCreateRelation: true })
  const addEdge = instance.options.manipulation!.addEdge as (edge: Edge, callback: (value: Edge | null) => void) => void
  for (const operation of ['cancel', 'scope', 'unmount']) {
    const callback = vi.fn()
    wrapper.vm.startRelation()
    addEdge({ from: 'a', to: 'b' }, callback)
    expect(wrapper.emitted('create-edge')?.at(-1)).toEqual([{ from: 'a', to: 'b' }])
    expect(instance.data.edges.length).toBe(0)
    if (operation === 'cancel') wrapper.vm.finishRelation()
    else if (operation === 'scope') await wrapper.setProps({ scopeKey: 'a' })
    else wrapper.unmount()
    expect(callback).toHaveBeenCalledExactlyOnceWith(null)
  }
})

it('卸载清理尺寸观察器、排队帧及单击任务，迟到回调不得恢复实例', async () => {
  const wrapper = mount(GraphCanvas, { props: { data, label: '图谱', scopeKey: 'all' } })
  const instance = mocks.instances[0]!
  resize()
  instance.handlers.get('click')!({ nodes: ['a'], edges: [] })
  wrapper.unmount()
  frame(1)
  await vi.advanceTimersByTimeAsync(300)
  expect(instance.redraw).not.toHaveBeenCalled()
  expect(wrapper.emitted('select-node')).toBeUndefined()
  expect(disconnect).toHaveBeenCalled()
  expect(cancelAnimationFrame).toHaveBeenCalledWith(1)
  expect(mocks.instances).toHaveLength(1)
})

it('KeepAlive 停用暂停资源，激活恢复尺寸且不重复创建实例', async () => {
  const shown = ref(true)
  const wrapper = mount(defineComponent({ setup: () => () => h(KeepAlive, null, {
    default: () => shown.value ? h(GraphCanvas, { data, label: '图谱', scopeKey: 'all' }) : null,
  }) }))
  shown.value = false
  await nextTick()
  expect(mocks.instances[0]!.stopSimulation).toHaveBeenCalled()
  shown.value = true
  await nextTick()
  frame(1)
  expect(mocks.instances[0]!.redraw).toHaveBeenCalled()
  expect(mocks.instances).toHaveLength(1)
  wrapper.unmount()
})
