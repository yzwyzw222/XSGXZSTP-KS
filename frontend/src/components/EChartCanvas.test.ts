import { mount } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import EChartCanvas from '@/components/EChartCanvas.vue'

const chart = vi.hoisted(() => ({ setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn() }))
const init = vi.hoisted(() => vi.fn(() => chart))
vi.mock('echarts/core', () => ({ use: vi.fn(), init }))
const reduced = ref(false)
const dark = ref(false)
vi.mock('@/composables/useTheme', () => ({ useTheme: () => ({ isDark: dark }) }))
vi.mock('@/composables/useMotion', () => ({ useMotion: () => ({ reducedMotion: reduced, duration: () => reduced.value ? 0 : 320 }) }))
let notifyResize: () => void
let frame: FrameRequestCallback
const disconnect = vi.fn()
const cancelFrame = vi.fn()

beforeEach(() => {
  reduced.value = false
  dark.value = false
  vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(640)
  vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(300)
  vi.stubGlobal('ResizeObserver', class {
    constructor(callback: () => void) { notifyResize = callback }
    observe() {}
    disconnect = disconnect
  })
  vi.stubGlobal('requestAnimationFrame', vi.fn((callback: FrameRequestCallback) => { frame = callback; return 1 }))
  vi.stubGlobal('cancelAnimationFrame', cancelFrame)
})
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })

it('更新与移除系列时复用画布和系列对象，避免整图清空', async () => {
  const wrapper = mount(EChartCanvas, { props: { label: '趋势', option: { series: [{ id: 'years', type: 'line', data: [12] }] } } })
  await wrapper.setProps({ option: { series: [{ id: 'years', type: 'line', data: [18] }] } })
  expect(init).toHaveBeenCalledTimes(1)
  expect(chart.setOption).toHaveBeenLastCalledWith(expect.objectContaining({ animationDurationUpdate: 320 }), { notMerge: false, replaceMerge: ['series'], lazyUpdate: false })
  await wrapper.setProps({ option: { series: [] } })
  expect(chart.setOption.mock.lastCall?.[0].series).toEqual([])
  wrapper.unmount()
})

it('运行时切换减少动画会立即改为零时长', async () => {
  const wrapper = mount(EChartCanvas, { props: { label: '趋势', option: { series: [] } } })
  reduced.value = true
  await nextTick()
  expect(chart.setOption).toHaveBeenLastCalledWith(expect.objectContaining({ animation: false, animationDuration: 0, animationDurationUpdate: 0 }), expect.anything())
  wrapper.unmount()
})

it('卸载清理观察器与排队帧，迟到的尺寸回调不重建图表', () => {
  const wrapper = mount(EChartCanvas, { props: { label: '趋势', option: { series: [] } } })
  notifyResize()
  wrapper.unmount()
  expect(disconnect).toHaveBeenCalledTimes(1)
  expect(cancelFrame).toHaveBeenCalledWith(1)
  expect(chart.dispose).toHaveBeenCalledTimes(1)
  frame(10)
  expect(init).toHaveBeenCalledTimes(1)
  expect(chart.resize).not.toHaveBeenCalled()
})
