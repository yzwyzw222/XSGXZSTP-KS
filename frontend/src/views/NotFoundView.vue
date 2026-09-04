<script setup lang="ts">
import { ElButton } from 'element-plus'
</script>

<template>
  <main class="state-page">
    <span class="state-code">404</span>
    <span class="eyebrow">PAGE NOT FOUND</span>
    <h1>页面不存在</h1>
    <p>请求的地址没有对应页面，或该入口已经失效。</p>
    <RouterLink to="/"><ElButton type="primary">返回工作台</ElButton></RouterLink>
  </main>
</template>
