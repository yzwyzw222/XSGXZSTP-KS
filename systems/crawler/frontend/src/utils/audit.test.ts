import { describe, expect, it } from 'vitest'
import type { AuditLog } from '@/types/api'
import { auditActionLabel } from './audit'

describe('异步操作审计文案', () => {
  it('受理与完成使用不同文案，失败请求不显示已受理', () => {
    const log = (action: string, summary: Record<string, string> = {}) => ({ action, summary }) as AuditLog
    expect(auditActionLabel(log('EXPORT_CREATED'))).toBe('导出请求已受理')
    expect(auditActionLabel(log('EXPORT_SUCCEEDED'))).toBe('导出完成')
    expect(auditActionLabel(log('OPERATION_FAILED', { operation: 'EXPORT_CREATED' }))).toBe('导出请求 · 请求失败')
    expect(auditActionLabel(log('OPERATION_FAILED', { operation: 'GRAPH_BACKFILL_STARTED' }))).toBe('图回填 · 请求失败')
  })
})
