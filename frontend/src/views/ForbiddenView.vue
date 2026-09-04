<script setup lang="ts">
import { ElButton } from 'element-plus'
</script>

<template>
  <main class="state-page">
    <span class="state-code">403</span>
    <span class="eyebrow">ACCESS DENIED</span>
    <h1>当前账号无权访问</h1>
    <p>页面权限由服务端角色决定。如需访问，请联系管理员调整账号角色。</p>
    <RouterLink to="/"><ElButton type="primary">返回工作台</ElButton></RouterLink>
  </main>
</template>
