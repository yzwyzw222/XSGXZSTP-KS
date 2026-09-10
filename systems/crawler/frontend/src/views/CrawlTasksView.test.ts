import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, type PropType } from 'vue'
import CrawlTasksView from './CrawlTasksView.vue'
import { crawlApi, sourceApi } from '@/services/business'
import type { CrawlRun, CrawlSchedule, CrawlTask, DataSource, PageResponse } from '@/types/api'

vi.mock('@/services/business', () => ({ sourceApi: { page: vi.fn(), entities: vi.fn(), resolveEntities: vi.fn() }, crawlApi: {
  tasks: vi.fn(), task: vi.fn(), createTask: vi.fn(), updateTask: vi.fn(), trigger: vi.fn(),
  getSchedule: vi.fn(), schedule: vi.fn(), deleteSchedule: vi.fn(), runs: vi.fn(),
  run: vi.fn(), window: vi.fn(), failures: vi.fn(), control: vi.fn(),
} }))
vi.mock('@/stores/session', () => ({ useSessionStore: () => ({ hasPermission: () => true }) }))

const page = <T,>(items: T[]): PageResponse<T> => ({ items, page: 0, size: 20, totalElements: items.length, totalPages: 1 })
const task: CrawlTask = { id: 7, sourceId: 29, name: '论文采集', parameters: { maxPages: 5, maxRecords: 500,
  publicationDateFrom: null, publicationDateTo: null, keyword: null, updatedFrom: null, updatedUntil: null,
  authorIds: [], institutionIds: [], dois: [], orcids: [], rorIds: [] }, enabled: true, version: 0,
  parameterVersion: 1, createdAt: '2026-09-10T00:00:00Z', updatedAt: '2026-09-10T00:00:00Z' }
const pending = { id: 8, taskId: 7, runNumber: 'run-8', status: 'PENDING', readCount: 0, failureCount: 0,
  parsedCount: 0, createdCount: 0, updatedCount: 0, duplicateCount: 0, requestCount: 0 } as CrawlRun
const schedule = { id: 4, taskId: 7, localTime: '08:30:00', timeZone: 'Asia/Shanghai',
  version: 6, incrementalMode: 'FIXED_SCOPE_REFRESH', enabled: true, nextFireAt: null } as CrawlSchedule

