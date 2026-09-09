<template>
  <div class="author-card" @click="$router.push(`/authors/${author.id}`)">
    <div class="author-card__avatar">{{ author.name?.charAt(0) || '?' }}</div>
    <div class="author-card__info">
      <h3 class="author-card__name">{{ author.name }}</h3>
      <p class="author-card__affiliation" v-if="author.affiliation">{{ author.affiliation }}</p>
      <div class="author-card__stats">
        <span v-if="author.paperCount != null">论文 {{ author.paperCount }}</span>
        <span v-if="author.citationCount != null">引用 {{ author.citationCount }}</span>
        <span v-if="author.hIndex != null">H-index {{ author.hIndex }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  author: { type: Object, required: true }
})
</script>

<style lang="scss" scoped>
.author-card {
  display: flex;
  gap: var(--spacing-md);
  background: var(--card-bg);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: var(--spacing-md);
  cursor: pointer;
  transition: border-color 0.15s;

  &:hover {
    border-color: var(--accent);
  }

  &__avatar {
    width: 48px;
    height: 48px;
    border-radius: 50%;
    background: rgba($accent, 0.2);
    color: var(--accent);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 1.2rem;
    font-weight: 600;
    flex-shrink: 0;
  }

  &__info {
    flex: 1;
    min-width: 0;
  }

  &__name {
    margin: 0 0 2px;
    font-size: 0.95rem;
    color: var(--text-primary);
  }

  &__affiliation {
    margin: 0 0 var(--spacing-xs);
    font-size: 0.8rem;
    color: var(--text-tertiary);
  }

  &__stats {
    display: flex;
    gap: var(--spacing-md);
    font-size: 0.75rem;
    color: var(--text-secondary);
  }
}
</style>
