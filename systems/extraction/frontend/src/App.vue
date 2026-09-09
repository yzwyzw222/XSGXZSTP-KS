<template>
  <router-view />
  <a class="integration-return" href="/">← 统一门户</a>
</template>

<style scoped>
.integration-return { position: fixed; right: 16px; bottom: 16px; z-index: 999; padding: 8px 14px; border: 1px solid #507eac; border-radius: 6px; background: #102943; color: #e7f3ff; font-size: 13px; text-decoration: none; }
.integration-return:focus-visible { outline: 2px solid #90c9ff; outline-offset: 3px; }
</style>
