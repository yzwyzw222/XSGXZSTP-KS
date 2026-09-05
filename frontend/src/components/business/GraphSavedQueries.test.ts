import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import GraphSavedQueries from '@/components/business/GraphSavedQueries.vue'
import { session } from '@/services/session'
import type { GraphFilters } from '@/utils/graph-query'

const filters: GraphFilters = {
  centerType: 'AUTHOR', centerId: '42', depth: '1', nodeLimit: '100',
  publicationYearFrom: '', publicationYearTo: '', nodeTypes: [], relationshipTypes: [], achievementTypes: '',
}

describe('常用图谱查询', () => {
  beforeEach(() => {
    localStorage.clear()
    session.user = { id: 1, username: 'researcher', roles: ['RESEARCHER'], permissions: ['GRAPH_READ'] }
  })
  afterEach(() => { vi.restoreAllMocks(); session.user = null })

  it('保存后重新挂载可恢复完整筛选，其他账号不可读取', async () => {
    const first = mount(GraphSavedQueries, { props: { filters } })
    await first.get('input').setValue('作者合作')
    await first.get('button').trigger('click')
    first.unmount()
    const second = mount(GraphSavedQueries, { props: { filters } })
    await second.get('li button').trigger('click')
    expect(second.emitted('restore')).toEqual([[filters]])
    second.unmount()
    session.user!.id = 2
    const other = mount(GraphSavedQueries, { props: { filters } })
    expect(other.findAll('li')).toHaveLength(0)
    other.unmount()
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
