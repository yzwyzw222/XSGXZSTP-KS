<script setup lang="ts">
import {
  Bell,
  Collection,
  Connection,
  DataAnalysis,
  Grid,
  Histogram,
  Monitor,
  Operation,
  Share,
  SwitchButton,
  TrendCharts,
  User,
} from '@element-plus/icons-vue'
import { ElButton, ElIcon, ElMessage } from 'element-plus'
import { computed, ref, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { logout, session } from '@/services/session'
import type { Permission } from '@/types/api'

interface MenuItem {
  label: string
  caption: string
  to: string
  icon: Component
  permission?: Permission
}

const route = useRoute()
const router = useRouter()
const brandLogo = '/favicon.svg'
const loggingOut = ref(false)
const allItems: MenuItem[] = [
  { label: '工作台', caption: 'Dashboard', to: '/', icon: Grid },
  { label: '成果目录', caption: 'Catalog', to: '/catalog', icon: Collection, permission: 'CATALOG_READ' },
  { label: '数据源', caption: 'Sources', to: '/sources', icon: DataAnalysis, permission: 'SOURCE_READ' },
  { label: '采集任务', caption: 'Crawler', to: '/crawl', icon: Connection, permission: 'CRAWL_TASK_READ' },
  { label: '数据治理', caption: 'Governance', to: '/governance', icon: Operation, permission: 'GOVERNANCE_READ' },
  { label: '质量指标', caption: 'Quality', to: '/quality', icon: Histogram, permission: 'GOVERNANCE_READ' },
  { label: '知识图谱', caption: 'Network', to: '/graph', icon: Share, permission: 'GRAPH_READ' },
  { label: '统计分析', caption: 'Analytics', to: '/analytics', icon: TrendCharts, permission: 'ANALYTICS_READ' },
  { label: '运行监控', caption: 'Operations', to: '/operations', icon: Monitor, permission: 'OPERATIONS_READ' },
  { label: '用户管理', caption: 'Accounts', to: '/users', icon: User, permission: 'USER_LIST' },
]

const menuItems = computed(() =>
  allItems.filter(
    (item) => !item.permission || session.user?.permissions.includes(item.permission) === true,
  ),
)

function isActive(to: string): boolean {
  return to === '/' ? route.path === '/' : route.path.startsWith(to)
}

async function handleLogout(): Promise<void> {
  loggingOut.value = true
  try {
    await logout()
    await router.replace({ name: 'login' })
  } catch {
    ElMessage.warning('服务端退出请求未完成，本地会话已清除')
    await router.replace({ name: 'login' })
  } finally {
    loggingOut.value = false
  }
}
</script>

<template>
  <div class="business-shell">
    <aside class="business-sidebar">
      <RouterLink class="business-brand" to="/" aria-label="AACV System 工作台">
        <img :src="brandLogo" alt="" />
        <span>
          <strong>Academic<span>Insight</span></strong>
          <small>AACV 学术成果平台</small>
        </span>
      </RouterLink>

      <nav class="business-nav" aria-label="业务导航">
        <RouterLink
          v-for="item in menuItems"
          :key="item.to"
          :to="item.to"
          :class="{ active: isActive(item.to) }"
        >
          <ElIcon class="business-nav-icon" aria-hidden="true">
            <component :is="item.icon" />
          </ElIcon>
          <span class="business-nav-copy">
            <strong>{{ item.label }}</strong>
            <small>{{ item.caption }}</small>
          </span>
        </RouterLink>
      </nav>

      <div class="sidebar-account">
        <span class="account-avatar" aria-hidden="true"><ElIcon><User /></ElIcon></span>
        <span class="account-copy">
          <strong>{{ session.user?.username }}</strong>
          <small>{{ session.user?.roles.join(' · ') }}</small>
        </span>
      </div>
    </aside>

    <main class="business-main">
      <header class="business-topbar">
        <div class="topbar-title">
          <span class="eyebrow">ACADEMIC INTELLIGENCE</span>
          <strong>{{ route.meta.title }}</strong>
        </div>
        <div class="topbar-actions">
          <span class="session-mark"><i aria-hidden="true" />安全会话</span>
          <button class="icon-button" type="button" aria-label="系统通知">
            <ElIcon><Bell /></ElIcon>
            <span class="notification-dot" aria-hidden="true" />
          </button>
          <span class="topbar-account">
            <span class="account-avatar" aria-hidden="true"><ElIcon><User /></ElIcon></span>
            <span>
              <small>Researcher</small>
              <strong>{{ session.user?.username }}</strong>
            </span>
          </span>
          <ElButton
            class="logout-button"
            :icon="SwitchButton"
            :loading="loggingOut"
            text
            aria-label="退出登录"
            @click="handleLogout"
          />
        </div>
      </header>
      <RouterView />
    </main>
  </div>
</template>
