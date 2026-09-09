import { createPinia } from 'pinia'
import { createApp } from 'vue'

// 样式入口统一控制层叠顺序；组件继续按需具名引入。
import './styles/index.css'

import App from './App.vue'
import { initTheme } from './composables/useTheme'
import router from './router'

initTheme()

const app = createApp(App)
// Pinia 必须先于 Router 安装：路由守卫在首次导航时读取会话 store。
app.use(createPinia())
app.use(router)
app.mount('#app')
