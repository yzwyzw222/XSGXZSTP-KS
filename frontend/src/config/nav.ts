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
  label: string
  caption: string
  to: string
  icon: LucideIcon
  permission?: Permission
  /** 用于命令面板搜索的关键词 */
  keywords?: string[]
}

export const navItems: NavItem[] = [
  { label: '工作台', caption: 'Dashboard', to: '/', icon: LayoutDashboard, keywords: ['dashboard', 'overview', 'home'] },
  { label: '成果目录', caption: 'Catalog', to: '/catalog', icon: Library, permission: 'CATALOG_READ', keywords: ['catalog', 'achievement'] },
  { label: '数据源', caption: 'Sources', to: '/sources', icon: Database, permission: 'SOURCE_READ', keywords: ['source', 'openalex', 'crossref'] },
  { label: '采集任务', caption: 'Crawler', to: '/crawl', icon: Workflow, permission: 'CRAWL_TASK_READ', keywords: ['crawl', 'task', 'run'] },
  { label: '数据治理', caption: 'Governance', to: '/governance', icon: ShieldCheck, permission: 'GOVERNANCE_READ', keywords: ['governance', 'duplicate', 'merge'] },
  { label: '质量指标', caption: 'Quality', to: '/quality', icon: Activity, permission: 'GOVERNANCE_READ', keywords: ['quality', 'metric'] },
  { label: '知识图谱', caption: 'Network', to: '/graph', icon: Waypoints, permission: 'GRAPH_READ', keywords: ['graph', 'neo4j', 'network'] },
  { label: '统计分析', caption: 'Analytics', to: '/analytics', icon: TrendingUp, permission: 'ANALYTICS_READ', keywords: ['analytics', 'trend', 'chart'] },
  { label: '运行监控', caption: 'Operations', to: '/operations', icon: Share2, permission: 'OPERATIONS_READ', keywords: ['operations', 'health', 'alert'] },
  { label: '用户管理', caption: 'Accounts', to: '/users', icon: Users, permission: 'USER_LIST', keywords: ['user', 'account', 'role'] },
]
