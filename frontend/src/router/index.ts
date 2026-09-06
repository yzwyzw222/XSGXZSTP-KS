import { createRouter, createWebHistory } from 'vue-router'
import { getCurrentUser } from '../session'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { public: true }
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/RegisterView.vue'),
      meta: { public: true }
    },
    {
      path: '/',
      component: () => import('../views/LayoutView.vue'),
      children: [
        { path: '', redirect: '/graph' },
        { path: 'data', name: 'data', component: () => import('../views/DataView.vue') },
        { path: 'graph', name: 'graph', component: () => import('../views/GraphView.vue') },
        { path: 'analytics', name: 'analytics', component: () => import('../views/AnalyticsView.vue') },
        { path: 'admin', name: 'admin', component: () => import('../views/AdminUsersView.vue') }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach((to) => {
  if (to.meta.public) {
    return true
  }
  return getCurrentUser() !== null ? true : { name: 'login', query: { redirect: to.fullPath } }
})

export default router
