import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import UserRoleChart from './UserRoleChart.vue'

describe('账号角色分布', () => {
  it('展示后端的互斥人数及统计口径', () => {
    const wrapper = mount(UserRoleChart, { props: { statistics: { totalUsers: 4, admin: 1, dataOperator: 2, researcher: 1 } }, global: { stubs: { ChartFrame: true } } })
    expect(wrapper.text()).toContain('2 人')
    expect(wrapper.text()).toContain('50.0%')
    expect(wrapper.text()).toContain('每人计一次')
  })
  it('空库不生成 NaN 或虚构比例', () => {
    const wrapper = mount(UserRoleChart, { props: { statistics: { totalUsers: 0, admin: 0, dataOperator: 0, researcher: 0 } }, global: { stubs: { ChartFrame: true } } })
    expect(wrapper.text()).not.toContain('NaN')
    expect(wrapper.text()).not.toContain('%')
    expect(wrapper.findAll('dd')).toHaveLength(3)
  })
})
