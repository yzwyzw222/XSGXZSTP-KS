import type { AuditLog } from '@/types/api'

export const auditActions: Record<string, string> = {
  LOGIN_SUCCEEDED: '登录成功', LOGIN_FAILED: '登录失败', LOGOUT: '退出登录',
  USER_CREATED: '创建用户', USER_UPDATED: '编辑用户', USER_ENABLED: '启用用户', USER_DISABLED: '停用用户',
  USER_PASSWORD_RESET: '重置密码', USER_ROLES_CHANGED: '调整角色', OPERATION_FAILED: '操作请求失败',
  SOURCE_CREATED: '创建数据源', SOURCE_UPDATED: '更新数据源', SOURCE_ENABLED: '启用数据源', SOURCE_DISABLED: '停用数据源', SOURCE_PROBED: '探测数据源',
  CRAWL_TASK_CREATED: '创建采集任务', CRAWL_TASK_UPDATED: '更新采集任务', CRAWL_TASK_TRIGGERED: '采集请求已受理', CRAWL_SCHEDULE_CHANGED: '调整采集计划',
  CRAWL_RUN_PAUSE_REQUESTED: '暂停请求已受理', CRAWL_RUN_RESUMED: '恢复采集', CRAWL_RUN_CANCEL_REQUESTED: '取消请求已受理', CRAWL_FAILURES_RETRIED: '重试采集失败项',
  DUPLICATE_CANDIDATE_ACCEPTED: '接受合并', DUPLICATE_CANDIDATE_REJECTED: '拒绝合并', MERGE_DECISION_REVERTED: '撤销合并',
  ACHIEVEMENT_FIELD_OVERRIDDEN: '修正成果字段', ACHIEVEMENT_FIELD_OVERRIDE_REVERTED: '撤销字段修正',
  GRAPH_EVENT_REPLAYED: '图事件重放已提交', GRAPH_BACKFILL_STARTED: '图回填已提交', GRAPH_RECONCILIATION_STARTED: '图对账已提交', GRAPH_REBUILD_STARTED: '图重建已提交',
  EXPORT_CREATED: '导出请求已受理', EXPORT_SUCCEEDED: '导出完成', EXPORT_FAILED: '导出失败', EXPORT_DOWNLOADED: '下载导出文件', ALERT_ACKNOWLEDGED: '确认告警',
}

export function auditActionLabel(log: AuditLog): string {
  const operation = log.summary?.operation
  if (log.action === 'OPERATION_FAILED' && operation) {
    const label = (auditActions[operation] ?? operation).replace(/已受理|已提交/g, '')
    return `${label} · 请求失败`
  }
  return auditActions[log.action] ?? log.action
}

/** 浏览器信息仅作客户端声明展示，不把 User-Agent 当作可信设备身份。 */
export function browserLabel(agent?: string | null): string {
  if (!agent) return '--'
  if (/Edg\//.test(agent)) return 'Microsoft Edge'
  if (/OPR\//.test(agent)) return 'Opera'
  if (/Firefox\//.test(agent)) return 'Firefox'
  if (/Chrome\//.test(agent)) return 'Chrome'
  if (/Safari\//.test(agent)) return 'Safari'
  return '其他客户端'
}
