import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SourceEntitySelect from './SourceEntitySelect.vue'
import { sourceApi } from '@/services/business'
import { ApiError } from '@/services/api'
import type { SourceEntity } from '@/types/api'

vi.mock('@/services/business', () => ({ sourceApi: { entities: vi.fn(), resolveEntities: vi.fn() } }))
const author = (id: string, hint = '大学甲'): SourceEntity => ({ id, displayName: '张三', hint, worksCount: 20 })
let wrapper: VueWrapper<InstanceType<typeof SourceEntitySelect>> | undefined
beforeEach(() => {
  vi.useFakeTimers()
  vi.mocked(sourceApi.entities).mockResolvedValue([author('A1'), author('A2', '大学乙')])
  vi.mocked(sourceApi.resolveEntities).mockResolvedValue([])
})
afterEach(() => { wrapper?.unmount(); wrapper = undefined; vi.useRealTimers(); vi.clearAllMocks() })

async function open(ids: string[] = []) {
  wrapper = mount(SourceEntitySelect, { props: { id: 'authors', sourceId: 1, kind: 'authors', modelValue: ids,
    'onUpdate:modelValue': (value: string[]) => { void wrapper?.setProps({ modelValue: value }) },
  } })
  await flushPromises()
  return wrapper
}
async function search(text: string) {
  await wrapper!.get('input').setValue(text)
  await vi.advanceTimersByTimeAsync(300)
  await flushPromises()
}

describe('来源作者、机构名称选择', () => {
  it('同名候选以机构区分，多选提交对应标识且不重复加入', async () => {
    const view = await open()
    expect(sourceApi.entities).not.toHaveBeenCalled()
    await search(' 张三 ')
    expect(view.emitted('ready')?.at(-1)).toEqual([false])
    expect(sourceApi.entities).toHaveBeenCalledWith(1, 'authors', '张三', expect.any(AbortSignal))
    expect(view.text()).toContain('大学乙')
    await view.findAll('li button')[1]!.trigger('click'); await flushPromises()
    expect(view.props('modelValue')).toEqual(['A2'])
    expect(view.text()).not.toContain('A2')
    expect(view.emitted('ready')?.at(-1)).toEqual([true])
    await search('张三')
    expect(view.findAll('li button')[1]!.attributes('disabled')).toBeDefined()
    await view.findAll('li button')[0]!.trigger('click'); await flushPromises()
    expect(view.props('modelValue')).toEqual(['A2', 'A1'])
  })

  it('按名称回显旧的完整URL标识，保留原值且支持移除', async () => {
    vi.mocked(sourceApi.resolveEntities).mockResolvedValue([author('A1')])
    const view = await open(['https://openalex.org/A1'])
    expect(sourceApi.resolveEntities).toHaveBeenCalledWith(1, 'authors', ['A1'], expect.any(AbortSignal))
    expect(view.text()).toContain('张三')
    expect(view.text()).not.toContain('https://openalex.org/A1')
    expect(view.props('modelValue')).toEqual(['https://openalex.org/A1'])
    expect(view.emitted('ready')?.at(-1)).toEqual([true])
    await view.get('.el-tag__close').trigger('click'); await flushPromises()
    expect(view.props('modelValue')).toEqual([])
  })

  it('回显部分缺失时不丢失条件，重试成功后恢复可提交状态', async () => {
    const view = await open(['A1'])
    expect(view.props('modelValue')).toEqual(['A1'])
    expect(view.text()).toContain('原筛选条件已保留')
    expect(view.emitted('ready')?.at(-1)).toEqual([false])
    vi.mocked(sourceApi.resolveEntities).mockResolvedValue([author('A1')])
    await view.findAll('button').find(item => item.text().includes('重试读取名称'))!.trigger('click')
    await flushPromises()
    expect(view.text()).toContain('张三')
    expect(view.emitted('ready')?.at(-1)).toEqual([true])
  })

  it('快速改词和卸载会取消查询，迟到响应不能覆盖新搜索', async () => {
    let release!: (value: SourceEntity[]) => void
    vi.mocked(sourceApi.entities).mockReturnValueOnce(new Promise(resolve => { release = resolve }))
    const view = await open()
    await search('旧作者')
    const signal = vi.mocked(sourceApi.entities).mock.calls[0]![3]!
    await view.get('input').setValue('新作者')
    expect(signal.aborted).toBe(true)
    release([{ ...author('A8'), displayName: '过期作者' }]); await flushPromises()
    expect(view.text()).not.toContain('过期作者')
    view.unmount(); wrapper = undefined
    await vi.advanceTimersByTimeAsync(300)
    expect(sourceApi.entities).toHaveBeenCalledTimes(1)
  })

  it('切换来源时不会把旧的名称回显写入新来源', async () => {
    let release!: (value: SourceEntity[]) => void
    vi.mocked(sourceApi.resolveEntities).mockReturnValueOnce(new Promise(resolve => { release = resolve }))
    const view = await open(['A1'])
    const signal = vi.mocked(sourceApi.resolveEntities).mock.calls[0]![3]!
    await view.setProps({ sourceId: 2, modelValue: [] }); await flushPromises()
    release([{ ...author('A1'), displayName: '旧来源作者' }]); await flushPromises()
    expect(signal.aborted).toBe(true)
    expect(view.text()).not.toContain('旧来源作者')
    expect(view.emitted('ready')?.at(-1)).toEqual([true])
  })

  it('搜索无结果和网络失败有明确提示，保留输入直到选择或清空', async () => {
    vi.mocked(sourceApi.entities).mockResolvedValueOnce([])
    const view = await open()
    await search('未收录')
    expect(view.text()).toContain('未找到匹配名称')
    expect(view.emitted('ready')?.at(-1)).toEqual([false])
    vi.mocked(sourceApi.entities).mockRejectedValueOnce(new ApiError('名称查询超时', 503))
    await search('失败查询')
    expect(view.text()).toContain('名称查询超时')
    expect(view.text()).toContain('重试搜索')
    await view.get('input').setValue('')
    expect(view.emitted('ready')?.at(-1)).toEqual([true])
  })

  it('异常空响应不会让界面崩溃或放行未完成的名称选择', async () => {
    vi.mocked(sourceApi.entities).mockResolvedValueOnce(undefined as unknown as SourceEntity[])
    const view = await open()
    await search('作者')
    expect(view.text()).toContain('名称响应格式无效')
    expect(view.emitted('ready')?.at(-1)).toEqual([false])
  })
})
