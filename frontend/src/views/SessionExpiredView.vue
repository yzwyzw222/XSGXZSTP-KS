<script setup lang="ts">
import { ElButton } from 'element-plus'
</script>

<template>
  <main class="state-page">
    <span class="state-code">401</span>
    <span class="eyebrow">SESSION EXPIRED</span>
    <h1>登录会话已过期</h1>
    <p>为保护业务数据，系统已结束当前会话。重新登录后可继续访问。</p>
    <RouterLink :to="{ name: 'login', query: { reason: 'expired' } }">
      <ElButton type="primary">重新登录</ElButton>
    </RouterLink>
  </main>
</template>
