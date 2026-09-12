import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AuthorImportView from './AuthorImportView.vue'
import { ApiError } from '@/services/api'

const mocks = vi.hoisted(() => ({ previewFiles: vi.fn(), confirmFiles: vi.fn(), recent: vi.fn() }))
vi.mock('@/services/author-import', async original => ({ ...await original<typeof import('@/services/author-import')>(), authorImportApi: mocks }))
vi.mock('@/services/business', () => ({ catalogApi: { entities: vi.fn().mockResolvedValue({ items: [] }) } }))
const validPreview = { previewKey: 'reviewed', sheets: ['信息表'], headers: ['Title-题名', 'Author-作者'], mapping: { title: 0, authors: 1 }, totalRows: 1, validRows: 1,
  rows: [{ rowNumber: 2, title: '导入测试论文', type: 'article', authors: ['张三'], organizations: ['测试大学'], abstractText: '测试摘要', keywords: [], publicationDate: '2025-01-01', errors: [], warnings: [] }], issues: [] }
const bundle = { previewKey: 'reviewed', scholarName: '张三', candidates: ['张三'], organizations: ['测试大学'], messages: [], canConfirm: true, totalRows: 1, validRows: 1,
  files: [{ fileName: '信息.csv', modes: ['AUTHOR'], preview: validPreview }] }
let wrapper: VueWrapper | undefined
function render() {
  wrapper = mount(AuthorImportView, { global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
  return wrapper
}
async function fill(name = '信息.csv', content = 'Title-题名,Author-作者\n导入测试论文,张三') {
  const input = wrapper!.get('input[type=file]')
  Object.defineProperty(input.element, 'files', { value: [new File([content], name)], configurable: true })
  await input.trigger('change')
}
function button(name: string) { return wrapper!.findAll('button').find(item => item.text().includes(name))! }
beforeEach(() => {
  vi.clearAllMocks()
  mocks.recent.mockResolvedValue([])
  mocks.previewFiles.mockResolvedValue(structuredClone(bundle))
  mocks.confirmFiles.mockResolvedValue({ authorId: 12, scholarName: '张三', importedCount: 1, linkedCount: 0, skippedCount: 0, batches: [] })
})
afterEach(() => wrapper?.unmount())

describe('作者信息表导入', () => {
  it('无预览不提交，校验成功后确认并展示统计及图谱入口', async () => {
    render(); await fill()
    expect(wrapper!.find('input[aria-label="学者名称检索"]').exists()).toBe(false)
    expect(wrapper!.find('#scholar-organization').exists()).toBe(false)
    expect(mocks.confirmFiles).not.toHaveBeenCalled()
    await button('解析并预览').trigger('click'); await flushPromises()
    expect(wrapper!.text()).toContain('导入测试论文')
    expect(wrapper!.text()).toContain('测试摘要')
    expect(button('确认导入').attributes('disabled')).toBeUndefined()
    await button('确认导入').trigger('click'); await flushPromises()
    expect(mocks.confirmFiles).toHaveBeenCalledOnce()
    expect(mocks.confirmFiles.mock.calls[0]![2]).toBe('reviewed')
    expect(mocks.previewFiles.mock.calls[0]![1].scholarName).toBe('')
    expect(wrapper!.text()).toContain('导入已完成')
    expect(wrapper!.get('[role="status"]').text()).toContain('作者内部标识：12')
    expect(wrapper!.text()).not.toContain('ORCID')
    expect(wrapper!.text()).toContain('查看 张三 的知识图谱')
    expect(button('确认导入').attributes('disabled')).toBeDefined()
  })

  it('表头变更会使预览失效，任一文件的问题行阻止提交', async () => {
    render(); await fill()
    await button('解析并预览').trigger('click'); await flushPromises()
    await wrapper!.get('input[aria-label="表头所在行"]').setValue('2')
    await wrapper!.get('input[aria-label="表头所在行"]').trigger('change')
    expect(wrapper!.text()).toContain('请重新解析预览')
    expect(button('确认导入').attributes('disabled')).toBeDefined()
    mocks.previewFiles.mockResolvedValue({ ...bundle, canConfirm: false, validRows: 0, files: [{ ...bundle.files[0], preview: { ...validPreview, validRows: 0, issues: [{ rowNumber: 22, errors: ['缺少题名'], warnings: [] }] } }] })
    await button('重新解析预览').trigger('click'); await flushPromises()
    expect(wrapper!.text()).toContain('第 22 行')
    expect(wrapper!.text()).toContain('缺少题名')
    expect(button('确认导入').attributes('disabled')).toBeDefined()
  })

  it('非法文件不会上传，服务失败展示可重试的错误', async () => {
    render(); await fill('信息.exe')
    expect(wrapper!.text()).toContain('请选择 XLSX、XLS 或 CSV')
    await button('解析并预览').trigger('click'); await flushPromises()
    expect(mocks.previewFiles).not.toHaveBeenCalled()
    await fill()
    mocks.previewFiles.mockRejectedValue(new ApiError('文件解析失败', 400))
    await button('解析并预览').trigger('click'); await flushPromises()
    expect(wrapper!.text()).toContain('文件解析失败')
    expect(mocks.confirmFiles).not.toHaveBeenCalled()
  })

  it('更换文件后丢弃旧请求的预览结果', async () => {
    let resolve!: (value: typeof bundle) => void
    mocks.previewFiles.mockReturnValue(new Promise(done => { resolve = done }))
    render(); await fill()
    await button('解析并预览').trigger('click')
    await fill('新的信息.csv')
    resolve(bundle); await flushPromises()
    expect(wrapper!.text()).not.toContain('导入测试论文')
    expect(wrapper!.text()).toContain('新的信息.csv')
    expect(mocks.previewFiles.mock.calls[0]![2].aborted).toBe(true)
  })

  it('多人候选只需点选并重新解析，选定前无法确认', async () => {
    mocks.previewFiles.mockResolvedValueOnce({ ...bundle, scholarName: '', candidates: ['张三', '李四'], canConfirm: false })
    render(); await fill()
    await button('解析并预览').trigger('click'); await flushPromises()
    expect(button('确认导入').attributes('disabled')).toBeDefined()
    await button('选择 张三').trigger('click'); await flushPromises()
    expect(mocks.previewFiles.mock.calls[1]![1].scholarName).toBe('张三')
    expect(button('确认导入').attributes('disabled')).toBeUndefined()
  })

  it('多文件一起上传，硕博识别结果与主表归属一起展示', async () => {
    mocks.previewFiles.mockResolvedValue({ ...bundle, totalRows: 2, validRows: 2, files: [...bundle.files, { fileName: '硕论.csv', modes: ['MASTER_SUPERVISION'], preview: validPreview }] })
    render()
    const input = wrapper!.get('input[type=file]')
    Object.defineProperty(input.element, 'files', { value: [new File(['a'], '信息.csv'), new File(['b'], '硕论.csv')], configurable: true })
    await input.trigger('change')
    await button('解析并预览').trigger('click'); await flushPromises()
    expect(mocks.previewFiles.mock.calls[0]![0]).toHaveLength(2)
    expect(mocks.previewFiles.mock.calls[0]![1].files).toHaveLength(2)
    expect(wrapper!.text()).toContain('硕士论文指导')
    expect(button('确认导入').text()).toContain('2 条')
  })
})
