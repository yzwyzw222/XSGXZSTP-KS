<script setup lang="ts">
import { ElButton, ElMessage } from 'element-plus'
import { useFullscreen } from '@vueuse/core'
import { ArrowLeft, GraduationCap, Maximize, Menu, Minimize, Search } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import UserMenu from '@/components/business/UserMenu.vue'

defineProps<{ loggingOut?: boolean }>()
const emit = defineEmits<{ (e: 'open-sidebar'): void; (e: 'open-palette'): void; (e: 'logout'): void }>()
const route = useRoute()
const campusBanner = `${import.meta.env.BASE_URL}images/campus-banner.png`
const { isFullscreen, toggle } = useFullscreen()
async function toggleFullscreen() {
  try { await toggle() }
  catch { ElMessage.warning('浏览器暂不支持全屏，请稍后重试。') }
}
const isHome = computed(() => route.path === '/')
const now = ref(new Date())
const clock = computed(() => now.value.toLocaleString('zh-CN', { hour12: false }))
let timer: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  timer = setInterval(() => { now.value = new Date() }, 1000)
})
onBeforeUnmount(() => { clearInterval(timer) })
</script>

<template>
  <header class="app-topbar research-topbar">
    <img class="research-topbar__campus" :src="campusBanner" alt="" aria-hidden="true" />
    <div class="research-topbar__left">
      <ElButton text circle class="lg:hidden" aria-label="打开导航菜单" @click="emit('open-sidebar')"><Menu :size="20" /></ElButton>
      <RouterLink v-if="!isHome" to="/" class="screen-back" aria-label="返回工作台"><ArrowLeft :size="17" /><span>返回工作台</span></RouterLink>
    </div>
    <RouterLink to="/" class="research-brand" aria-label="学术成果信息采集及可视化系统首页">
      <GraduationCap class="research-brand__icon" :size="30" aria-hidden="true" />
      <span><strong>学术成果信息采集及可视化系统</strong><small>ACADEMIC ACHIEVEMENT VISUALIZATION PLATFORM</small></span>
    </RouterLink>
    <div class="research-topbar__tools">
      <time class="research-clock">{{ clock }}</time>
      <ElButton text circle aria-label="搜索或跳转" @click="emit('open-palette')"><Search :size="18" /></ElButton>
      <ElButton text circle :aria-label="isFullscreen ? '退出全屏' : '全屏'" @click="toggleFullscreen"><component :is="isFullscreen ? Minimize : Maximize" :size="18" /></ElButton>
      <UserMenu :logging-out="loggingOut" @logout="emit('logout')" />
    </div>
  </header>
</template>
