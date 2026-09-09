<script setup lang="ts">
import { ElDrawer, ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'

import AppSidebar from '@/components/business/AppSidebar.vue'
import AppTopbar from '@/components/business/AppTopbar.vue'
import CommandPalette from '@/components/business/CommandPalette.vue'
import { navItems } from '@/config/nav'
import ModuleNavigation from '@/components/business/ModuleNavigation.vue'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const sessionStore = useSessionStore()
const route = useRoute()
const isDashboard = computed(() => route.meta.shell === 'dashboard')

const loggingOut = ref(false)
const paletteOpen = ref(false)
const mobileNavOpen = ref(false)
/** 权限过滤后再分组，避免向无权限用户展示空模块。 */
const menuItems = computed(() =>
  navItems.filter((item) => sessionStore.hasPermission(item.permission)),
)

async function handleLogout(): Promise<void> {
  // 退出只允许一条在途请求，避免重复点击造成重复提示。
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await sessionStore.logout()
    await router.replace({ name: 'login' })
  } catch {
    ElMessage.warning('服务端退出请求未完成，本地会话已清除')
    await router.replace({ name: 'login' })
  } finally {
    loggingOut.value = false
  }
}

function onKeydown(event: KeyboardEvent): void {
  const mod = event.ctrlKey || event.metaKey
  if (mod && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    paletteOpen.value = !paletteOpen.value
  } else if (mod && event.key.toLowerCase() === 'b') {
    event.preventDefault()
    mobileNavOpen.value = !mobileNavOpen.value
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

// 路由变化后关闭移动抽屉
const removeAfterEach = router.afterEach(() => { mobileNavOpen.value = false })
onBeforeUnmount(removeAfterEach)
</script>

<template>
  <div class="app-shell research-surface h-dvh overflow-hidden bg-background">
    <a
      href="#main-content"
      class="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-primary focus:px-4 focus:py-2 focus:text-primary-foreground"
    >
      跳到主内容
    </a>

    <!-- 窄屏抽屉导航 -->
    <ElDrawer
      v-model="mobileNavOpen"
      direction="ltr"
      size="256px"
      :with-header="false"
      class="aacv-drawer aacv-navigation-drawer"
      aria-label="业务导航抽屉"
    >
      <AppSidebar :items="menuItems" :collapsed="false" />
    </ElDrawer>

    <!-- 主区 -->
    <div
      class="flex h-full min-h-0 flex-col overflow-hidden"

    >
      <AppTopbar
        :logging-out="loggingOut"
        @open-sidebar="mobileNavOpen = true"
        @open-palette="paletteOpen = true"
        @logout="handleLogout"
      />
      <ModuleNavigation v-if="!isDashboard" />
      <main id="main-content" tabindex="-1" class="min-h-0 min-w-0 flex-1 overflow-hidden">
        <RouterView v-if="sessionStore.isAuthenticated" v-slot="{ Component }">
          <transition name="page">
            <component :is="Component" />
          </transition>
        </RouterView>
      </main>
    </div>

    <CommandPalette v-model:open="paletteOpen" @logout="handleLogout" />
  </div>
</template>

<style scoped>
/* 旧页立即卸载，新页只做短淡入，路由请求和键盘焦点不等待离场动画。 */
.app-shell__sidebar { z-index: var(--z-sidebar); }
.page-enter-active { transition: opacity var(--duration-fast) var(--ease-standard); }
.page-enter-from {
  opacity: 0;
}
@media (prefers-reduced-motion: reduce) {
  .page-enter-active,
  .page-leave-active {
    transition: none;
  }
}
</style>
