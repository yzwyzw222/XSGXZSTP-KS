import {
  createRouter,
  createWebHistory,
  type RouterHistory,
  type RouteRecordRaw,
} from 'vue-router'

import { ensureSession, hasPermission, session } from '@/services/session'
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
        name: 'graph',
        component: () => import('@/views/GraphView.vue'),
        meta: { permission: 'GRAPH_READ', title: '知识图谱' },
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

  router.beforeEach(async (to) => {
    document.title = to.meta.title ? `${to.meta.title} · AACV System` : 'AACV System'
    if (to.meta.public) {
      if (to.name === 'login' && (await ensureSession())) {
        return { name: 'overview' }
      }
      return true
    }

    const user = await ensureSession()
    if (!user) {
      return sessionExpiredTarget(to.fullPath)
    }
    if (!hasPermission(to.meta.permission)) {
      return { name: 'forbidden' }
    }
    return true
  })

  return router
}

function sessionExpiredTarget(fullPath: string) {
  return {
    name: 'login',
    query: { redirect: fullPath, reason: session.lastError ? 'unavailable' : undefined },
  }
}

export default createAppRouter()
