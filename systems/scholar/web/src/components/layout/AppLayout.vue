<template>
  <div class="app-layout">
    <AppSidebar />
    <div class="app-main">
      <AppTopbar />
      <main class="app-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useSession } from '../../composables/useSession'
import AppSidebar from './AppSidebar.vue'
import AppTopbar from './AppTopbar.vue'

// 刷新工作区后同步当前用户和权限，与服务端 Cookie 会话保持一致。
onMounted(() => useSession().fetchUser())
</script>

<style scoped>
.app-layout {
  display: flex;
  /* 用 100% 撑满 #app（其高度已按缩放比例补偿），避免 100vh 在缩放后视觉变矮 */
  height: 100%;
}

.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-left: 228px;
}

.app-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}
</style>
