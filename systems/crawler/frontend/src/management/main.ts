import { createApp, watch } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import { initTheme } from '@/composables/useTheme'
import ManagementApp from './ManagementApp.vue'
import '@/styles/index.css'

const router = createRouter({ history: createWebHistory('/management/'), routes: [
  { path: '/users', component: () => import('@/views/UsersView.vue'), meta: { permission: 'USER_LIST' } },
  { path: '/users/overview', component: () => import('@/views/UsersView.vue'), props: { section: 'overview' }, meta: { permission: 'USER_LIST' } },
  { path: '/logs', component: () => import('./PlatformLogsView.vue'), meta: { permission: 'AUDIT_READ' } },
  { path: '/audits', component: () => import('@/views/LogsView.vue'), meta: { permission: 'AUDIT_READ' } },
] })
const app = createApp(ManagementApp)
app.use(createPinia())
const session = useSessionStore()
router.beforeEach(async to => {
  const user = await session.ensureSession()
  if (!user) { window.location.replace('/login'); return false }
  return session.hasPermission(to.meta.permission)
})
watch(() => session.expired, expired => { if (expired) window.location.replace('/login') })
initTheme()
app.use(router)
router.afterEach((to, _from, failure) => {
  if (!failure && window.parent !== window) window.parent.postMessage({ type: 'portal-location', path: '/management' + to.fullPath }, window.location.origin)
})
app.mount('#app')
