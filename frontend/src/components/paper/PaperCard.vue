<template>
  <div class="paper-card" @click="$router.push(`/papers/${paper.id}`)">
    <h3 class="paper-card__title">{{ paper.title }}</h3>
    <div class="paper-card__meta">
      <span v-if="paper.year">{{ paper.year }}</span>
      <span v-if="paper.venueName"> · {{ paper.venueName }}</span>
    </div>
    <p class="paper-card__abstract">{{ truncatedAbstract }}</p>
    <div class="paper-card__stats">
      <span v-if="paper.citationCount != null">引用 {{ paper.citationCount }}</span>
      <span v-if="paper.extractionStatus" class="paper-card__status" :class="`paper-card__status--${paper.extractionStatus.toLowerCase()}`">
        {{ statusLabel }}
      </span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { truncate } from '../../utils/formatters.js'

const props = defineProps({
  paper: { type: Object, required: true }
})

const truncatedAbstract = computed(() => truncate(props.paper.abstract, 150))

const statusLabel = computed(() => {
  const map = { PENDING: '待抽取', IN_PROGRESS: '抽取中', COMPLETED: '已完成', FAILED: '失败' }
  return map[props.paper.extractionStatus] || props.paper.extractionStatus
})
</script>

<style lang="scss" scoped>
.paper-card {
  background: var(--card-bg);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: var(--spacing-md);
  cursor: pointer;
  transition: border-color 0.15s;

  &:hover {
    border-color: var(--accent);
  }

  &__title {
    margin: 0 0 var(--spacing-xs);
    font-size: 0.95rem;
    color: var(--text-primary);
    line-height: 1.4;
  }

  &__meta {
    font-size: 0.8rem;
    color: var(--text-tertiary);
    margin-bottom: var(--spacing-xs);
  }

  &__abstract {
    font-size: 0.8rem;
    color: var(--text-secondary);
    line-height: 1.5;
    margin: 0 0 var(--spacing-sm);
  }

  &__stats {
    display: flex;
    gap: var(--spacing-sm);
    font-size: 0.75rem;
    color: var(--text-tertiary);
  }

  &__status {
    padding: 1px 6px;
    border-radius: 3px;

    &--pending { background: color-mix(in srgb, var(--amber) 20%, transparent); color: var(--amber); }
    &--in_progress { background: color-mix(in srgb, var(--accent) 20%, transparent); color: var(--accent); }
    &--completed { background: color-mix(in srgb, var(--green) 20%, transparent); color: var(--green); }
    &--failed { background: color-mix(in srgb, var(--danger) 20%, transparent); color: var(--danger); }
  }
}
</style>
