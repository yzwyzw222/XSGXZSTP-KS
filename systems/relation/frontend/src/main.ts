/**
 * 应用入口：创建 Vue 实例并装配插件。
 * - Element Plus：UI 组件库，同时引入其暗色主题变量（配合 index.html 的 html.dark）
 * - style.css：全局设计令牌（深海蓝主题，颜色/间距/圆角全部走 CSS 变量）
 * - router：Vue Router 路由（含登录守卫）
 */
import { createApp } from 'vue'
import ElementPlus, { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import App from './App.vue'
import router from './router'
import './style.css'
import { ApiError, get } from './api/http'
import { clearSession, setCurrentUser, setCsrfToken, type SessionUser } from './session'
import { integrated, redirectToPortal } from './services/portal-auth'

/** 先恢复服务端会话，再安装路由，保证直接打开或刷新受保护页面时守卫能读取登录状态。 */
async function bootstrap() {
  try {
    setCurrentUser(await get<SessionUser>('/api/v1/auth/me'))
    const csrf = await get<{ token: string }>('/api/v1/auth/csrf')
    setCsrfToken(csrf.token)
  } catch (error) {
    clearSession()
    if (!(error instanceof ApiError && error.status === 401)) {
      if (integrated) { redirectToPortal('/', true); return }
      ElMessage.error('暂时无法恢复登录状态，请检查服务后重新登录。')
    }
  }
  createApp(App).use(router).use(ElementPlus).mount('#app')
}

void bootstrap()
