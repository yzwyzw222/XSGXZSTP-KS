import { afterEach, beforeEach, expect, it } from 'vitest'
import { authorImportApi, type ImportOptions } from './author-import'
import { clearCsrfToken, resetUnauthorizedLatch } from './api'
import { installHttpStub, json, type HttpStub } from '@/test/http-stub'

let stub: HttpStub | undefined
beforeEach(() => { clearCsrfToken(); resetUnauthorizedLatch() })
afterEach(() => { stub?.restore(); clearCsrfToken() })
it('上传采用 multipart 并沿用 CSRF，确认时携带预览标识和字段映射', async () => {
  stub = installHttpStub(request => request.url?.endsWith('/auth/csrf')
    ? json({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'synthetic-token' }) : json({}))
  const options: ImportOptions = { scholarName: '张三', scholarOrganization: '', sheetIndex: 0, headerRow: 1, mode: 'AUTHOR', mapping: { title: 0, authors: 1 } }
  const file = new File(['Title,Author\n论文,张三'], '信息.csv')
  await authorImportApi.confirm(file, options, 'reviewed-file')
  const request = stub.requests.find(item => item.url.endsWith('/confirm'))!
  expect(request.headers['X-CSRF-TOKEN']).toBe('synthetic-token')
  expect(request.data).toBeInstanceOf(FormData)
  expect((request.data as FormData).get('previewKey')).toBe('reviewed-file')
  expect(JSON.parse((request.data as FormData).get('options') as string)).toEqual(options)
})

it('多文件确认无需手填姓名机构或关系，保留文件顺序及每份表格设置', async () => {
  stub = installHttpStub(request => request.url?.endsWith('/auth/csrf')
    ? json({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'synthetic-token' }) : json({}))
  const files = [new File(['期刊'], '成果.csv'), new File(['硕士'], '硕论.csv')]
  const options = { scholarName: '', files: files.map(() => ({ sheetIndex: 0, headerRow: 1, mapping: {} })) }
  await authorImportApi.confirmFiles(files, options, 'reviewed-bundle')
  const request = stub.requests.find(item => item.url.endsWith('/files/confirm'))!
  expect(request.headers['X-CSRF-TOKEN']).toBe('synthetic-token')
  const body = request.data as FormData
  expect(body.getAll('files')).toEqual(files)
  expect(body.get('previewKey')).toBe('reviewed-bundle')
  expect(JSON.parse(body.get('options') as string)).toEqual(options)
})
