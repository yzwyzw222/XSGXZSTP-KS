import {
  Activity,
  Database,
  LayoutDashboard,
  Library,
  Share2,
  ShieldCheck,
  TrendingUp,
  Users,
  Waypoints,
  Workflow,
  type LucideIcon,
} from 'lucide-vue-next'

import type { Permission } from '@/types/api'

export interface NavItem {
  group: NavGroupId
  label: string
  caption: string
  to: string
  icon: LucideIcon
  permission?: Permission
  /** 用于命令面板搜索的关键词 */
  keywords?: string[]
}

export type NavGroupId = 'visualization' | 'crawler' | 'status' | 'users'

export const navGroups: ReadonlyArray<{ id: NavGroupId; label: string }> = [
  { id: 'visualization', label: '可视化' },
  { id: 'crawler', label: '爬虫管理' },
  { id: 'status', label: '系统状态' },
  { id: 'users', label: '用户管理' },
]

/** 权限过滤后再分组，避免向无权限用户展示空模块。 */
export function groupNavigation(items: readonly NavItem[]) {
  return navGroups
    .map((group) => ({ ...group, items: items.filter((item) => item.group === group.id) }))
    .filter((group) => group.items.length > 0)
}

export const navItems: NavItem[] = [
  { group: 'visualization', label: '工作台', caption: 'Dashboard', to: '/', icon: LayoutDashboard, keywords: ['dashboard', 'overview', 'home'] },
  { group: 'visualization', label: '成果目录', caption: 'Catalog', to: '/catalog', icon: Library, permission: 'CATALOG_READ', keywords: ['catalog', 'achievement'] },
  { group: 'visualization', label: '知识图谱', caption: 'Network', to: '/graph', icon: Waypoints, permission: 'GRAPH_READ', keywords: ['graph', 'neo4j', 'network'] },
  { group: 'visualization', label: '统计分析', caption: 'Analytics', to: '/analytics', icon: TrendingUp, permission: 'ANALYTICS_READ', keywords: ['analytics', 'trend', 'chart'] },
  { group: 'crawler', label: '数据源', caption: 'Sources', to: '/sources', icon: Database, permission: 'SOURCE_READ', keywords: ['source', 'openalex', 'crossref'] },
  { group: 'crawler', label: '采集任务', caption: 'Crawler', to: '/crawl', icon: Workflow, permission: 'CRAWL_TASK_READ', keywords: ['crawl', 'task', 'run'] },
  { group: 'crawler', label: '数据治理', caption: 'Governance', to: '/governance', icon: ShieldCheck, permission: 'GOVERNANCE_READ', keywords: ['governance', 'duplicate', 'merge'] },
  { group: 'status', label: '质量指标', caption: 'Quality', to: '/quality', icon: Activity, permission: 'GOVERNANCE_READ', keywords: ['quality', 'metric'] },
  { group: 'status', label: '运行监控', caption: 'Operations', to: '/operations', icon: Share2, permission: 'OPERATIONS_READ', keywords: ['operations', 'health', 'alert'] },
  { group: 'status', label: '日志管理', caption: 'Logs', to: '/logs', icon: ShieldCheck, permission: 'AUDIT_READ', keywords: ['logs', 'audit', 'login'] },
  { group: 'users', label: '账号管理', caption: 'Accounts', to: '/users', icon: Users, permission: 'USER_LIST', keywords: ['user', 'account', 'role', '用户管理'] },
]
