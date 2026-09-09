import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '../composables/useAuth.js'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue'),
    meta: { guest: true }
  },
  {
    path: '/',
    component: () => import('../layout/AppLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/papers' },
      { path: 'papers', name: 'Papers', component: () => import('../views/PaperSearchView.vue') },
      { path: 'papers/:id', name: 'PaperDetail', component: () => import('../views/PaperDetailView.vue'), props: true },
      { path: 'authors', name: 'Authors', component: () => import('../views/AuthorSearchView.vue') },
      { path: 'authors/:id', name: 'AuthorDetail', component: () => import('../views/AuthorDetailView.vue'), props: true },
      { path: 'authors/:id/graph', name: 'AuthorGraph', component: () => import('../views/AuthorGraphView.vue'), props: true },
      { path: 'extraction', name: 'Extraction', component: () => import('../views/ExtractionView.vue') },
      { path: 'statistics', name: 'Statistics', component: () => import('../views/StatisticsView.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const { isAuthenticated } = useAuth()
  if (to.meta.requiresAuth && !isAuthenticated.value) {
    return { name: 'Login' }
  }
  if (to.meta.guest && isAuthenticated.value) {
    return { path: '/' }
  }
})

export default router
