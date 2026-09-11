import { mount } from '@vue/test-utils'
import { expect, it } from 'vitest'
import NodeDetail from './NodeDetail.vue'

it('详情展示完整长名称和扩展零值，不执行扩展字段或名称中的 HTML', () => {
  const name = '不会在详情或后续编辑中被截断的完整学术作品名称'
  const wrapper = mount(NodeDetail, { props: {
    node: { id: 'a', businessId: '1', type: 'AUTHOR', label: name, properties: { count: 0, safe: '<img src=x onerror=alert(1)>', missing: null } },
    loading: false,
  }, global: { stubs: { RouterLink: true } } })
  expect(wrapper.text()).toContain(name)
  expect(wrapper.text()).toContain('0')
  expect(wrapper.text()).toContain('--')
  expect(wrapper.find('img').exists()).toBe(false)
  expect(wrapper.findAll('button').filter(button => button.attributes('disabled') !== undefined)).toHaveLength(2)
  wrapper.unmount()
})
