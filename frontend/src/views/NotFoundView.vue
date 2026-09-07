<script setup lang="ts">
import { ElButton } from 'element-plus'
</script>

<template>
  <main class="grid min-h-screen place-items-center bg-background px-6 text-center">
    <div class="max-w-md">
      <p class="select-none text-[clamp(5rem,15vw,8rem)] font-semibold leading-none text-muted-foreground/25" aria-hidden="true">404</p>
      <h1 class="mt-2 text-2xl font-semibold tracking-tight text-foreground">页面不存在</h1>
      <p class="mt-3 text-sm leading-relaxed text-muted-foreground">
        请求的地址没有对应页面，或该入口已经失效。
      </p>
      <!-- tag="router-link" 让按钮直接渲染为真实链接，避免出现 a 内嵌 button 的无效嵌套 -->
      <ElButton tag="router-link" to="/" type="primary" size="large" class="mt-7">返回工作台</ElButton>
    </div>
  </main>
</template>
