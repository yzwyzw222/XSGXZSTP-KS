import {
  Activity,
  BookOpen,
  Database,
  ContactRound,
  LayoutDashboard,
  Library,
  ShieldCheck,
  TrendingUp,
  Users,
  Workflow,
  type LucideIcon,
} from 'lucide-vue-next'

import { integrated } from '@/services/portal-auth'
import type { Permission } from '@/types/api'

export interface NavItem {
  group: NavGroupId
  label: string
  caption: string
  to: string
  activePaths?: readonly string[]
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

const allNavItems: NavItem[] = [
  { group: 'visualization', label: '可视化大屏', caption: 'Dashboard', to: '/', icon: LayoutDashboard, keywords: ['dashboard', 'overview', 'home'] },
  { group: 'visualization', label: '成果目录', caption: 'Catalog', to: '/catalog', icon: Library, permission: 'CATALOG_READ', keywords: ['catalog', 'achievement'], children: [
    { label: '全部成果', to: '/catalog' },
  ] },
  { group: 'visualization', label: '实体编目', caption: 'Entities', to: '/catalog/authors', icon: ContactRound, permission: 'CATALOG_READ', activePaths: ['/catalog/authors', '/catalog/organizations', '/catalog/venues', '/catalog/topics'], children: [
    { label: '作者', to: '/catalog/authors' }, { label: '机构', to: '/catalog/organizations' },
    { label: '期刊', to: '/catalog/venues' }, { label: '主题', to: '/catalog/topics' },
  ] },
  {
    group: 'visualization', label: '知识图谱', caption: 'Network', to: '/graph', icon: BookOpen, permission: 'GRAPH_READ', keywords: ['graph', 'network'],
    children: [
      { label: '图谱概览', to: '/graph' },
      { label: '实体管理', to: '/graph/entities' },
      { label: '关系管理', to: '/graph/relations' },
      { label: '高级查询', to: '/graph/explore' },
      { label: '路径分析', to: '/graph/path' },
      { label: '保存的查询', to: '/graph/queries' },
    ],
  },
  { group: 'visualization', label: '统计分析', caption: 'Analytics', to: '/analytics', icon: TrendingUp, permission: 'ANALYTICS_READ', keywords: ['analytics', 'trend', 'chart'], children: [
    { label: '趋势与覆盖', to: '/analytics', activePaths: ['/analytics', '/analytics/coverage'] },
    { label: '成果分布', to: '/analytics/distributions', activePaths: ['/analytics/distributions', '/analytics/research'] },
    { label: '合作分析', to: '/analytics/collaboration' },
  ] },
  { group: 'crawler', label: '数据源', caption: 'Sources', to: '/sources', icon: Database, permission: 'SOURCE_READ', keywords: ['source', 'openalex', 'crossref'] },
  { group: 'crawler', label: '采集任务', caption: 'Collection', to: '/crawl', icon: Workflow, permission: 'CRAWL_TASK_READ', keywords: ['crawl', 'collection', 'task', 'run'] },
  { group: 'crawler', label: '数据治理', caption: 'Governance', to: '/governance', icon: ShieldCheck, permission: 'GOVERNANCE_READ', keywords: ['governance', 'duplicate', 'merge'] },
  { group: 'crawler', label: '质量指标', caption: 'Quality', to: '/quality', icon: Activity, permission: 'GOVERNANCE_READ', keywords: ['quality', 'metric'] },
  { group: 'status', label: '日志管理', caption: 'Logs', to: '/logs', icon: ShieldCheck, permission: 'AUDIT_READ', keywords: ['logs', 'audit', 'login'] },
  { group: 'status', label: '账号管理', caption: 'Accounts', to: '/users', icon: Users, permission: 'USER_LIST', keywords: ['user', 'account', 'role', '用户管理'] },
]

export const navItems = integrated ? allNavItems.filter(item => item.to !== '/logs' && item.to !== '/users') : allNavItems

/** 优先匹配更具体的模块路径，避免实体编目被成果目录前缀覆盖。 */
export function activeNavigation(path: string, items: readonly NavItem[] = navItems): NavItem | undefined {
  return items.filter(item => (item.activePaths ?? [item.to]).some(prefix =>
    path === prefix || (prefix !== '/' && path.startsWith(`${prefix}/`)),
  )).sort((a, b) => b.to.length - a.to.length)[0]
}