const stubs = {
  DataTable: defineComponent({ props: { data: { type: Array as PropType<object[]>, default: () => [] } }, setup(props, { slots }) {
    return () => h('div', props.data.map((row) => h('div', [
      ...(slots['cell-sourceName']?.({ row }) ?? []), ...(slots['cell-actions']?.({ row }) ?? []),
    ])))
  } }),
  ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot name="header" :title-id="\'title\'"/><slot/></div>' },
  ElSelect: { props: ['modelValue'], emits: ['update:modelValue'], template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><slot/></select>' },
  ElOption: { props: ['value', 'label'], template: '<option :value="value">{{ label }}</option>' },
  ElTimePicker: { props: ['modelValue'], template: '<input :value="modelValue" />' },
  ElDatePicker: true, ElProgress: true, LiveLogPanel: true, CountUpNumber: true,
}

describe('采集任务操作', () => {
  let wrapper: VueWrapper | undefined
  beforeEach(() => {
    vi.mocked(sourceApi.entities).mockResolvedValue([])
    vi.mocked(sourceApi.resolveEntities).mockResolvedValue([])
    vi.mocked(crawlApi.tasks).mockResolvedValue(page([task]))
    vi.mocked(sourceApi.page).mockResolvedValue(page([
      { id: 29, sourceType: 'CROSSREF', enabled: true }, { id: 31, sourceType: 'OPENALEX', enabled: true },
    ] as DataSource[]))
    vi.mocked(crawlApi.getSchedule).mockResolvedValue(schedule)
    vi.mocked(crawlApi.schedule).mockResolvedValue(schedule)
    vi.mocked(crawlApi.task).mockResolvedValue(task)
    vi.mocked(crawlApi.window).mockResolvedValue(undefined)
    vi.mocked(crawlApi.failures).mockResolvedValue(page([]))
    vi.mocked(crawlApi.trigger).mockResolvedValue(pending)
    vi.mocked(crawlApi.run).mockResolvedValue(pending)
    vi.mocked(crawlApi.control).mockResolvedValue({ ...pending, status: 'CANCELLED' })
    vi.mocked(crawlApi.runs).mockResolvedValue(page([pending]))
  })
  afterEach(() => { wrapper?.unmount(); vi.useRealTimers() })
  async function open() {
    wrapper = mount(CrawlTasksView, { global: { stubs } })
    await flushPromises()
    return wrapper
  }
  async function click(text: string) {
    const button = wrapper!.findAll('button').find((item) => item.text().includes(text))
    expect(button, text).toBeDefined()
    await button!.trigger('click'); await flushPromises()
  }
  it('以名称显示来源并随来源切换可用筛选字段', async () => {
    const view = await open()
    expect(view.text()).toContain('Crossref')
    await click('新建采集任务')
    expect(view.get('select#sourceName').text()).toContain('Crossref')
    expect(view.get('select#sourceName').text()).toContain('OpenAlex')
    expect(view.text()).toContain('ORCID')
    await view.get('select#sourceName').setValue('31')
    expect(view.text()).not.toContain('ORCID')
    expect(view.text()).toContain('作者')
  })
  it('作者机构按名称选择，保存对应标识和原关键词', async () => {
    vi.useFakeTimers()
    vi.mocked(sourceApi.entities).mockImplementation(async (_source, kind) => kind === 'authors'
      ? [{ id: 'A2', displayName: '张三', hint: '某大学', worksCount: 20 }]
      : [{ id: 'I3', displayName: '某大学', hint: '北京', worksCount: 100 }])
    const view = await open(); await click('新建采集任务')
    await view.get('select#sourceName').setValue('31')
    expect(view.text()).not.toContain('作者 ID')
    expect(view.text()).not.toContain('机构 ID')
    expect(view.text()).toContain('标题、摘要及可检索全文')
    await view.get('input#taskName').setValue('名称筛选任务')
    await view.get('input#keyword').setValue('  graph neural networks  ')
    await view.get('input#authorNames').setValue('张三')
    await view.get('form').trigger('submit'); await flushPromises()
    expect(crawlApi.createTask).not.toHaveBeenCalled()
    expect(view.text()).toContain('未选中的搜索文字请先清空')
    await vi.advanceTimersByTimeAsync(300); await flushPromises()
    await view.get('ul[aria-label="作者搜索结果"] button').trigger('click')
    await view.get('input#institutionNames').setValue('大学')
    await vi.advanceTimersByTimeAsync(300); await flushPromises()
    await view.get('ul[aria-label="机构搜索结果"] button').trigger('click')
    await view.get('form').trigger('submit'); await flushPromises()
    expect(crawlApi.createTask).toHaveBeenCalledWith(expect.objectContaining({ sourceId: 31, parameters: expect.objectContaining({
      authorIds: ['A2'], institutionIds: ['I3'], keyword: 'graph neural networks',
    }) }))
  })
  it('旧任务回显失败时保留原筛选且阻止保存', async () => {
    vi.mocked(crawlApi.tasks).mockResolvedValue(page([{ ...task, sourceId: 31, parameters: {
      ...task.parameters, authorIds: ['https://openalex.org/A2'],
    } }]))
    const view = await open(); await click('编辑')
    expect(view.text()).toContain('原筛选条件已保留')
    await view.get('form').trigger('submit'); await flushPromises()
    expect(crawlApi.updateTask).not.toHaveBeenCalled()
    expect(view.text()).toContain('请完成作者、机构的名称选择或回显')
  })
  it('读取服务器计划版本后直接保存，再次打开重新读取', async () => {
    await open(); await click('调度')
    expect(wrapper!.text()).not.toContain('计划版本')
    await wrapper!.get('form').trigger('submit'); await flushPromises()
    expect(crawlApi.schedule).toHaveBeenCalledWith(7, '08:30', 'Asia/Shanghai', 6, 'FIXED_SCOPE_REFRESH', true)
    await click('调度')
    expect(crawlApi.getSchedule).toHaveBeenCalledTimes(2)
  })
  it('可由任务打开运行历史并取消等待中的运行', async () => {
    await open(); await click('运行历史')
    expect(crawlApi.runs).toHaveBeenCalledWith(7, 0, 20)
    await click('查看详情')
    expect(crawlApi.task).toHaveBeenCalledWith(7)
    await click('取消')
    expect(crawlApi.control).toHaveBeenCalledWith(8, 'cancel')
  })
  it('整页失败没有增加记录失败数时也刷新错误明细', async () => {
    vi.useFakeTimers()
    await open(); await click('立即执行')
    expect(crawlApi.failures).toHaveBeenCalledTimes(1)
    vi.mocked(crawlApi.run).mockResolvedValue({ ...pending, status: 'FAILED', completionReason: 'BATCH_FAILED' })
    await vi.advanceTimersByTimeAsync(1500); await flushPromises()
    expect(crawlApi.failures).toHaveBeenCalledTimes(2)
    expect(wrapper!.text()).toContain('检查点重试')
  })
})
