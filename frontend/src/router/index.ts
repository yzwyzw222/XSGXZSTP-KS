import {
  createRouter,
  createWebHistory,
  type RouterHistory,
  type RouteRecordRaw,
} from 'vue-router'

import { useSessionStore } from '@/stores/session'
import { cancelSessionRequests } from '@/services/http'
import type { Permission } from '@/types/api'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    permission?: Permission
    title?: string
  }
}

export const routes: RouteRecordRaw[] = [
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
  {
    path: '/forbidden',
    name: 'forbidden',
    component: () => import('@/views/ForbiddenView.vue'),
    meta: { public: true, title: '无权访问' },
  },
  {
    path: '/',
    component: () => import('@/layouts/BusinessLayout.vue'),
    children: [
      {
        path: '',
        name: 'overview',
        component: () => import('@/views/OverviewView.vue'),
        meta: { title: '工作台' },
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
        name: 'analytics',
        component: () => import('@/views/AnalyticsView.vue'),
        meta: { permission: 'ANALYTICS_READ', title: '统计分析' },
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/UsersView.vue'),
        meta: { permission: 'USER_LIST', title: '用户管理' },
      },
      {
        path: 'logs',
        name: 'logs',
        component: () => import('@/views/LogsView.vue'),
        meta: { permission: 'AUDIT_READ', title: '日志管理' },
      },
      {
        path: 'operations',
        name: 'operations',
        component: () => import('@/views/OperationsView.vue'),
        meta: { permission: 'OPERATIONS_READ', title: '运行监控' },
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

export function createAppRouter(history: RouterHistory = createWebHistory()) {
  const router = createRouter({
    history,
    routes,
  })

  // 成功导航提交后、目标页面挂载前，取消上个页面的业务读取与轮询请求。
  router.afterEach((_to, _from, failure) => {
    if (!failure) cancelSessionRequests()
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
