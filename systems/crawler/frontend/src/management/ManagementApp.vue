<script setup lang="ts">
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { useSessionStore } from '@/stores/session'
const session = useSessionStore()
</script>
<template>
  <ElConfigProvider :locale="zhCn">
    <div class="management-shell research-surface">
      <header class="management-header">
        <a class="integration-return" href="/" target="_top">← 统一门户</a>
        <strong>学术智能平台 · 统一管理</strong>
        <nav aria-label="平台管理">
          <RouterLink v-if="session.hasPermission('USER_LIST')" to="/users">用户管理</RouterLink>
          <RouterLink v-if="session.hasPermission('AUDIT_READ')" to="/logs">日志管理</RouterLink>
        </nav>
      </header>
      <main v-if="session.isAuthenticated" class="management-content"><RouterView /></main>
      <p v-else role="status">正在确认管理权限…</p>
    </div>
  </ElConfigProvider>
</template>
<style scoped>
.management-shell { min-height: 100dvh; background: var(--background); color: var(--foreground); }
.management-header { display: flex; align-items: center; gap: 24px; min-height: 64px; padding: 12px 24px; border-bottom: 1px solid var(--border); flex-wrap: wrap; }
.management-header a { color: var(--primary); text-decoration: none; }
.management-header nav { display: flex; gap: 24px; margin-left: auto; }
.management-content { padding: 24px; }
.management-content :deep(.page-stack) { height: auto; overflow: visible; }
@media (max-width: 600px) { .management-header, .management-content { padding: 12px; } .management-header { gap: 12px; } }
</style>
