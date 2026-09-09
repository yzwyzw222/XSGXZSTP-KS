<template>
  <div class="author-list">
    <AuthorCard v-for="author in authors" :key="author.id" :author="author" />
    <el-empty v-if="!authors.length && !loading" description="暂无数据" />
  </div>
</template>

<script setup>
import AuthorCard from './AuthorCard.vue'

defineProps({
  authors: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})
</script>

<style lang="scss" scoped>
.author-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}
</style>
