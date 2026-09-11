import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import cytoscape, { type Core } from 'cytoscape'
import GraphCanvas from './GraphCanvas.vue'

vi.mock('cytoscape', async importOriginal => {
  const original = await importOriginal<{ default: typeof cytoscape }>()
  return { default: vi.fn(() => original.default({ headless: true })) }
})
vi.mock('@/composables/useTheme', () => ({ useTheme: () => ({ isDark: ref(true) }) }))
vi.mock('@/composables/useMotion', () => ({ useMotion: () => ({ reducedMotion: ref(true), duration: () => 0 }) }))
const disconnect = vi.fn()
let cy: Core
const elements = [{ data: { id: 'a', label: '作者', nodeType: 'AUTHOR' } }, { data: { id: 'b', label: '作品', nodeType: 'ACHIEVEMENT' } }, { data: { id: 'ab', source: 'a', target: 'b' } }]
beforeEach(() => {
  vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(800)
  vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(600)
  vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() { disconnect() } })
  disconnect.mockClear()
})
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })

it('局部筛选保留拖拽坐标，清空再恢复也不重新布局', async () => {
  const wrapper = mount(GraphCanvas, { props: { elements, label: '图', scopeKey: 'all' } })
  cy = vi.mocked(cytoscape).mock.results.at(-1)!.value as Core
  cy.getElementById('a').position({ x: 222, y: 333 })
  await wrapper.setProps({ elements: [] })
  await wrapper.setProps({ elements })
  expect(cy.getElementById('a').position()).toEqual({ x: 222, y: 333 })
  await wrapper.setProps({ scopeKey: 'subgraph', positions: { a: { x: 0, y: 0 }, b: { x: 300, y: 0 } } })
  expect(cy.getElementById('a').position()).toEqual({ x: 0, y: 0 })
  wrapper.unmount()
  expect(cy.destroyed()).toBe(true)
  expect(disconnect).toHaveBeenCalledOnce()
})

it('概览单击和双击独立，离开页面释放画布', async () => {
  const wrapper = mount(GraphCanvas, { props: { elements, label: '图', drilldown: true } })
  cy = vi.mocked(cytoscape).mock.results.at(-1)!.value as Core
  const node = cy.getElementById('a')
  node.emit('tap')
  expect(wrapper.emitted('select-node')).toBeUndefined()
  node.emit('dbltap')
  expect(wrapper.emitted('double-click-node')).toEqual([['a']])
  node.emit('onetap')
  expect(wrapper.emitted('select-node')).toEqual([['a']])
  await wrapper.trigger('keydown', { key: 'Escape' })
  expect(wrapper.emitted('clear-selection')).toHaveLength(1)
  await flushPromises()
  wrapper.unmount()
  expect(cy.destroyed()).toBe(true)
})

it('高级查询更换全部节点时重新布局，不把新节点堆在原点', async () => {
  const wrapper = mount(GraphCanvas, { props: { elements, label: '图' } })
  cy = vi.mocked(cytoscape).mock.results.at(-1)!.value as Core
  await wrapper.setProps({ elements: [{ data: { id: 'c', label: '新作者' } }, { data: { id: 'd', label: '新作品' } }] })
  expect(cy.getElementById('c').position()).not.toEqual(cy.getElementById('d').position())
  wrapper.unmount()
})
