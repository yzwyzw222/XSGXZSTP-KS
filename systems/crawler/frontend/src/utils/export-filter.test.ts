import { expect, it } from 'vitest'
import { resolveExportFilter } from './export-filter'
import { catalogRouteQuery, readCatalogQuery } from './catalog-query'

it('目录分页和实体选择可经 URL 往返恢复，导出沿用相同条件', () => {
  const query = readCatalogQuery({ title: ' 学术 ', author: '王伟', authorId: '42', organization: '研究院', publicationYear: '2026', page: '3', size: '20' })
  expect(readCatalogQuery(catalogRouteQuery(query) as Record<string, string>)).toEqual(query)
  expect(resolveExportFilter(query)).toEqual({ title: '学术', author: '王伟', authorId: 42, organization: '研究院', publicationYearFrom: 2026, publicationYearTo: 2026 })
})
it('未选择候选时保留模糊文本，无需重新解析唯一实体', () => {
  expect(resolveExportFilter(readCatalogQuery({ author: '王', organization: '科技', venue: 'Science', topic: '图', sourceCode: 'CROSSREF' })))
    .toEqual({ author: '王', organization: '科技', venue: 'Science', topic: '图', sourceCode: 'CROSSREF' })
})
it('忽略未知参数和非法边界，不把空条件传给导出', () => {
  const query = readCatalogQuery({ page: '-1', size: '101', authorId: '9007199254740992', venueId: '1.5', publicationYear: '999', title: ['a', 'b'], redirect: '/admin' })
  expect(query.page).toBe(0)
  expect(query.size).toBe(20)
  expect(resolveExportFilter(query)).toEqual({})
})
