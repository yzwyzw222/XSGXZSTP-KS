<script setup lang="ts">
import { ElDrawer, ElMessage } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterView, useRouter } from 'vue-router'

import AppSidebar from '@/components/business/AppSidebar.vue'
import AppTopbar from '@/components/business/AppTopbar.vue'
import CommandPalette from '@/components/business/CommandPalette.vue'
import { navItems } from '@/config/nav'
import { useMotion } from '@/composables/useMotion'
import { usePreferencesStore } from '@/stores/preferences'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const sessionStore = useSessionStore()
const preferences = usePreferencesStore()

const loggingOut = ref(false)
const paletteOpen = ref(false)
const mobileNavOpen = ref(false)
const mainContent = ref<HTMLElement | null>(null)
const { reducedMotion, duration } = useMotion()
let sidebarAnimation: Animation | undefined
let layoutSequence = 0

/** 一次提交新宽度后只平移内容；连续折叠从当前视觉位置接续，不积累动画。 */
watch(() => preferences.sidebarCollapsed, async () => {
  const element = mainContent.value
  if (!element || window.innerWidth < 1024) return
  const sequence = ++layoutSequence
  const before = element.getBoundingClientRect().left
  sidebarAnimation?.cancel()
  await nextTick()
  if (sequence !== layoutSequence || !mainContent.value || reducedMotion.value || !element.animate) return
  const delta = before - element.getBoundingClientRect().left
  sidebarAnimation = element.animate([
    { transform: `translateX(${delta}px)` }, { transform: 'translateX(0)' },
  ], { duration: duration('normal'), easing: 'cubic-bezier(0.16, 1, 0.3, 1)' })
})
watch(reducedMotion, () => sidebarAnimation?.cancel())
onBeforeUnmount(() => { layoutSequence++; sidebarAnimation?.cancel() })

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
    preferences.toggleSidebar()
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

// 路由变化后关闭移动抽屉
const removeAfterEach = router.afterEach(() => { mobileNavOpen.value = false })
onBeforeUnmount(removeAfterEach)
</script>

<template>
  <div class="app-shell h-dvh overflow-hidden bg-background">
    <a
      href="#main-content"
      class="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-primary focus:px-4 focus:py-2 focus:text-primary-foreground"
    >
      跳到主内容
    </a>

    <!-- 桌面侧栏 -->
    <aside
      class="app-shell__sidebar fixed inset-y-0 left-0 hidden border-r border-sidebar-border lg:block"
      :style="{ width: preferences.sidebarCollapsed ? 'var(--sidebar-width-collapsed)' : 'var(--sidebar-width)' }"
    >
      <AppSidebar
        :items="menuItems"
        :collapsed="preferences.sidebarCollapsed"
        @toggle-collapse="preferences.toggleSidebar()"
      />
    </aside>

    <!-- 窄屏抽屉导航 -->
    <ElDrawer
      v-model="mobileNavOpen"
      direction="ltr"
      size="256px"
      :with-header="false"
      class="aacv-drawer aacv-navigation-drawer lg:hidden"
      aria-label="业务导航抽屉"
    >
      <AppSidebar :items="menuItems" :collapsed="false" />
    </ElDrawer>

    <!-- 主区 -->
    <div
      class="flex h-full min-h-0 flex-col overflow-hidden"
      :class="preferences.sidebarCollapsed ? 'lg:pl-16' : 'lg:pl-60'"
    >
      <AppTopbar
        :logging-out="loggingOut"
        @open-sidebar="mobileNavOpen = true"
        @open-palette="paletteOpen = true"
        @logout="handleLogout"
      />
      <main id="main-content" ref="mainContent" tabindex="-1" class="min-h-0 min-w-0 flex-1 overflow-hidden">
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
