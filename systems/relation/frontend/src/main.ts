/**
 * 应用入口：创建 Vue 实例并装配插件。
 * - Element Plus：UI 组件库，同时引入其暗色主题变量（配合 index.html 的 html.dark）
 * - style.css：全局设计令牌（深海蓝主题，颜色/间距/圆角全部走 CSS 变量）
 * - router：Vue Router 路由（含登录守卫）
 */
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import App from './App.vue'
import router from './router'
import './style.css'

createApp(App).use(router).use(ElementPlus).mount('#app')
