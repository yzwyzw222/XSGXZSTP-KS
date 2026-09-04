<script setup lang="ts">
import { RouterLink } from 'vue-router'

import { Button } from '@/components/ui/button'
</script>

<template>
  <main class="grid min-h-screen place-items-center bg-background px-6 text-center">
    <div class="max-w-md">
      <p class="text-[clamp(6rem,18vw,11rem)] font-bold leading-none text-muted/60 select-none" aria-hidden="true">401</p>
      <span class="eyebrow -mt-4 block">SESSION EXPIRED</span>
      <h1 class="mt-3 text-3xl font-semibold tracking-tight text-foreground">登录会话已过期</h1>
      <p class="mt-3 text-sm leading-relaxed text-muted-foreground">
        为保护业务数据，系统已结束当前会话。重新登录后可继续访问。
      </p>
      <RouterLink :to="{ name: 'login', query: { reason: 'expired' } }" class="mt-7 inline-block">
        <Button size="lg">重新登录</Button>
      </RouterLink>
    </div>
  </main>
</template>
