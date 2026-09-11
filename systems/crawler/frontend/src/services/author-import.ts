import { api, apiRequest } from '@/services/api'

export interface ImportOptions {
  scholarName: string
  scholarOrganization: string
  authorId?: number
  sheetIndex: number
  headerRow: number
  mode: 'AUTHOR' | 'MASTER_SUPERVISION' | 'DOCTOR_SUPERVISION'
  mapping: Record<string, number>
}

export interface ImportRow {
  rowNumber: number
  title: string
  type: string
  authors: string[]
  organizations: string[]
  keywords: string[]
  abstractText: string
  publicationDate: string | null
  errors: string[]
  warnings: string[]
}

export interface ImportPreview {
  previewKey: string
  sheets: string[]
  headers: string[]
  mapping: Record<string, number>
  totalRows: number
  validRows: number
  rows: ImportRow[]
  issues: Array<{ rowNumber: number; errors: string[]; warnings: string[] }>
}

export interface ImportSummary {
  id: number
  authorId: number
  scholarName: string
  fileName: string
  sheetName: string
  importMode: string
  totalRows: number
  importedCount: number
  linkedCount: number
  skippedCount: number
  createdAt: string
}

export interface ImportEvidence {
  batchId: number
  fileName: string
  sheetName: string
  importMode: string
  scholarName: string
  rowNumber: number
  originalColumns: Record<string, string>
}

export interface ImportFileSettings {
  sheetIndex: number
  headerRow: number
  mapping: Record<string, number>
}

export interface ImportBundleOptions {
  scholarName: string
  files: ImportFileSettings[]
}

export interface ImportBundlePreview {
  previewKey: string
  scholarName: string
  candidates: string[]
  organizations: string[]
  messages: string[]
  canConfirm: boolean
  totalRows: number
  validRows: number
  files: Array<{ fileName: string; modes: string[]; preview: ImportPreview }>
}

export interface ImportBundleSummary {
  authorId: number
  scholarName: string
  importedCount: number
  linkedCount: number
  skippedCount: number
  batches: ImportSummary[]
}

function uploadFiles<T>(action: string, files: File[], options: ImportBundleOptions, signal?: AbortSignal, previewKey?: string) {
  const body = new FormData()
  files.forEach(file => body.append('files', file))
  body.append('options', JSON.stringify(options))
  if (previewKey) body.append('previewKey', previewKey)
  return apiRequest<T>(`/api/v1/author-import/files/${action}`, { method: 'POST', body, signal, timeoutMs: 90000 })
}

function upload<T>(action: string, file: File, options: ImportOptions, signal?: AbortSignal, previewKey?: string) {
  const body = new FormData()
  body.append('file', file)
  body.append('options', JSON.stringify(options))
  if (previewKey) body.append('previewKey', previewKey)
  return apiRequest<T>(`/api/v1/author-import/${action}`, { method: 'POST', body, signal, timeoutMs: 90000 })
}

export const authorImportApi = {
  previewFiles: (files: File[], options: ImportBundleOptions, signal?: AbortSignal) => uploadFiles<ImportBundlePreview>('preview', files, options, signal),
  confirmFiles: (files: File[], options: ImportBundleOptions, previewKey: string, signal?: AbortSignal) => uploadFiles<ImportBundleSummary>('confirm', files, options, signal, previewKey),
  preview: (file: File, options: ImportOptions, signal?: AbortSignal) => upload<ImportPreview>('preview', file, options, signal),
  confirm: (file: File, options: ImportOptions, previewKey: string, signal?: AbortSignal) => upload<ImportSummary>('confirm', file, options, signal, previewKey),
  recent: () => api.get<ImportSummary[]>('/api/v1/author-import'),
  evidence: (id: number) => api.get<ImportEvidence[]>(`/api/v1/author-import/achievements/${id}/evidence`),
}

export const importFields = [
  ['database', '来源库'], ['title', '题名'], ['authors', '作者 / 发明人'], ['organizations', '单位 / 机构'],
  ['venue', '文献来源'], ['keywords', '关键词'], ['abstract', '摘要'], ['date', '发表时间'],
  ['firstAuthor', '第一责任人'], ['year', '年'], ['issn', 'ISSN'], ['url', '网址'], ['doi', 'DOI'],
] as const

export function importModeLabel(mode: string): string {
  return ({ AUTHOR: '本人署名成果', MASTER_SUPERVISION: '硕士论文指导', DOCTOR_SUPERVISION: '博士论文指导' } as Record<string, string>)[mode] ?? mode
}

export function authorGraphTarget(authorId: number) {
  return { path: '/graph/explore', query: { centerType: 'AUTHOR', centerId: String(authorId), depth: '2' } }
}
