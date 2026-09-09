<template>
  <div class="loading-overlay" v-if="visible">
    <div class="loading-overlay__spinner" />
    <p class="loading-overlay__text" v-if="text">{{ text }}</p>
  </div>
</template>

<script setup>
defineProps({
  visible: { type: Boolean, default: false },
  text: { type: String, default: '' }
})
</script>

<style lang="scss" scoped>
.loading-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--bg) 70%, transparent);
  z-index: 100;

  &__spinner {
    width: 36px;
    height: 36px;
    border: 3px solid var(--border-color);
    border-top-color: var(--accent);
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
  }

  &__text {
    margin-top: var(--spacing-sm);
    color: var(--text-secondary);
    font-size: 0.875rem;
  }
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
