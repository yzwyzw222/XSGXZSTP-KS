<template>
  <header class="topbar">
    <button class="topbar__toggle" @click="$emit('toggle-sidebar')">
      <span>☰</span>
    </button>
    <div class="topbar__spacer" />
    <div class="topbar__user">
      <span class="topbar__username">{{ user?.username || 'Admin' }}</span>
      <button class="topbar__logout" @click="handleLogout">退出</button>
    </div>
  </header>
</template>

<script setup>
import { useAuth } from '../composables/useAuth.js'
import { useRouter } from 'vue-router'

defineEmits(['toggle-sidebar'])

const { user, logout } = useAuth()
const router = useRouter()

async function handleLogout() {
  await logout()
  router.push('/login')
}
</script>

<style lang="scss" scoped>
.topbar {
  height: $topbar-height;
  background: var(--sidebar-bg);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  padding: 0 var(--spacing-md);
  flex-shrink: 0;

  &__toggle {
    background: none;
    border: none;
    color: var(--text-secondary);
    font-size: 1.25rem;
    cursor: pointer;
    padding: var(--spacing-xs);
    border-radius: var(--radius-sm);

    &:hover {
      background: color-mix(in srgb, var(--text-primary) 5%, transparent);
      color: var(--text-primary);
    }
  }

  &__spacer {
    flex: 1;
  }

  &__user {
    display: flex;
    align-items: center;
    gap: var(--spacing-sm);
  }

  &__username {
    color: var(--text-secondary);
    font-size: 0.875rem;
  }

  &__logout {
    background: none;
    border: 1px solid var(--border-color);
    color: var(--text-secondary);
    padding: 4px 12px;
    border-radius: var(--radius-sm);
    cursor: pointer;
    font-size: 0.8rem;

    &:hover {
      border-color: var(--accent);
      color: var(--accent);
    }
  }
}
</style>
