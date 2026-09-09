import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import App from './App.vue'
import router from './router'
import './styles/global.scss'
import { useAuth } from './composables/useAuth.js'

const app = createApp(App)
app.use(ElementPlus)
// 路由守卫执行前恢复已有 Cookie 会话，直接访问子路径时无需重复登录。
void useAuth().fetchMe().then(() => {
  app.use(router)
  app.mount('#app')
})
