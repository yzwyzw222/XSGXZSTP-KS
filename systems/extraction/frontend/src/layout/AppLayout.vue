<template>
  <div class="app-layout">
    <Sidebar :collapsed="collapsed" />
    <div class="app-layout__main">
      <Topbar @toggle-sidebar="collapsed = !collapsed" />
      <main class="app-layout__content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import Sidebar from './Sidebar.vue'
import Topbar from './Topbar.vue'

const collapsed = ref(false)
</script>

<style lang="scss" scoped>
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;

  &__main {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  &__content {
    flex: 1;
    overflow-y: auto;
    padding: var(--spacing-lg);
    background: var(--bg);
  }
}
</style>
