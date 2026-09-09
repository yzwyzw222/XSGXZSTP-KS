<script setup lang="ts">
import { ElButton } from 'element-plus'
</script>

<template>
  <main class="research-surface research-exception grid min-h-screen place-items-center bg-background px-6 text-center">
    <div class="research-exception__panel max-w-md">
      <p class="select-none text-[clamp(5rem,15vw,8rem)] font-semibold leading-none text-muted-foreground/25" aria-hidden="true">403</p>
      <h1 class="mt-2 text-2xl font-semibold tracking-tight text-foreground">当前账号无权访问</h1>
      <p class="mt-3 text-sm leading-relaxed text-muted-foreground">
        页面权限由服务端角色决定。如需访问，请联系管理员调整账号角色。
      </p>
      <!-- tag="router-link" 让按钮直接渲染为真实链接，避免出现 a 内嵌 button 的无效嵌套 -->
      <ElButton tag="router-link" to="/" type="primary" size="large" class="mt-7">返回大屏</ElButton>
    </div>
  </main>
</template>
