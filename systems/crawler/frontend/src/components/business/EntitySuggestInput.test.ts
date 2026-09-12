import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, expect, it, vi } from 'vitest'
import EntitySuggestInput from './EntitySuggestInput.vue'
import { catalogApi } from '@/services/business'
import type { CatalogEntity, PageResponse } from '@/types/api'

vi.mock('@/services/business', () => ({ catalogApi: { entities: vi.fn() } }))
afterEach(() => { vi.useRealTimers(); vi.clearAllMocks() })

it('输入变化立即作废旧候选，卸载取消尚未发起的防抖查询', async () => {
  vi.useFakeTimers()
  let release!: (value: PageResponse<CatalogEntity>) => void
  vi.mocked(catalogApi.entities).mockReturnValueOnce(new Promise(resolve => { release = resolve }))
  const wrapper = mount(EntitySuggestInput, { props: { collection: 'authors', label: '作者', modelValue: '' } })
  await wrapper.get('input').setValue('旧名称')
  await vi.advanceTimersByTimeAsync(250)
  await wrapper.get('input').setValue('新名称')
  release({ items: [{ id: 1, displayName: '旧候选', externalId: null, entityType: 'AUTHOR', achievementCount: 1, advisors: [] }], page: 0, size: 8, totalElements: 1, totalPages: 1 })
  await flushPromises()
  expect(wrapper.text()).not.toContain('旧候选')
  wrapper.unmount()
  await vi.advanceTimersByTimeAsync(300)
  expect(catalogApi.entities).toHaveBeenCalledTimes(1)
})
