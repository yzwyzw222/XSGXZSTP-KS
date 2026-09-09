<template>
  <router-view />
  <a class="integration-return" href="/">← 统一门户</a>
</template>

<style scoped>
.integration-return { position: fixed; right: 16px; bottom: 16px; z-index: 999; padding: 8px 14px; border: 1px solid #507eac; border-radius: 6px; background: #102943; color: #e7f3ff; font-size: 13px; text-decoration: none; }
.integration-return:focus-visible { outline: 2px solid #90c9ff; outline-offset: 3px; }
</style>

<script setup>
import { onMounted, onBeforeUnmount } from 'vue'

// 全局等比例自适应：以 1440px 为设计基准，窗口变窄时整体等比缩小（最小 0.55），无需用户手动缩放
const DESIGN_WIDTH = 1440
const MIN_SCALE = 0.55

function applyScale() {
  const scale = Math.max(MIN_SCALE, Math.min(1, window.innerWidth / DESIGN_WIDTH))
  document.documentElement.style.setProperty('--app-scale', String(scale))
}

onMounted(() => {
  applyScale()
  window.addEventListener('resize', applyScale)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', applyScale)
})
</script>
