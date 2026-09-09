import { describe, expect, it, vi } from 'vitest'

import {
  ExportFilterResolutionError,
  resolveExportFilter,
  type CatalogEntityResolver,
} from '@/utils/export-filter'

function entityPage(id: number, name: string) {
  return {
    items: [{ id, externalId: null, displayName: name, entityType: 'AUTHOR', achievementCount: 2 }],
    page: 0,
    size: 2,
    totalElements: 1,
    totalPages: 1,
  }
}

describe('目录筛选导出映射', () => {
  it('保留直接条件并将文本实体解析为唯一规范ID', async () => {
    const resolver = vi.fn<CatalogEntityResolver>(async (collection, name) =>
      entityPage({ authors: 1, organizations: 2, venues: 3, topics: 4 }[collection], name),
    )

    await expect(resolveExportFilter({
      title: ' 可信计算 ',
      author: '张研究员',
      organization: '可信实验室',
      publicationYear: 2026,
      achievementType: ' article ',
      sourceCode: 'openalex',
      venue: '安全学报',
      topic: '软件安全',
    }, resolver)).resolves.toEqual({
      title: '可信计算',
      authorId: 1,
      organizationId: 2,
      publicationYearFrom: 2026,
      publicationYearTo: 2026,
      achievementType: 'article',
      sourceType: 'OPENALEX',
      venueId: 3,
      topicId: 4,
    })
    expect(resolver).toHaveBeenCalledTimes(4)
  })

  it('实体匹配不唯一时拒绝扩大导出范围', async () => {
    const resolver = vi.fn<CatalogEntityResolver>(async () => ({
      ...entityPage(1, '张研究员'),
      totalElements: 2,
    }))

    await expect(resolveExportFilter({
      title: '',
      author: '张',
      organization: '',
      achievementType: '',
      sourceCode: '',
      venue: '',
      topic: '',
    }, resolver)).rejects.toBeInstanceOf(ExportFilterResolutionError)
  })

  it('拒绝冻结契约以外的来源代码', async () => {
    const resolver = vi.fn<CatalogEntityResolver>()

    await expect(resolveExportFilter({
      title: '',
      author: '',
      organization: '',
      achievementType: '',
      sourceCode: 'custom',
      venue: '',
      topic: '',
    }, resolver)).rejects.toThrow('来源代码仅支持')
    expect(resolver).not.toHaveBeenCalled()
  })
})
