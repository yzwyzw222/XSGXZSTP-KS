<script setup lang="ts">
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { storeToRefs } from 'pinia'
import { watch } from 'vue'
import { RouterView, useRouter } from 'vue-router'

import { useSessionStore } from '@/stores/session'

const router = useRouter()
const sessionStore = useSessionStore()
const { expired } = storeToRefs(sessionStore)

// 业务请求返回 401 时由请求层置位 expired，这里统一跳转到会话过期页。
watch(expired, (value) => {
  if (value && router.currentRoute.value.name !== 'session-expired') {
    void router.replace({ name: 'session-expired' })
  }
})
</script>

<template>
  <!-- 通过 ConfigProvider 下发中文语言包，避免全量注册 Element Plus 带来的体积增长。 -->
  <ElConfigProvider :locale="zhCn">
    <RouterView />
  </ElConfigProvider>
</template>
