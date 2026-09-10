<template>
  <!-- 应用骨架（规范规格）：左侧固定 228px 侧栏 + 右侧内容区（顶部 78px 吸顶顶栏）。
       响应式断点：≤900px 侧栏收成 64px 图标栏；≤680px 侧栏隐藏、顶栏出现汉堡按钮切换 -->
  <div class="layout">
    <aside class="sidebar" :class="{ 'sidebar-mini': mini, 'sidebar-open': mobileOpen }">
      <div class="brand" :title="'学术知识图谱'">{{ mini ? '图' : '学术知识图谱' }}</div>
      <!-- 左侧导航：五个板块（用户指定形态）——
           综合情况（作者学术关系知识图谱，后四板块的关系总和）、发行论文及时间、
           合作者、研究趋向、引用影响。数据管理/权限管理收进顶栏按钮（课程设计要求的
           CRUD 与角色权限入口仍在，只是不再占导航位） -->
      <nav class="nav">
        <router-link class="nav-item" to="/relations/overview" :title="mini ? '综合情况' : ''">
          <span class="nav-icon">◉</span><span class="nav-label">综合情况</span>
        </router-link>
        <router-link class="nav-item" to="/relations/timeline" :title="mini ? '发行论文及时间' : ''">
          <span class="nav-icon">⏱</span><span class="nav-label">发行论文及时间</span>
        </router-link>
        <router-link class="nav-item" to="/relations/coauthors" :title="mini ? '合作者' : ''">
          <span class="nav-icon">⬡</span><span class="nav-label">合作者</span>
        </router-link>
        <router-link class="nav-item" to="/relations/fields" :title="mini ? '研究趋向' : ''">
          <span class="nav-icon">✦</span><span class="nav-label">研究趋向</span>
        </router-link>
        <router-link class="nav-item" to="/relations/citations" :title="mini ? '引用影响' : ''">
          <span class="nav-icon">⇄</span><span class="nav-label">引用影响</span>
        </router-link>
      </nav>
    </aside>
    <div class="main">
      <header class="topbar">
        <div class="left-box">
          <a class="integration-return" href="/" target="_top">← 统一门户</a>
          <!-- ≤680px 时显示汉堡按钮，点击开合侧栏抽屉 -->
          <el-button v-if="narrow" class="menu-btn" text @click="mobileOpen = !mobileOpen">☰</el-button>
          <span class="page-title">{{ title }}</span>
          <!-- 顶栏快捷入口：数据管理（五实体 CRUD，课程设计要求）与权限管理（ADMIN 可见）。
               侧栏只放五个分析板块，系统类功能收在这里 -->
          <el-button size="small" text @click="router.push({ name: 'data' })">数据管理</el-button>
          <el-button v-if="isAdmin && !integrated" size="small" text @click="router.push({ name: 'admin' })">权限管理</el-button>
        </div>
        <div class="user-box">
          <span class="username">{{ user?.displayName ?? '' }}</span>
          <el-button size="small" :loading="loggingOut" @click="onLogout">退出登录</el-button>
        </div>
      </header>
      <!-- 子页面出口：路由切换时渲染对应业务视图 -->
      <main class="content">
        <router-view />
      </main>
    </div>
    <!-- 移动端侧栏展开时的半透明遮罩：点击遮罩收起侧栏 -->
    <div v-if="narrow && mobileOpen" class="mask" @click="mobileOpen = false"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authApi } from '../api'
import { clearSession, getCurrentUser, hasRole } from '../session'
import { ElMessage } from 'element-plus'
import { integrated, redirectToPortal } from '../services/portal-auth'

const route = useRoute()
const router = useRouter()

// 从会话模块读取当前用户与角色（内存态，刷新后由 /auth/me 恢复）
const user = getCurrentUser()
const isAdmin = hasRole('ADMIN')
const title = computed(() => String(route.meta.title ?? ''))
const loggingOut = ref(false)

// ------------------------------------------------------------------
// 响应式断点（规范：1180/900/680/480）：
//  ≤900 → mini 图标侧栏；≤680 → 侧栏彻底隐藏，用汉堡按钮抽屉式展开
// ------------------------------------------------------------------
const narrow = ref(false)
const mini = ref(false)
const mobileOpen = ref(false)
const onResize = () => {
  narrow.value = window.innerWidth <= 680
  mini.value = window.innerWidth <= 900 && window.innerWidth > 680
  if (!narrow.value) mobileOpen.value = false
}
onMounted(() => {
  onResize()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

/** 服务端确认退出后清理页面状态；失败时明确提示并允许重试。 */
async function onLogout() {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await authApi.logout()
    clearSession()
    if (integrated) redirectToPortal()
    else await router.replace({ name: 'login' })
  } catch {
    ElMessage.error('退出未完成，请稍后重试。')
  } finally {
    loggingOut.value = false
  }
}
</script>

<style scoped>
.integration-return { color: #70d8ff; white-space: nowrap; text-decoration: none; }
.layout {
  display: flex;
  height: 100%;
}

/* 侧栏：宽度取设计令牌 --sidebar-width（228px），mini 模式收成 64px */
.sidebar {
  width: var(--sidebar-width);
  flex-shrink: 0;
  background: var(--sidebar);
  padding: var(--space-4) var(--space-3);
  transition: width 0.2s ease;
}

.sidebar-mini {
  width: 64px;
  padding: var(--space-4) var(--space-2);
}

.brand {
  color: var(--ink);
  font-size: 18px;
  font-weight: 600;
  padding: var(--space-2) var(--space-3);
  margin-bottom: var(--space-6);
  white-space: nowrap;
  overflow: hidden;
}

.nav {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  color: var(--muted);
  text-decoration: none;
  padding: var(--space-3);
  border-radius: var(--radius-sm);
  white-space: nowrap;
}

.nav-item.router-link-active {
  color: var(--ink);
  background: var(--accent-dark);
}

/* mini 模式：导航只留图标，品牌名收成单字 */
.sidebar-mini .nav-label {
  display: none;
}

.sidebar-mini .brand {
  text-align: center;
  padding-left: 0;
  padding-right: 0;
}

.nav-icon {
  flex-shrink: 0;
  width: 20px;
  text-align: center;
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

.left-box {
  display: flex;
  align-items: center;
  gap: var(--space-3);
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

/* ≤680px：侧栏脱离文档流，作为抽屉浮在左侧 */
@media (max-width: 680px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 100;
    transform: translateX(-100%);
    transition: transform 0.2s ease;
  }

  .sidebar-open {
    transform: translateX(0);
  }

  .content {
    padding: var(--space-3);
  }

  .topbar {
    padding: 0 var(--space-3);
  }

  .username {
    display: none;
  }
}

.mask {
  position: fixed;
  inset: 0;
  z-index: 99;
  background: rgb(0 0 0 / 45%);
}
</style>
