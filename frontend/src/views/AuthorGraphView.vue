<template>
  <div class="author-graph">
    <div class="author-graph__header">
      <el-button text @click="$router.back()">← 返回</el-button>
      <h2 class="author-graph__title">{{ authorName }} — 学术关系图谱</h2>
    </div>

    <div class="author-graph__search">
      <SearchBar v-model="searchName" placeholder="输入学者姓名搜索图谱..." @search="searchGraph" />
    </div>

    <div class="author-graph__canvas">
      <CytoscapeGraph
        ref="graphRef"
        :graph-data="graphData"
        :loading="loading"
        @node-click="onNodeClick"
        @node-limit-reached="onNodeLimitReached"
      >
        <GraphToolbar @fit="graphRef?.fit()" @zoom-in="zoomIn" @zoom-out="zoomOut" @relayout="relayout" />
        <GraphLegend />
      </CytoscapeGraph>
    </div>

    <div class="author-graph__info" v-if="selectedNode">
      <h3>节点详情</h3>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="名称">{{ selectedNode.label }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ selectedNode.nodeType }}</el-descriptions-item>
        <el-descriptions-item v-if="selectedNode.properties" label="属性">
          {{ selectedNode.properties }}
        </el-descriptions-item>
      </el-descriptions>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getAuthorGraph } from '../api/author.js'
import { searchAuthors } from '../api/author.js'
import CytoscapeGraph from '../components/graph/CytoscapeGraph.vue'
import GraphToolbar from '../components/graph/GraphToolbar.vue'
import GraphLegend from '../components/graph/GraphLegend.vue'
import SearchBar from '../components/common/SearchBar.vue'

const props = defineProps({ id: String })

const graphRef = ref(null)
const graphData = ref(null)
const loading = ref(false)
const authorName = ref('')
const searchName = ref('')
const selectedNode = ref(null)

async function loadGraph(authorId) {
  loading.value = true
  try {
    const data = await getAuthorGraph(authorId)
    graphData.value = data
    if (data.authorName) {
      authorName.value = data.authorName
    }
  } catch {
    graphData.value = { nodes: [], edges: [] }
  } finally {
    loading.value = false
  }
}

async function searchGraph() {
  if (!searchName.value.trim()) return
  try {
    const res = await searchAuthors({ keyword: searchName.value, page: 0, size: 1 })
    if (res.items?.length) {
      const author = res.items[0]
      authorName.value = author.name
      await loadGraph(author.id)
    }
  } catch {
    // ignore
  }
}

function onNodeClick(nodeData) {
  selectedNode.value = nodeData
}

function onNodeLimitReached({ total, shown }) {
  ElMessage.warning(`节点数量过多（${total}），仅显示前 ${shown} 个，请缩小搜索范围`)
}

function zoomIn() {
  const cy = graphRef.value?.getCy()
  if (cy) cy.zoom(cy.zoom() * 1.3)
}

function zoomOut() {
  const cy = graphRef.value?.getCy()
  if (cy) cy.zoom(cy.zoom() / 1.3)
}

function relayout() {
  const cy = graphRef.value?.getCy()
  if (cy) cy.layout({ name: 'cose', animate: true, animationDuration: 500 }).run()
}

onMounted(() => {
  if (props.id) {
    loadGraph(props.id)
  }
})

watch(() => props.id, (newId) => {
  if (newId) loadGraph(newId)
})
</script>

<style lang="scss" scoped>
.author-graph {
  display: flex;
  flex-direction: column;
  height: calc(100vh - #{$topbar-height} - var(--spacing-lg) * 2);

  &__header {
    display: flex;
    align-items: center;
    gap: var(--spacing-sm);
    margin-bottom: var(--spacing-sm);
    flex-shrink: 0;
  }

  &__title {
    color: var(--text-primary);
    font-size: 1.1rem;
    margin: 0;
  }

  &__search {
    margin-bottom: var(--spacing-sm);
    flex-shrink: 0;
  }

  &__canvas {
    flex: 1;
    min-height: 0;
  }

  &__info {
    flex-shrink: 0;
    margin-top: var(--spacing-sm);
    background: var(--card-bg);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-md);
    padding: var(--spacing-sm) var(--spacing-md);
    max-height: 200px;
    overflow-y: auto;

    h3 {
      margin: 0 0 var(--spacing-xs);
      font-size: 0.9rem;
      color: var(--text-primary);
    }
  }
}
</style>
