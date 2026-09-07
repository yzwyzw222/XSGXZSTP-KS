import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import GraphSavedQueries from '@/components/business/GraphSavedQueries.vue'
import { useSessionStore } from '@/stores/session'
import type { GraphFilters } from '@/utils/graph-query'

const filters: GraphFilters = {
  centerType: 'AUTHOR', centerId: '42', depth: '1', nodeLimit: '100',
  publicationYearFrom: '', publicationYearTo: '', nodeTypes: [], relationshipTypes: [], achievementTypes: '',
}

function signIn(userId: number): void {
  const store = useSessionStore()
  store.user = {
    id: userId, username: 'researcher', roles: ['RESEARCHER'], permissions: ['GRAPH_READ'],
  }
  store.status = 'authenticated'
}

describe('常用图谱查询', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    signIn(1)
  })
  afterEach(() => { vi.restoreAllMocks() })

  it('保存后重新挂载可恢复完整筛选，其他账号不可读取', async () => {
    const first = mount(GraphSavedQueries, { props: { filters } })
    await first.get('input').setValue('作者合作')
    await first.get('button').trigger('click')
    first.unmount()

    const second = mount(GraphSavedQueries, { props: { filters } })
    await second.get('li button').trigger('click')
    expect(second.emitted('restore')).toEqual([[filters]])
    second.unmount()

    // 切换到另一个账号：本机存储按账号隔离，不能读到上一个账号的查询。
    signIn(2)
    const other = mount(GraphSavedQueries, { props: { filters } })
    expect(other.findAll('li')).toHaveLength(0)
    other.unmount()
  })

  it('未登录时不能保存，避免把检索习惯写入匿名存储键', async () => {
    const store = useSessionStore()
    store.user = null
    store.status = 'anonymous'

    const wrapper = mount(GraphSavedQueries, { props: { filters } })
    await wrapper.get('input').setValue('匿名查询')
    await wrapper.get('button').trigger('click')
    expect(wrapper.findAll('li')).toHaveLength(0)
    expect(Object.keys(localStorage)).toHaveLength(0)
    wrapper.unmount()
  })

  it('无效存储和写入失败有明确提示，非法中心不能保存', async () => {
    localStorage.setItem('aacv-graph-queries-v1:1', '{invalid')
    const wrapper = mount(GraphSavedQueries, { props: { filters: { ...filters, centerId: '-1' } } })
    expect(wrapper.text()).toContain('无法读取')
    await wrapper.get('input').setValue('错误查询')
    await wrapper.get('button').trigger('click')
    expect(wrapper.text()).toContain('确认中心节点')
    await wrapper.setProps({ filters })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new DOMException('QuotaExceededError') })
    await wrapper.get('button').trigger('click')
    expect(wrapper.text()).toContain('未允许保存')
    expect(wrapper.findAll('li')).toHaveLength(0)
    wrapper.unmount()
  })
})
