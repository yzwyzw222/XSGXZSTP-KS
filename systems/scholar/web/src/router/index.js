import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../components/layout/AppLayout.vue'
import { integrated, redirectToPortal } from '../services/portal-auth.js'
import { useSession } from '../composables/useSession'

const routes = [
  ...(integrated ? [] : [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
  },
  ]),
  {
    path: '/',
    component: AppLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '总览', icon: 'DataBoard' },
      },
      {
        path: 'scholar-graph',
        name: 'ScholarGraph',
        component: () => import('../views/ScholarGraph.vue'),
        meta: { title: '学术成果知识图谱', icon: 'Share' },
      },
      {
        path: 'scholar-profile',
        name: 'ScholarProfile',
        component: () => import('../views/ScholarProfile.vue'),
        meta: { title: '学者画像', icon: 'User' },
      },
      {
        path: 'keyword-search',
        name: 'KeywordSearch',
        component: () => import('../views/KeywordSearch.vue'),
        meta: { title: '关键词检索', icon: 'Search' },
      },
      {
        path: 'author-search',
        name: 'AuthorSearch',
        component: () => import('../views/AuthorSearch.vue'),
        meta: { title: '作者检索', icon: 'Avatar' },
      },
      {
        path: 'institution-search',
        name: 'InstitutionSearch',
        component: () => import('../views/InstitutionSearch.vue'),
        meta: { title: '机构检索', icon: 'OfficeBuilding' },
      },
      {
        path: 'paper-search',
        name: 'PaperSearch',
        component: () => import('../views/PaperSearch.vue'),
        meta: { title: '论文检索', icon: 'Document' },
      },
      {
        path: 'ai-selection',
        name: 'AiSelection',
        component: () => import('../views/AiSelection.vue'),
        meta: { title: 'AI 选文分析', icon: 'MagicStick' },
      },
      {
        path: 'analytics',
        name: 'Analytics',
        component: () => import('../views/Analytics.vue'),
        meta: { title: '科研分析', icon: 'TrendCharts' },
      },

    ],
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach(async to => {
  if (!integrated) return true
  const session = useSession()
  if (session.state.authenticated || await session.fetchUser()) return true
  redirectToPortal(to.fullPath, session.state.unavailable)
  return false
})

export default router
