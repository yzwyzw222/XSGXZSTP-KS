<template>
  <div class="paper-detail" v-if="paper">
    <div class="paper-detail__header">
      <el-button text @click="$router.back()">← 返回</el-button>
      <h2 class="paper-detail__title">{{ paper.title }}</h2>
    </div>
    <div class="paper-detail__meta">
      <span v-if="paper.year">{{ paper.year }}</span>
      <span v-if="paper.venueName"> · {{ paper.venueName }}</span>
      <span v-if="paper.doi"> · DOI: {{ paper.doi }}</span>
    </div>
    <div class="paper-detail__stats" v-if="paper.citationCount != null">
      引用次数: {{ paper.citationCount }}
    </div>
    <div class="paper-detail__section" v-if="paper.abstract">
      <h3>摘要</h3>
      <p>{{ paper.abstract }}</p>
    </div>
    <div class="paper-detail__section" v-if="paper.authors?.length">
      <h3>作者</h3>
      <div class="paper-detail__authors">
        <el-tag v-for="a in paper.authors" :key="a.id" @click="$router.push(`/authors/${a.id}`)" class="paper-detail__author-tag">
          {{ a.name }}
        </el-tag>
      </div>
    </div>
    <div class="paper-detail__section" v-if="paper.extractedEntities?.length">
      <h3>抽取实体</h3>
      <el-table :data="paper.extractedEntities" stripe>
        <el-table-column prop="entityName" label="实体名称" />
        <el-table-column prop="entityType" label="类型" width="120" />
        <el-table-column prop="confidence" label="置信度" width="100" />
      </el-table>
    </div>
  </div>
  <LoadingOverlay :visible="loading" text="加载中..." />
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getPaper } from '../api/paper.js'
import LoadingOverlay from '../components/common/LoadingOverlay.vue'

const props = defineProps({ id: String })
const paper = ref(null)
const loading = ref(true)

onMounted(async () => {
  try {
    paper.value = await getPaper(props.id)
  } finally {
    loading.value = false
  }
})
</script>

<style lang="scss" scoped>
.paper-detail {
  max-width: 900px;

  &__header {
    margin-bottom: var(--spacing-md);
  }

  &__title {
    color: var(--text-primary);
    font-size: 1.25rem;
    margin: var(--spacing-sm) 0 0;
  }

  &__meta {
    color: var(--text-tertiary);
    font-size: 0.85rem;
    margin-bottom: var(--spacing-xs);
  }

  &__stats {
    color: var(--text-secondary);
    font-size: 0.85rem;
    margin-bottom: var(--spacing-md);
  }

  &__section {
    margin-top: var(--spacing-lg);

    h3 {
      color: var(--text-primary);
      font-size: 1rem;
      margin: 0 0 var(--spacing-sm);
    }

    p {
      color: var(--text-secondary);
      font-size: 0.875rem;
      line-height: 1.7;
    }
  }

  &__authors {
    display: flex;
    flex-wrap: wrap;
    gap: var(--spacing-xs);
  }

  &__author-tag {
    cursor: pointer;
  }
}
</style>
