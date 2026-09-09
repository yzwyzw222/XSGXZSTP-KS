<script setup lang="ts">
import { ElButton, ElMessage } from 'element-plus'
import { ArrowLeft, GraduationCap, Maximize, Menu, Minimize, Search } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import UserMenu from '@/components/business/UserMenu.vue'

defineProps<{ loggingOut?: boolean }>()
const emit = defineEmits<{ (e: 'open-sidebar'): void; (e: 'open-palette'): void; (e: 'logout'): void }>()
const route = useRoute()
const isDashboard = computed(() => route.meta.shell === 'dashboard')
const now = ref(new Date())
const clock = computed(() => now.value.toLocaleString('zh-CN', { hour12: false }))
const fullscreen = ref(false)
let timer: ReturnType<typeof setInterval> | undefined
function syncFullscreen(): void { fullscreen.value = Boolean(document.fullscreenElement) }
async function toggleFullscreen(): Promise<void> {
  try {
    if (document.fullscreenElement) await document.exitFullscreen()
    else if (document.documentElement.requestFullscreen) await document.documentElement.requestFullscreen()
    else ElMessage.info('当前浏览器不支持全屏显示')
  } catch { ElMessage.warning('未能切换全屏，请重试') }
}
onMounted(() => {
  timer = setInterval(() => { now.value = new Date() }, 1000)
  document.addEventListener('fullscreenchange', syncFullscreen)
  syncFullscreen()
})
onBeforeUnmount(() => { clearInterval(timer); document.removeEventListener('fullscreenchange', syncFullscreen) })
</script>

<template>
  <header class="app-topbar research-topbar">
    <div class="research-topbar__circuit research-topbar__circuit--left" aria-hidden="true" />
    <div class="research-topbar__circuit research-topbar__circuit--right" aria-hidden="true" />
    <div class="research-topbar__left">
      <ElButton text circle class="lg:hidden" aria-label="打开导航菜单" @click="emit('open-sidebar')"><Menu :size="20" /></ElButton>
      <RouterLink v-if="!isDashboard" to="/" class="screen-back" aria-label="返回大屏"><ArrowLeft :size="17" /><span>返回大屏</span></RouterLink>
      <span v-else class="research-topbar__motto">探索学术数据 · 赋能科研创新</span>
    </div>
    <RouterLink to="/" class="research-brand" aria-label="学术成果爬虫及可视化系统首页">
      <GraduationCap class="research-brand__icon" :size="30" aria-hidden="true" />
      <span><strong>学术成果爬虫及可视化系统</strong><small>ACADEMIC ACHIEVEMENT VISUALIZATION PLATFORM</small></span>
    </RouterLink>
    <div class="research-topbar__tools">
      <time class="research-clock">{{ clock }}</time>
      <ElButton text circle aria-label="搜索或跳转" @click="emit('open-palette')"><Search :size="18" /></ElButton>
      <ElButton text circle class="research-fullscreen" :aria-label="fullscreen ? '退出全屏' : '进入全屏'" @click="toggleFullscreen"><component :is="fullscreen ? Minimize : Maximize" :size="18" /></ElButton>
      <UserMenu :logging-out="loggingOut" @logout="emit('logout')" />
    </div>
  </header>
</template>
