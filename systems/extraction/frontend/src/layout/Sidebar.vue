<template>
  <aside class="sidebar" :class="{ 'sidebar--collapsed': collapsed }">
    <div class="sidebar__header">
      <h1 class="sidebar__title" v-show="!collapsed">ACV</h1>
      <span class="sidebar__title sidebar__title--icon" v-show="collapsed">A</span>
    </div>
    <nav class="sidebar__nav">
      <router-link
        v-for="item in menuItems"
        :key="item.path"
        :to="item.path"
        class="sidebar__item"
        active-class="sidebar__item--active"
      >
        <span class="sidebar__icon">{{ item.icon }}</span>
        <span class="sidebar__label" v-show="!collapsed">{{ item.label }}</span>
      </router-link>
    </nav>
  </aside>
</template>

<script setup>
defineProps({
  collapsed: Boolean
})

const menuItems = [
  { path: '/papers', label: '论文搜索', icon: '📄' },
  { path: '/authors', label: '学者检索', icon: '👤' },
  { path: '/extraction', label: '实体抽取', icon: '🔬' },
  { path: '/statistics', label: '数据统计', icon: '📊' }
]
</script>

<style lang="scss" scoped>
.sidebar {
  width: $sidebar-width;
  height: 100vh;
  background: var(--sidebar-bg);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease;
  overflow: hidden;
  flex-shrink: 0;

  &--collapsed {
    width: 64px;
  }

  &__header {
    height: $topbar-height;
    display: flex;
    align-items: center;
    justify-content: center;
    border-bottom: 1px solid var(--border-color);
  }

  &__title {
    color: var(--accent);
    font-size: 1.25rem;
    font-weight: 700;
    letter-spacing: 2px;

    &--icon {
      font-size: 1.5rem;
    }
  }

  &__nav {
    flex: 1;
    padding: var(--spacing-sm) 0;
  }

  &__item {
    display: flex;
    align-items: center;
    gap: var(--spacing-sm);
    padding: var(--spacing-sm) var(--spacing-md);
    margin: 2px var(--spacing-xs);
    border-radius: var(--radius-sm);
    color: var(--text-secondary);
    text-decoration: none;
    transition: all 0.15s ease;
    white-space: nowrap;

    &:hover {
      background: color-mix(in srgb, var(--text-primary) 5%, transparent);
      color: var(--text-primary);
    }

    &--active {
      background: rgba($accent, 0.15);
      color: var(--accent);
    }
  }

  &__icon {
    font-size: 1.1rem;
    width: 24px;
    text-align: center;
    flex-shrink: 0;
  }

  &__label {
    font-size: 0.875rem;
  }
}
</style>
