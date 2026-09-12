import {
  BookOpen,
  CalendarDays,
  Network,
  FileInput,
  ContactRound,
  LayoutDashboard,
  Library,
  ShieldCheck,
  TrendingUp,
  Users,
  type LucideIcon,
} from 'lucide-vue-next'

import type { Permission } from '@/types/api'

export interface NavItem {
  group: NavGroupId
  label: string
  caption: string
  to: string
  activePaths?: readonly string[]
  icon: LucideIcon
  permission?: Permission
  preserveAuthor?: boolean
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
  { group: 'visualization', label: '工作台', caption: 'Workspace', to: '/', icon: LayoutDashboard, keywords: ['home', 'overview'] },
  { group: 'visualization', label: '可视化大屏', caption: 'Dashboard', to: '/dashboard', icon: LayoutDashboard, keywords: ['dashboard'] },
  { group: 'visualization', label: '成果目录', caption: 'Catalog', to: '/catalog', icon: Library, permission: 'CATALOG_READ', keywords: ['catalog', 'achievement'], children: [
    { label: '全部成果', to: '/catalog' },
  ] },
  { group: 'visualization', label: '实体编目', caption: 'Entities', to: '/catalog/authors', icon: ContactRound, permission: 'CATALOG_READ', activePaths: ['/catalog/authors', '/catalog/organizations', '/catalog/venues', '/catalog/topics', '/catalog/patents', '/catalog/master-theses', '/catalog/doctoral-theses'], children: [
    { label: '作者', to: '/catalog/authors' }, { label: '机构', to: '/catalog/organizations' },
    { label: '期刊', to: '/catalog/venues' }, { label: '主题', to: '/catalog/topics' },
    { label: '专利', to: '/catalog/patents' }, { label: '指导硕论', to: '/catalog/master-theses' },
    { label: '指导博论', to: '/catalog/doctoral-theses' },
  ] },
  { group: 'visualization', label: '学术关系图谱', caption: 'Relations', to: '/academic-relations', icon: Network, permission: 'GRAPH_READ', preserveAuthor: true, keywords: ['graph', '合作'] },
  { group: 'visualization', label: '学术成果图谱', caption: 'Achievements', to: '/academic-achievements', icon: BookOpen, permission: 'GRAPH_READ', preserveAuthor: true, keywords: ['graph', '成果'] },
  { group: 'visualization', label: '学术背景图谱', caption: 'Background', to: '/academic-background', icon: CalendarDays, permission: 'GRAPH_READ', preserveAuthor: true, keywords: ['graph', '时间线'] },
  { group: 'visualization', label: '统计分析', caption: 'Analytics', to: '/analytics', icon: TrendingUp, permission: 'ANALYTICS_READ', keywords: ['analytics', 'trend', 'chart'], children: [
    { label: '趋势与覆盖', to: '/analytics', activePaths: ['/analytics', '/analytics/coverage'] },
    { label: '成果分布', to: '/analytics/distributions', activePaths: ['/analytics/distributions', '/analytics/research'] },
    { label: '合作分析', to: '/analytics/collaboration' },
  ] },
  { group: 'crawler', label: '作者导入', caption: 'Author Import', to: '/author-import', icon: FileInput, permission: 'AUTHOR_IMPORT', keywords: ['导入', '学者', '知网', 'xlsx', 'xls', 'csv'] },
  { group: 'status', label: '日志管理', caption: 'Logs', to: '/logs', icon: ShieldCheck, permission: 'AUDIT_READ', keywords: ['logs', 'audit', 'login'] },
  { group: 'status', label: '账号管理', caption: 'Accounts', to: '/users', icon: Users, permission: 'USER_LIST', keywords: ['user', 'account', 'role', '用户管理'] },
]

export const navItems = allNavItems

/** 优先匹配更具体的模块路径，避免实体编目被成果目录前缀覆盖。 */
export function activeNavigation(path: string, items: readonly NavItem[] = navItems): NavItem | undefined {
  return items.filter(item => (item.activePaths ?? [item.to]).some(prefix =>
    path === prefix || (prefix !== '/' && path.startsWith(`${prefix}/`)),
  )).sort((a, b) => b.to.length - a.to.length)[0]
}
