import { describe, expect, it } from 'vitest'
import { catalogRouteQuery, readCatalogQuery } from './catalog-query'
import { resolveExportFilter } from './export-filter'

describe('紧凑成果目录条件', () => {
  it('保留搜索、内部标识与年份，丢弃已删除的类型来源条件', () => {
    const query = readCatalogQuery({ author: '  学者  ', authorId: '42', publicationYear: '2025', achievementType: 'article', sourceCode: 'CNKI', unused: 'value' })
    expect(catalogRouteQuery(query)).toEqual({ author: '学者', authorId: '42', publicationYear: '2025' })
    expect(resolveExportFilter(query)).toEqual({ author: '学者', authorId: 42, publicationYearFrom: 2025, publicationYearTo: 2025 })
  })
  it('限制非法年份、分页、数组和超长输入', () => {
    const query = readCatalogQuery({ publicationYear: '10000', page: '-1', size: '101', authorId: '0', title: ['a', 'b'], topic: 'x'.repeat(230) })
    expect(query).toMatchObject({ publicationYear: undefined, page: 0, size: 20, authorId: undefined, title: '', topic: 'x'.repeat(200) })
  })
  it('直接序列化也不重新加入类型和来源选择条件', () => {
    expect(catalogRouteQuery({ title: '论文', achievementType: 'patent', sourceCode: 'CNKI', page: 2, size: 50 }))
      .toEqual({ title: '论文', page: '2', size: '50' })
  })
})
