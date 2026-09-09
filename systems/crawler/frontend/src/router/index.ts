import {
  createRouter,
  createWebHistory,
  type RouterHistory,
  type RouteRecordRaw,
} from 'vue-router'

import { useSessionStore } from '@/stores/session'
import { cancelSessionRequests } from '@/services/http'
import type { Permission } from '@/types/api'
import { integrated, redirectToPortal } from '@/services/portal-auth'

declare module 'vue-router' {
  interface RouteMeta {
    shell?: 'dashboard' | 'module'
    public?: boolean
    permission?: Permission
    title?: string
    /** 同模块子页复用组件和筛选状态，切换时不取消该模块的读取。 */
    workspace?: string
  }
}

export const routes: RouteRecordRaw[] = [
  ...(integrated ? [] : [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/session-expired',
    name: 'session-expired',
    component: () => import('@/views/SessionExpiredView.vue'),
    meta: { public: true, title: '会话已过期' },
  },
  ]),
  {
    path: '/forbidden',
    name: 'forbidden',
    component: () => import('@/views/ForbiddenView.vue'),
    meta: { public: true, title: '无权访问' },
  },
  {
    path: '/',
    component: () => import('@/layouts/BusinessLayout.vue'),
    meta: { shell: 'module' },
    children: [
      {
        path: '',
        name: 'overview',
        component: () => import('@/views/OverviewView.vue'),
        meta: { title: '可视化大屏', workspace: 'overview', shell: 'dashboard' },
      },
      {
        path: 'overview',
        meta: { title: '工作台', workspace: 'overview' },
        children: [
          { path: 'research', name: 'overview-research', redirect: '/analytics/collaboration', meta: { title: '研究关系' } },
          { path: 'activity', name: 'overview-activity', redirect: '/logs', meta: { title: '活动与采集' } },
        ],
      },
      {
        path: 'catalog',
        name: 'catalog',
        component: () => import('@/views/CatalogView.vue'),
        meta: { permission: 'CATALOG_READ', title: '成果目录' },
      },
      {
        path: 'catalog/achievements/:id',
        name: 'achievement-detail',
        component: () => import('@/views/AchievementDetailView.vue'),
        meta: { permission: 'CATALOG_READ', title: '成果详情' },
      },
      {
        path: 'catalog/:collection(authors|organizations|venues|topics)',
        name: 'catalog-entities',
        component: () => import('@/views/CatalogEntitiesView.vue'),
        meta: { permission: 'CATALOG_READ', title: '编目实体' },
      },
      {
        path: 'sources',
        name: 'sources',
        component: () => import('@/views/SourcesView.vue'),
        meta: { permission: 'SOURCE_READ', title: '数据源' },
      },
      {
        path: 'crawl',
        name: 'crawl-tasks',
        component: () => import('@/views/CrawlTasksView.vue'),
        meta: { permission: 'CRAWL_TASK_READ', title: '采集任务' },
      },
      {
        path: 'governance',
        name: 'governance',
        component: () => import('@/views/GovernanceView.vue'),
        meta: { permission: 'GOVERNANCE_READ', title: '数据治理' },
      },
      {
        path: 'quality',
        name: 'quality',
        component: () => import('@/views/QualityView.vue'),
        meta: { permission: 'GOVERNANCE_READ', title: '质量指标' },
      },
      {
        path: 'graph',
        meta: { permission: 'GRAPH_READ', title: '知识图谱' },
        children: [
          {
            path: '',
            name: 'graph',
            component: () => import('@/views/GraphView.vue'),
            meta: { permission: 'GRAPH_READ', title: '图谱概览' },
          },
          {
            path: 'explore',
            name: 'graph-explore',
            component: () => import('@/views/GraphExploreView.vue'),
            meta: { permission: 'GRAPH_READ', title: '高级查询' },
          },
          {
            path: 'entities',
            name: 'graph-entities',
            component: () => import('@/views/GraphTypesView.vue'),
            props: { kind: 'NODE' },
            meta: { permission: 'GRAPH_READ', title: '实体管理' },
          },
          {
            path: 'relations',
            name: 'graph-relations',
            component: () => import('@/views/GraphTypesView.vue'),
            props: { kind: 'RELATIONSHIP' },
            meta: { permission: 'GRAPH_READ', title: '关系管理' },
          },
          {
            path: 'path',
            name: 'graph-path',
            component: () => import('@/views/GraphPathView.vue'),
            meta: { permission: 'GRAPH_READ', title: '路径分析' },
          },
          {
            path: 'queries',
            name: 'graph-queries',
            component: () => import('@/views/GraphQueriesView.vue'),
            meta: { permission: 'GRAPH_READ', title: '常用查询' },
          },
        ],
      },
      {
        path: 'analytics',
        meta: { permission: 'ANALYTICS_READ', title: '统计分析', workspace: 'analytics' },
        children: [
          { path: '', name: 'analytics', component: () => import('@/views/AnalyticsView.vue'), props: { section: 'overview' }, meta: { title: '趋势总览' } },
          { path: 'coverage', name: 'analytics-coverage', component: () => import('@/views/AnalyticsView.vue'), props: { section: 'coverage' }, meta: { title: '字段覆盖' } },
          { path: 'distributions', name: 'analytics-distributions', component: () => import('@/views/AnalyticsView.vue'), props: { section: 'distributions' }, meta: { title: '类型与来源' } },
          { path: 'research', name: 'analytics-research', component: () => import('@/views/AnalyticsView.vue'), props: { section: 'research' }, meta: { title: '机构与主题' } },
          { path: 'collaboration', name: 'analytics-collaboration', component: () => import('@/views/AnalyticsView.vue'), props: { section: 'collaboration' }, meta: { title: '合作分析' } },
        ],
      },
      {
        path: 'users',
        meta: { permission: 'USER_LIST', title: '账号管理', workspace: 'users' },
        children: [
          { path: '', name: 'users', component: () => import('@/views/UsersView.vue'), props: { section: 'accounts' }, meta: { title: '系统账号' } },
          { path: 'overview', name: 'users-overview', component: () => import('@/views/UsersView.vue'), props: { section: 'overview' }, meta: { title: '账号概况' } },
        ],
      },
      {
        path: 'logs',
        name: 'logs',
        component: () => import('@/views/LogsView.vue'),
        meta: { permission: 'AUDIT_READ', title: '日志管理' },
      },
      {
        path: 'operations/:section(overview|alerts|events|maintenance|audits)?',
        name: 'operations',
        redirect: '/logs',
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { public: true, title: '页面不存在' },
  },
]

export function createAppRouter(history: RouterHistory = createWebHistory(import.meta.env.BASE_URL)) {
  const router = createRouter({
    history,
    routes,
  })

  // 跨模块导航取消旧读取与轮询；同模块子页复用实例，保留其在途请求。
  router.afterEach((to, from, failure) => {
    if (!failure && (!to.meta.workspace || to.meta.workspace !== from.meta.workspace)) cancelSessionRequests()
  })

  router.beforeEach(async (to) => {
    document.title = to.meta.title ? `${to.meta.title} · AACV System` : 'AACV System'
    // 在守卫内部取 store：模块加载期不访问 Pinia，避免与路由的初始化顺序冲突。
    const sessionStore = useSessionStore()
    if (to.meta.public) {
      if (to.name === 'login' && (await sessionStore.ensureSession())) {
        return { name: 'overview' }
      }
      return true
    }

    const user = await sessionStore.ensureSession()
    if (!user) {
      if (integrated) { redirectToPortal(to.fullPath, Boolean(sessionStore.lastError)); return false }
      return sessionExpiredTarget(to.fullPath, sessionStore.lastError)
    }
    if (!sessionStore.hasPermission(to.meta.permission)) {
      return { name: 'forbidden' }
    }
    return true
  })

  return router
}

function sessionExpiredTarget(fullPath: string, lastError: string) {
  return {
    name: 'login',
    query: { redirect: fullPath, reason: lastError ? 'unavailable' : undefined },
  }
}

export default createAppRouter()
