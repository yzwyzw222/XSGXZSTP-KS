<template>
  <div class="paper-list">
    <PaperCard v-for="paper in papers" :key="paper.id" :paper="paper" />
    <el-empty v-if="!papers.length && !loading" description="暂无数据" />
  </div>
</template>

<script setup>
import PaperCard from './PaperCard.vue'

defineProps({
  papers: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})
</script>

<style lang="scss" scoped>
.paper-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}
</style>
