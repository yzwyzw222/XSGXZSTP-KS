<script setup lang="ts">
import { useStorage } from '@vueuse/core'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterView, useRouter } from 'vue-router'

import AppSidebar from '@/components/business/AppSidebar.vue'
import AppTopbar from '@/components/business/AppTopbar.vue'
import CommandPalette from '@/components/business/CommandPalette.vue'
import { Sheet, SheetContent, SheetDescription, SheetTitle } from '@/components/ui/sheet'
import { toast } from '@/components/ui/sonner'
import { TooltipProvider } from '@/components/ui/tooltip'
import { navItems } from '@/config/nav'
import { logout, session } from '@/services/session'

const router = useRouter()
const loggingOut = ref(false)
const paletteOpen = ref(false)
const mobileNavOpen = ref(false)
const collapsed = useStorage('aacv-sidebar-collapsed', false)

const menuItems = computed(() =>
  navItems.filter(
    (item) => !item.permission || session.user?.permissions.includes(item.permission) === true,
  ),
)

async function handleLogout(): Promise<void> {
  loggingOut.value = true
  try {
    await logout()
    await router.replace({ name: 'login' })
  } catch {
    toast.warning('服务端退出请求未完成，本地会话已清除')
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
    collapsed.value = !collapsed.value
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

// 路由变化后关闭移动抽屉
const removeAfterEach = router.afterEach(() => { mobileNavOpen.value = false })
onBeforeUnmount(removeAfterEach)
</script>

<template>
  <TooltipProvider :delay-duration="200">
    <div class="min-h-screen bg-background">
      <a
        href="#main-content"
        class="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-primary focus:px-4 focus:py-2 focus:text-primary-foreground"
      >
        跳到主内容
      </a>

      <!-- 桌面侧栏 -->
      <aside
        class="fixed inset-y-0 left-0 z-40 hidden border-r border-sidebar-border transition-[width] duration-200 lg:block"
        :style="{ width: collapsed ? 'var(--sidebar-width-collapsed)' : 'var(--sidebar-width)' }"
      >
        <AppSidebar :items="menuItems" :collapsed="collapsed" @toggle-collapse="collapsed = !collapsed" />
      </aside>

      <!-- 移动抽屉 -->
      <Sheet v-model:open="mobileNavOpen">
        <SheetContent side="left" class="w-64 p-0 lg:hidden">
          <SheetTitle class="sr-only">业务导航</SheetTitle>
          <SheetDescription class="sr-only">按模块选择可访问的业务页面。</SheetDescription>
          <AppSidebar :items="menuItems" :collapsed="false" />
        </SheetContent>
      </Sheet>

      <!-- 主区 -->
      <div
        class="flex min-h-screen flex-col transition-[padding] duration-200"
        :class="collapsed ? 'lg:pl-16' : 'lg:pl-60'"
      >
        <AppTopbar
          :logging-out="loggingOut"
          @open-sidebar="mobileNavOpen = true"
          @open-palette="paletteOpen = true"
          @logout="handleLogout"
        />
        <main id="main-content" class="min-w-0 flex-1">
          <RouterView v-slot="{ Component }">
            <transition name="page" mode="out-in">
              <component :is="Component" />
            </transition>
          </RouterView>
        </main>
      </div>

      <CommandPalette v-model:open="paletteOpen" @logout="handleLogout" />
    </div>
  </TooltipProvider>
</template>

<style scoped>
.page-enter-active,
.page-leave-active {
  transition: opacity 160ms var(--ease-standard), transform 160ms var(--ease-standard);
}
.page-enter-from {
  opacity: 0;
  transform: translateY(6px);
}
.page-leave-to {
  opacity: 0;
}
@media (prefers-reduced-motion: reduce) {
  .page-enter-active,
  .page-leave-active {
    transition: none;
  }
}
</style>
