<template>
  <header class="topbar">
    <div class="topbar-left">
      <span class="topbar-title">{{ pageTitle }}</span>
    </div>
    <div class="topbar-right">
      <el-dropdown trigger="click" @command="handleCommand">
        <span class="topbar-user">
          <el-icon><User /></el-icon>
          <span>{{ session.state.user?.username || '用户' }}</span>
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { User, ArrowDown } from '@element-plus/icons-vue'
import { useSession } from '../../composables/useSession'
import { ElMessage } from 'element-plus'
import { integrated, redirectToPortal } from '../../services/portal-auth.js'

const route = useRoute()
const router = useRouter()
const session = useSession()

const pageTitle = computed(() => route.meta?.title || '')

async function handleCommand(command) {
  if (command === 'logout') {
    try {
      await session.logout()
      if (integrated) redirectToPortal()
      else await router.push('/login')
    } catch { ElMessage.error('退出未完成，请稍后重试。') }
  }
}
</script>

<style scoped>
.topbar {
  position: sticky;
  top: 0;
  height: 78px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background-color: var(--sidebar);
  border-bottom: 1px solid #1e3a5e;
  z-index: 50;
}

.topbar-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--ink);
}

.topbar-user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--muted);
  font-size: 14px;
}

.topbar-user:hover {
  color: var(--ink);
}
</style>
