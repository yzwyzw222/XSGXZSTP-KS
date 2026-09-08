import {
  Activity,
  BookOpen,
  Database,
  LayoutDashboard,
  Library,
  ShieldCheck,
  TrendingUp,
  Users,
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
  /** 父模块下的子模块入口，可独立展开或收起。 */
  children?: readonly { label: string; to: string; activePaths?: readonly string[] }[]
}

export type NavGroupId = 'visualization' | 'crawler' | 'status'

export const navGroups: ReadonlyArray<{ id: NavGroupId; label: string }> = [
  { id: 'visualization', label: '研究工作' },
  { id: 'crawler', label: '数据管理' },
  { id: 'status', label: '管理工具' },
]

/** 权限过滤后再分组，避免向无权限用户展示空模块。 */
export function groupNavigation(items: readonly NavItem[]) {
  return navGroups
    .map((group) => ({ ...group, items: items.filter((item) => item.group === group.id) }))
    .filter((group) => group.items.length > 0)
}

export const navItems: NavItem[] = [
  { group: 'visualization', label: '工作台', caption: 'Dashboard', to: '/', icon: LayoutDashboard, keywords: ['dashboard', 'overview', 'home'] },
  { group: 'visualization', label: '成果目录', caption: 'Catalog', to: '/catalog', icon: Library, permission: 'CATALOG_READ', keywords: ['catalog', 'achievement'], children: [
    { label: '全部成果', to: '/catalog' },
    { label: '实体编目', to: '/catalog/authors', activePaths: ['/catalog/authors', '/catalog/organizations', '/catalog/venues', '/catalog/topics'] },
  ] },
  {
    group: 'visualization', label: '知识图谱', caption: 'Network', to: '/graph', icon: BookOpen, permission: 'GRAPH_READ', keywords: ['graph', 'network'],
    children: [
      { label: '图谱概览', to: '/graph' },
      { label: '实体管理', to: '/graph/entities' },
      { label: '关系管理', to: '/graph/relations' },
    ],
  },
  { group: 'visualization', label: '统计分析', caption: 'Analytics', to: '/analytics', icon: TrendingUp, permission: 'ANALYTICS_READ', keywords: ['analytics', 'trend', 'chart'], children: [
    { label: '趋势与覆盖', to: '/analytics', activePaths: ['/analytics', '/analytics/coverage'] },
    { label: '成果分布', to: '/analytics/distributions', activePaths: ['/analytics/distributions', '/analytics/research'] },
    { label: '合作分析', to: '/analytics/collaboration' },
  ] },
  { group: 'crawler', label: '数据源', caption: 'Sources', to: '/sources', icon: Database, permission: 'SOURCE_READ', keywords: ['source', 'openalex', 'crossref'] },
  { group: 'crawler', label: '采集任务', caption: 'Crawler', to: '/crawl', icon: Workflow, permission: 'CRAWL_TASK_READ', keywords: ['crawl', 'task', 'run'] },
  { group: 'crawler', label: '数据治理', caption: 'Governance', to: '/governance', icon: ShieldCheck, permission: 'GOVERNANCE_READ', keywords: ['governance', 'duplicate', 'merge'] },
  { group: 'crawler', label: '质量指标', caption: 'Quality', to: '/quality', icon: Activity, permission: 'GOVERNANCE_READ', keywords: ['quality', 'metric'] },
  { group: 'status', label: '日志管理', caption: 'Logs', to: '/logs', icon: ShieldCheck, permission: 'AUDIT_READ', keywords: ['logs', 'audit', 'login'] },
  { group: 'status', label: '账号管理', caption: 'Accounts', to: '/users', icon: Users, permission: 'USER_LIST', keywords: ['user', 'account', 'role', '用户管理'] },
]
