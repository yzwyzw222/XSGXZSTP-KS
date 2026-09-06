<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="brand">学术知识图谱</div>
      <nav class="nav">
        <router-link class="nav-item" to="/graph">图谱可视化</router-link>
        <router-link class="nav-item" to="/data">数据管理</router-link>
        <router-link class="nav-item" to="/analytics">多维分析</router-link>
        <router-link v-if="isAdmin" class="nav-item" to="/admin">权限管理</router-link>
      </nav>
    </aside>
    <div class="main">
      <header class="topbar">
        <span class="page-title">{{ title }}</span>
        <div class="user-box">
          <span class="username">{{ user?.displayName ?? '' }}</span>
          <el-button size="small" @click="onLogout">退出登录</el-button>
        </div>
      </header>
      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clearSession, getCurrentUser, hasRole } from '../session'

const route = useRoute()
const router = useRouter()
const user = getCurrentUser()
const isAdmin = hasRole('ADMIN')
const title = computed(() => String(route.meta.title ?? ''))

function onLogout() {
  clearSession()
  router.push({ name: 'login' })
}
</script>

<style scoped>
.layout {
  display: flex;
  height: 100%;
}

.sidebar {
  width: var(--sidebar-width);
  flex-shrink: 0;
  background: var(--sidebar);
  padding: var(--space-4) var(--space-3);
}

.brand {
  color: var(--ink);
  font-size: 18px;
  font-weight: 600;
  padding: var(--space-2) var(--space-3);
  margin-bottom: var(--space-6);
}

.nav {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.nav-item {
  color: var(--muted);
  text-decoration: none;
  padding: var(--space-3);
  border-radius: var(--radius-sm);
}

.nav-item.router-link-active {
  color: var(--ink);
  background: var(--accent-dark);
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  height: var(--topbar-height);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-6);
  background: var(--paper);
  border-bottom: 1px solid var(--border);
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--ink);
}

.user-box {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.username {
  color: var(--muted);
}

.content {
  flex: 1;
  overflow: auto;
  padding: var(--space-6);
}
</style>
