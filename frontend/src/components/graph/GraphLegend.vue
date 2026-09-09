<template>
  <div class="graph-legend">
    <h4 class="graph-legend__title">图例</h4>
    <div class="graph-legend__items">
      <div v-for="item in legendItems" :key="item.label" class="graph-legend__item">
        <span class="graph-legend__dot" :class="`graph-legend__dot--${item.key}`" />
        <span class="graph-legend__label">{{ item.label }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const legendItems = computed(() => [
  { key: 'author', label: '学者' },
  { key: 'paper', label: '论文' },
  { key: 'venue', label: '期刊/会议' },
  { key: 'entity', label: '实体' }
])
</script>

<style lang="scss" scoped>
.graph-legend {
  position: absolute;
  bottom: 12px;
  left: 12px;
  background: color-mix(in srgb, var(--sidebar-bg) 90%, transparent);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  padding: var(--spacing-xs) var(--spacing-sm);
  z-index: 10;

  &__title {
    margin: 0 0 4px;
    font-size: 0.75rem;
    color: var(--text-secondary);
  }

  &__items {
    display: flex;
    gap: var(--spacing-sm);
  }

  &__item {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  &__dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    flex-shrink: 0;

    &--author { background-color: var(--graph-author); }
    &--paper { background-color: var(--graph-paper); }
    &--venue { background-color: var(--graph-venue); }
    &--entity { background-color: var(--graph-entity); }
  }

  &__label {
    font-size: 0.7rem;
    color: var(--text-secondary);
  }
}
</style>
