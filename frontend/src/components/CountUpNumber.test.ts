import { mount } from '@vue/test-utils'
import { expect, it, vi } from 'vitest'
import CountUpNumber from '@/components/CountUpNumber.vue'

it('指标首屏与连续更新直接显示真实值，不安排插值动画', async () => {
  const frame = vi.spyOn(window, 'requestAnimationFrame')
  const wrapper = mount(CountUpNumber, { props: { value: 1286 } })
  expect(wrapper.text()).toBe('1,286')
  await wrapper.setProps({ value: 72 })
  expect(wrapper.text()).toBe('72')
  await wrapper.setProps({ value: 0 })
  expect(wrapper.text()).toBe('0')
  expect(frame).not.toHaveBeenCalled()
  wrapper.unmount()
  frame.mockRestore()
})

it('无效数字不显示成零，保留后缀兼容性', async () => {
  const wrapper = mount(CountUpNumber, { props: { value: NaN } })
  expect(wrapper.text()).toBe('--')
  await wrapper.setProps({ value: 28, suffix: ' 项' })
  expect(wrapper.text()).toBe('28 项')
  wrapper.unmount()
})
