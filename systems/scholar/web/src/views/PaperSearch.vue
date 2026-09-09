<template>
  <div class="paper-search">
    <h2 class="page-title">论文检索</h2>
    <p class="page-desc">
      按论文标题浏览：左侧选择论文，右侧列出该论文包含的研究主题。
      也可以在「科研分析 → 论文相似」中点击节点直达。
    </p>

    <div class="ps-main">
      <aside class="ps-side">
        <el-input v-model="search" placeholder="搜索论文标题" clearable :prefix-icon="Search" />
        <div v-loading="paperLoading" class="paper-list">
          <div
            v-for="item in papers"
            :key="item.id"
            class="paper-item"
            :class="{ active: item.id === selectedId }"
            @click="selectPaper(item)"
          >
            <span class="paper-name">{{ item.title }}</span>
            <span class="paper-meta">
              <span v-if="item.year" class="paper-year">{{ item.year }}</span>
              <span class="paper-count">{{ item.topicCount }}</span>
            </span>
          </div>
          <el-empty
            v-if="!paperLoading && papers.length === 0"
            description="没有匹配的论文"
            :image-size="80"
          />
        </div>
      </aside>

      <section class="ps-content">
        <div class="ps-head">
          <span class="ps-current">
            {{ selected ? (selected.title ? `「${selected.title}」的研究主题` : '请在左侧选择论文') : '请在左侧选择论文' }}
          </span>
          <span v-if="selected" class="ps-sub">
            {{ selected.year ?? '' }}{{ selected.year && selected.topicCount ? ' · ' : '' }}
            {{ selected.topicCount ? `${selected.topicCount} 个主题` : '' }}
          </span>
        </div>

        <div class="topic-card" v-loading="loading">
          <template v-if="!loading && selectedId">
            <div v-if="topics.length > 0" class="topic-tags">
              <el-tag
                v-for="t in topics"
                :key="t.id"
                class="topic-tag"
                effect="dark"
                size="large"
                @click="goKeyword(t)"
              >
                {{ t.name }}
              </el-tag>
              <span class="topic-hint">点击主题可查看该主题下的全部论文</span>
            </div>
            <el-empty
              v-else
              description="该论文暂无主题数据（CNKI 导出文献大多缺失关键词字段）"
            />
          </template>
          <el-empty v-else-if="!loading && !selectedId" description="在左侧选择一篇论文查看主题" />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchPapers, fetchPaperTopics } from '../api/paper'

const route = useRoute()
const router = useRouter()

const search = ref('')
const papers = ref([])
const paperLoading = ref(false)

const selectedId = ref(null)
const selected = ref(null)
const topics = ref([])
const loading = ref(false)

let searchTimer = null
watch(search, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(loadPapers, 300)
})

async function loadPapers() {
  paperLoading.value = true
  try {
    papers.value = await fetchPapers(search.value || undefined)
  } catch (e) {
    ElMessage.error('论文加载失败：' + (e.message || '未知错误'))
  } finally {
    paperLoading.value = false
  }
}

function selectPaper(item) {
  if (selectedId.value === item.id) return
  selectedId.value = item.id
  selected.value = item
  loadTopics()
}

async function loadTopics() {
  if (!selectedId.value) return
  loading.value = true
  try {
    topics.value = await fetchPaperTopics(selectedId.value)
  } catch (e) {
    ElMessage.error('主题加载失败：' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 点击主题 → 跳转关键词检索页，查看该主题下的全部论文
function goKeyword(topic) {
  router.push({
    path: '/keyword-search',
    query: { topicId: topic.id, name: topic.name },
  })
}

// 支持从「论文相似」图点击节点跳转过来（query.paperId / query.name）
onMounted(async () => {
  const paperId = route.query.paperId ? String(route.query.paperId) : ''
  const name = route.query.name ? String(route.query.name) : ''
  if (!paperId && name) {
    search.value = name
  }
  await loadPapers()
  if (paperId) {
    const hit = papers.value.find((p) => p.id === paperId)
    if (hit) {
      selectPaper(hit)
      return
    }
  }
  if (name) {
    const byName = papers.value.find((p) => p.title === name)
    if (byName) {
      selectPaper(byName)
    } else if (papers.value.length === 1) {
      selectPaper(papers.value[0])
    }
  }
})
</script>

<style scoped>
.paper-search {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--ink);
  margin-bottom: 6px;
}

.page-desc {
  color: var(--muted);
  font-size: 13px;
  margin-bottom: 16px;
}

.ps-main {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 16px;
  align-items: stretch;
}

/* 左侧论文面板 */
.ps-side {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background-color: var(--paper);
  border: 1px solid #1e3a5e;
  border-radius: 8px;
  padding: 12px;
}

.paper-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.paper-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  color: var(--ink);
  font-size: 13px;
  border: 1px solid transparent;
}

.paper-item:hover {
  background-color: var(--raised);
}

.paper-item.active {
  background-color: var(--paper-deep);
  border-color: var(--accent);
}

.paper-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.35;
}

.paper-meta {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}

.paper-year {
  font-size: 12px;
  color: var(--muted);
}

.paper-count {
  font-size: 12px;
  color: var(--muted);
  background-color: var(--paper-deep);
  border-radius: 10px;
  padding: 1px 8px;
}

.paper-item.active .paper-count {
  color: var(--accent-bright);
}

/* 右侧主题面板 */
.ps-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ps-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}

.ps-current {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
}

.ps-sub {
  font-size: 12px;
  color: var(--muted);
}

.topic-card {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  background-color: var(--paper);
  border: 1px solid #1e3a5e;
  border-radius: 8px;
  padding: 16px;
}

.topic-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.topic-tag {
  font-size: 14px;
  cursor: pointer;
}

.topic-tag:hover {
  opacity: 0.85;
}

.topic-hint {
  font-size: 12px;
  color: var(--muted);
  align-self: center;
}
</style>
