import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia, type Pinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import GraphEntityPicker from '@/components/business/GraphEntityPicker.vue'
import { findGraphEntities } from '@/services/graph-lookup'
import { useSessionStore } from '@/stores/session'
import type { Permission } from '@/types/api'

vi.mock('@/services/graph-lookup', () => ({ findGraphEntities: vi.fn() }))

function signIn(permissions: Permission[]): Pinia {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useSessionStore()
  store.user = { id: 1, username: 'researcher', roles: ['RESEARCHER'], permissions }
  store.status = 'authenticated'
  return pinia
}

function picker(pinia: Pinia, props: Record<string, unknown> = {}) {
  return mount(GraphEntityPicker, {
    props: { type: 'AUTHOR', label: '中心', modelValue: '', ...props },
    global: { plugins: [pinia] },
  })
}

describe('图谱实体检索', () => {
  beforeEach(() => vi.mocked(findGraphEntities).mockReset())

  it('同名结果保留独立ID，只有用户选择后才写入中心', async () => {
    const pinia = signIn(['CATALOG_READ', 'GRAPH_READ'])
    vi.mocked(findGraphEntities).mockResolvedValue([
      { id: 12, label: '张研究员', description: '机构甲' },
      { id: 28, label: '张研究员', description: '机构乙' },
    ])
    const wrapper = picker(pinia)
    await wrapper.get('input:not([type="number"])').setValue('张研究员')
    await wrapper.get('button[aria-label="查找中心"]').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('li')).toHaveLength(2)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    await wrapper.findAll('li button')[1]!.trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([['28']])
    wrapper.unmount()
  })

  it('切换类型后忽略旧请求，网络失败可以再次检索', async () => {
    const pinia = signIn(['CATALOG_READ', 'GRAPH_READ'])
    let finish!: (value: Awaited<ReturnType<typeof findGraphEntities>>) => void
    vi.mocked(findGraphEntities).mockReturnValueOnce(new Promise((resolve) => { finish = resolve }))
    const wrapper = picker(pinia, { modelValue: '7' })
    await wrapper.get('input:not([type="number"])').setValue('研究')
    await wrapper.get('button[aria-label="查找中心"]').trigger('click')
    await wrapper.setProps({ type: 'INSTITUTION' })
    finish([{ id: 9, label: '过期作者', description: '' }])
    await flushPromises()
    expect(wrapper.text()).not.toContain('过期作者')
    expect(wrapper.emitted('update:modelValue')).toEqual([['']])
    vi.mocked(findGraphEntities).mockRejectedValueOnce(new Error('网络不可用'))
    await wrapper.get('button[aria-label="查找中心"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    vi.mocked(findGraphEntities).mockResolvedValueOnce([])
    await wrapper.get('button[aria-label="查找中心"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('没有匹配结果')
    wrapper.unmount()
  })

  it('没有目录读取权限时不提供名称检索入口，只保留业务ID输入', async () => {
    const pinia = signIn(['GRAPH_READ'])
    const wrapper = picker(pinia)

    expect(wrapper.find('input:not([type="number"])').exists()).toBe(false)
    expect(wrapper.find('button[aria-label="查找中心"]').exists()).toBe(false)
    expect(wrapper.find('input[type="number"]').exists()).toBe(true)
    expect(vi.mocked(findGraphEntities)).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('空关键字给出明确提示，不发起检索请求', async () => {
    const pinia = signIn(['CATALOG_READ', 'GRAPH_READ'])
    const wrapper = picker(pinia)

    await wrapper.get('button[aria-label="查找中心"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toContain('请先输入名称或论文标题')
    expect(vi.mocked(findGraphEntities)).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
