<template>
  <div class="author-detail" v-if="author">
    <div class="author-detail__header">
      <el-button text @click="$router.back()">← 返回</el-button>
    </div>
    <div class="author-detail__profile">
      <div class="author-detail__avatar">{{ author.name?.charAt(0) || '?' }}</div>
      <div>
        <h2 class="author-detail__name">{{ author.name }}</h2>
        <p class="author-detail__affiliation" v-if="author.affiliation">{{ author.affiliation }}</p>
        <div class="author-detail__stats">
          <div class="author-detail__stat" v-if="author.paperCount != null">
            <span class="author-detail__stat-value">{{ author.paperCount }}</span>
            <span class="author-detail__stat-label">论文</span>
          </div>
          <div class="author-detail__stat" v-if="author.citationCount != null">
            <span class="author-detail__stat-value">{{ author.citationCount }}</span>
            <span class="author-detail__stat-label">引用</span>
          </div>
          <div class="author-detail__stat" v-if="author.hIndex != null">
            <span class="author-detail__stat-value">{{ author.hIndex }}</span>
            <span class="author-detail__stat-label">H-index</span>
          </div>
        </div>
      </div>
    </div>
    <div class="author-detail__actions">
      <el-button type="primary" @click="$router.push(`/authors/${id}/graph`)">
        查看学术关系图谱
      </el-button>
    </div>
    <div class="author-detail__section" v-if="author.papers?.length">
      <h3>发表论文</h3>
      <PaperList :papers="author.papers" />
    </div>
  </div>
  <LoadingOverlay :visible="loading" text="加载中..." />
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getAuthor } from '../api/author.js'
import LoadingOverlay from '../components/common/LoadingOverlay.vue'
import PaperList from '../components/paper/PaperList.vue'

const props = defineProps({ id: String })
const author = ref(null)
const loading = ref(true)

onMounted(async () => {
  try {
    author.value = await getAuthor(props.id)
  } finally {
    loading.value = false
  }
})
</script>

<style lang="scss" scoped>
.author-detail {
  max-width: 900px;

  &__header {
    margin-bottom: var(--spacing-md);
  }

  &__profile {
    display: flex;
    gap: var(--spacing-lg);
    margin-bottom: var(--spacing-lg);
  }

  &__avatar {
    width: 72px;
    height: 72px;
    border-radius: 50%;
    background: color-mix(in srgb, var(--accent) 20%, transparent);
    color: var(--accent);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 2rem;
    font-weight: 600;
    flex-shrink: 0;
  }

  &__name {
    color: var(--text-primary);
    font-size: 1.5rem;
    margin: 0 0 4px;
  }

  &__affiliation {
    color: var(--text-tertiary);
    font-size: 0.875rem;
    margin: 0 0 var(--spacing-sm);
  }

  &__stats {
    display: flex;
    gap: var(--spacing-lg);
  }

  &__stat {
    text-align: center;

    &-value {
      display: block;
      font-size: 1.25rem;
      font-weight: 600;
      color: var(--accent);
    }

    &-label {
      font-size: 0.75rem;
      color: var(--text-tertiary);
    }
  }

  &__actions {
    margin-bottom: var(--spacing-lg);
  }

  &__section {
    h3 {
      color: var(--text-primary);
      margin: 0 0 var(--spacing-sm);
    }
  }
}
</style>
