<script setup lang="ts">
import { ElButton } from 'element-plus'
</script>

<template>
  <main class="grid min-h-screen place-items-center bg-background px-6 text-center">
    <div class="max-w-md">
      <p class="select-none text-[clamp(5rem,15vw,8rem)] font-semibold leading-none text-muted-foreground/25" aria-hidden="true">401</p>
      <h1 class="mt-2 text-2xl font-semibold tracking-tight text-foreground">登录会话已过期</h1>
      <p class="mt-3 text-sm leading-relaxed text-muted-foreground">
        为保护业务数据，系统已结束当前会话。重新登录后可继续访问。
      </p>
      <!-- tag="router-link" 让按钮直接渲染为真实链接，避免出现 a 内嵌 button 的无效嵌套 -->
      <ElButton
        tag="router-link"
        :to="{ name: 'login', query: { reason: 'expired' } }"
        type="primary"
        size="large"
        class="mt-7"
      >
        重新登录
      </ElButton>
    </div>
  </main>
</template>
