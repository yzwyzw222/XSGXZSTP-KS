<script setup lang="ts">
import { RouterLink } from 'vue-router'

import { Button } from '@/components/ui/button'
</script>

<template>
  <main class="grid min-h-screen place-items-center bg-background px-6 text-center">
    <div class="max-w-md">
      <p class="text-[clamp(6rem,18vw,11rem)] font-bold leading-none text-muted/60 select-none" aria-hidden="true">403</p>
      <span class="eyebrow -mt-4 block">ACCESS DENIED</span>
      <h1 class="mt-3 text-3xl font-semibold tracking-tight text-foreground">当前账号无权访问</h1>
      <p class="mt-3 text-sm leading-relaxed text-muted-foreground">
        页面权限由服务端角色决定。如需访问，请联系管理员调整账号角色。
      </p>
      <RouterLink to="/" class="mt-7 inline-block">
        <Button size="lg">返回工作台</Button>
      </RouterLink>
    </div>
  </main>
</template>
