/**
 * 路由配置：
 *  - meta.public 标记的登录/注册页无需会话即可访问；
 *  - 其余页面挂载在 LayoutView（侧栏+顶栏布局）之下，按需懒加载（代码分割）；
 *  - 全局前置守卫只做交互提示：无会话时跳回登录页并记录目标地址。
 *    注意：前端守卫不是安全边界，所有接口的真实权限校验在后端完成。
 */
import { createRouter, createWebHistory } from 'vue-router'
import { getCurrentUser } from '../session'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { public: true, title: '登录' }
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/RegisterView.vue'),
      meta: { public: true, title: '注册' }
    },
    {
      path: '/',
      component: () => import('../views/LayoutView.vue'),
      children: [
        { path: '', redirect: '/relations/overview' },
        // 左侧导航五个板块（用户指定的最终形态）：综合情况 / 发行论文及时间 / 合作者 / 研究趋向 / 引用影响
        { path: 'relations/overview', name: 'overview', component: () => import('../views/relations/OverviewGraphView.vue'), meta: { title: '综合情况' } },
        { path: 'relations/timeline', name: 'timeline', component: () => import('../views/relations/TimelineView.vue'), meta: { title: '发行论文及时间' } },
        { path: 'relations/coauthors', name: 'coauthors', component: () => import('../views/relations/CoauthorNetworkView.vue'), meta: { title: '合作者' } },
        { path: 'relations/fields', name: 'fields', component: () => import('../views/relations/ResearchFieldView.vue'), meta: { title: '研究趋向' } },
        { path: 'relations/citations', name: 'citations', component: () => import('../views/relations/CitationImpactView.vue'), meta: { title: '引用影响' } },
        // 保留但不挂在导航上的页面：从顶栏按钮进入（数据管理/权限管理）或历史直达
        { path: 'data', name: 'data', component: () => import('../views/DataView.vue'), meta: { title: '数据管理' } },
        { path: 'graph', name: 'graph', component: () => import('../views/GraphView.vue'), meta: { title: '图谱可视化' } },
        { path: 'analytics', name: 'analytics', component: () => import('../views/AnalyticsView.vue'), meta: { title: '分析总览' } },
        { path: 'admin', name: 'admin', component: () => import('../views/AdminUsersView.vue'), meta: { title: '权限管理' } },
        { path: 'relations/institutions', name: 'institutions', component: () => import('../views/relations/InstitutionView.vue'), meta: { title: '科研机构' } }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

// 前置守卫：登录/注册直接放行；受保护页面依赖内存会话状态（由登录流程或刷新时 /auth/me 恢复）
router.beforeEach((to) => {
  if (to.meta.public) {
    return true
  }
  return getCurrentUser() !== null ? true : { name: 'login', query: { redirect: to.fullPath } }
})

export default router
