import { mount } from '@vue/test-utils'
import { ElPagination } from 'element-plus'
import { expect, it } from 'vitest'
import DataTable from '@/components/business/DataTable.vue'

it('分页以零基回传，排序交给服务端且保留当前数据次序和业务主键', async () => {
  const rows = [{ id: 'b', title: '乙' }, { id: 'a', title: '甲' }]
  const wrapper = mount(DataTable, {
    props: {
      columns: [{ accessorKey: 'title', header: '题名', enableSorting: true }],
      data: rows, page: 0, size: 2, total: 5,
      getRowId: (row: Record<string, unknown>) => String(row.id),
    },
  })
  const pagination = wrapper.findComponent(ElPagination)
  expect(pagination.props('currentPage')).toBe(1)
  pagination.vm.$emit('update:current-page', 2)
  expect(wrapper.emitted('update:page')).toEqual([[1]])
  const table = wrapper.findComponent({ name: 'ElTable' })
  table.vm.$emit('sort-change', { prop: 'title', order: 'descending' })
  table.vm.$emit('sort-change', { prop: null, order: null })
  expect(wrapper.emitted('sort')).toEqual([[[{ id: 'title', desc: true }]], [[]]])
  expect(table.props('data')).toEqual(rows)
  expect((table.props('rowKey') as (row: typeof rows[number]) => string)(rows[0]!)).toBe('b')
  await wrapper.setProps({ page: 2, data: [] })
  expect(pagination.props('currentPage')).toBe(3)
  await wrapper.setProps({ total: 0 })
  expect(wrapper.findComponent(ElPagination).exists()).toBe(false)
  expect(wrapper.text()).toContain('暂无数据')
  wrapper.unmount()
})
