<template>
  <div class="graph-page">
    <div class="graph-toolbar">
      <h2 class="page-title">学术成果知识图谱</h2>
      <div class="legend">
        <span class="lg-item lg-paper">论文</span>
        <span class="lg-item lg-author">作者</span>
        <span class="lg-item lg-institution">机构</span>
        <span class="lg-item lg-topic">主题</span>
        <span class="lg-item lg-venue">期刊</span>
      </div>
      <div class="toolbar-actions">
        <el-button size="small" :loading="loading" @click="loadInitial">
          重新加载
        </el-button>
        <el-button size="small" @click="fitGraph">适配视图</el-button>
      </div>
    </div>

    <div class="stats-bar">
      <el-tag type="info" effect="plain">节点 × {{ stats.nodes }}</el-tag>
      <el-tag type="info" effect="plain">关系 × {{ stats.edges }}</el-tag>
      <el-tag type="info" effect="plain">当前显示 {{ currentNodes }} / 库中 {{ totalNodes }}</el-tag>
      <el-tooltip content="点击节点可动态展开其邻居，拖拽/滚轮可缩放" placement="top">
        <span class="hint">💡 交互提示</span>
      </el-tooltip>
    </div>

    <div class="graph-main">
      <div class="graph-container">
        <div ref="containerRef" class="graph-canvas"></div>
        <div v-if="loading" class="graph-loading">
          <el-icon class="is-loading" :size="28"><Loading /></el-icon>
          <span>图谱数据加载中…</span>
        </div>
        <el-empty v-if="!loading && nodes.length === 0" description="暂无图谱数据" />
      </div>

      <aside class="graph-stats-panel" v-loading="statsLoading">
        <div class="panel-header">
          <h3>统计概览</h3>
          <el-button size="small" text :icon="RefreshRight" @click="loadStats" />
        </div>

        <div class="stats-grid">
          <div class="stat-item">
            <span class="stat-label">论文</span>
            <span class="stat-value">{{ graphStats.totalPapers }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">作者</span>
            <span class="stat-value">{{ graphStats.totalAuthors }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">机构</span>
            <span class="stat-value">{{ graphStats.totalInstitutions }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">期刊</span>
            <span class="stat-value">{{ graphStats.totalVenues }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">主题</span>
            <span class="stat-value">{{ graphStats.totalTopics }}</span>
          </div>
        </div>

        <div class="rank-section">
          <h4>Top 作者</h4>
          <ul class="rank-list">
            <li v-for="item in graphStats.topAuthors" :key="item.name">
              <span class="rank-name">{{ item.name }}</span>
              <span class="rank-count">{{ item.count }}</span>
            </li>
          </ul>
        </div>

        <div class="rank-section">
          <h4>Top 主题</h4>
          <ul class="rank-list">
            <li v-for="item in graphStats.topTopics" :key="item.name">
              <span class="rank-name">{{ item.name }}</span>
              <span class="rank-count">{{ item.count }}</span>
            </li>
          </ul>
        </div>

        <div class="rank-section">
          <h4>Top 期刊</h4>
          <ul class="rank-list">
            <li v-for="item in graphStats.topVenues" :key="item.name">
              <span class="rank-name">{{ item.name }}</span>
              <span class="rank-count">{{ item.count }}</span>
            </li>
          </ul>
        </div>

        <div class="rank-section">
          <h4>Top 机构</h4>
          <ul class="rank-list">
            <li v-for="item in graphStats.topInstitutions" :key="item.name">
              <span class="rank-name">{{ item.name }}</span>
              <span class="rank-count">{{ item.count }}</span>
            </li>
          </ul>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, reactive } from 'vue'
import cytoscape from 'cytoscape'
import fcose from 'cytoscape-fcose'
import { Loading, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchInitialGraph, fetchExpandGraph, fetchGraphStats } from '../api/graph'

// 布局扩展需要显式注册，安装依赖本身不会使 fcose 可用。
cytoscape.use(fcose)

const containerRef = ref(null)
let cy = null

const nodes = ref([])
const edges = ref([])
const loading = ref(false)
const totalNodes = ref(0)
const currentNodes = ref(0)
const stats = reactive({ nodes: 0, edges: 0 })

// 统计侧边栏数据
const graphStats = reactive({
  totalPapers: 0,
  totalAuthors: 0,
  totalInstitutions: 0,
  totalVenues: 0,
  totalTopics: 0,
  topAuthors: [],
  topTopics: [],
  topVenues: [],
  topInstitutions: [],
})
const statsLoading = ref(false)

async function loadStats() {
  statsLoading.value = true
  try {
    const res = await fetchGraphStats()
    Object.assign(graphStats, res)
  } catch (e) {
    ElMessage.error('统计加载失败：' + (e.message || '未知错误'))
  } finally {
    statsLoading.value = false
  }
}

// 节点类型 -> 颜色 / 尺寸
const TYPE_STYLE = {
  paper: { color: '#2d9df5', size: 42 },
  author: { color: '#f5a623', size: 34 },
  institution: { color: '#7bb661', size: 30 },
  topic: { color: '#9b7bf0', size: 26 },
  venue: { color: '#e06c9f', size: 26 },
}

function mergeData(res) {
  // 合并节点（去重）与边（去重）
  const nodeMap = new Map()
  nodes.value.forEach((n) => nodeMap.set(n.id, n))
  res.nodes.forEach((n) => nodeMap.set(n.id, n))
  nodes.value = Array.from(nodeMap.values())

  const edgeKey = new Set(edges.value.map((e) => `${e.source}|${e.target}|${e.type}`))
  const newEdges = res.edges.filter((e) => !edgeKey.has(`${e.source}|${e.target}|${e.type}`))
  edges.value = edges.value.concat(newEdges)

  totalNodes.value = res.totalNodes
  currentNodes.value = nodes.value.length
  stats.nodes = res.nodes.length
  stats.edges = res.edges.length
}

function renderGraph() {
  if (!cy) return
  const eles = [
    ...nodes.value.map((n) => ({
      data: { id: n.id, label: n.label, type: n.type, degree: n.degree },
    })),
    ...edges.value.map((e) => ({
      data: { source: e.source, target: e.target, type: e.type },
    })),
  ]
  cy.json({ elements: eles })
  cy.style().update()
  if (nodes.value.length === 0) return
  cy.layout({
    name: 'fcose',
    quality: 'proof',
    animate: false,
    padding: 50,
    nodeSeparation: 120,
    idealEdgeLength: 120,
    nodeRepulsion: 16000,
    gravity: 0.25,
    numIter: 4500,
  }).run()
  fitGraph()
}

function buildStylesheet() {
  return [
    {
      selector: 'node',
      style: {
        'background-color': (ele) => TYPE_STYLE[ele.data('type')]?.color || '#666',
        'width': (ele) => {
          const base = TYPE_STYLE[ele.data('type')]?.size || 26
          const deg = ele.data('degree') || 0
          return base + Math.min(deg, 25) * 1.6
        },
        'height': (ele) => {
          const base = TYPE_STYLE[ele.data('type')]?.size || 26
          const deg = ele.data('degree') || 0
          return base + Math.min(deg, 25) * 1.6
        },
        'shape': (ele) => (ele.data('type') === 'author' ? 'diamond' : 'ellipse'),
        'label': 'data(label)',
        'font-size': 9,
        'text-wrap': 'wrap',
        'text-max-width': 110,
        'min-zoomed-font-size': 8,
        'color': '#e8eef7',
        'text-valign': 'bottom',
        'text-margin-y': 4,
        'text-background-color': '#0a1e38',
        'text-background-opacity': 0.7,
        'text-background-padding': 2,
        'text-background-shape': 'roundrectangle',
        'border-width': 1,
        'border-color': '#ffffff55',
      },
    },
    {
      selector: 'edge',
      style: {
        'width': 1.5,
        'curve-style': 'bezier',
        'target-arrow-shape': 'triangle',
        'target-arrow-color': '#4a5b75',
        'line-color': (ele) => {
          switch (ele.data('type')) {
            case 'authoredBy': return '#f5a62388'
            case 'affiliatedTo': return '#7bb66188'
            case 'publishedIn': return '#e06c9f88'
            case 'hasTopic': return '#9b7bf088'
            default: return '#4a5b75'
          }
        },
        'opacity': 0.7,
      },
    },
    {
      selector: 'node:selected',
      style: { 'border-width': 3, 'border-color': '#ffffff' },
    },
  ]
}

function initCytoscape() {
  cy = cytoscape({
    container: containerRef.value,
    style: buildStylesheet(),
    elements: [],
    wheelSensitivity: 0.2,
    minZoom: 0.1,
    maxZoom: 3,
  })

  cy.on('tap', 'node', async (evt) => {
    const node = evt.target
    const { id, type } = node.data()
    // paper / author / topic 可展开
    if (!['paper', 'author', 'topic'].includes(type)) return
    try {
      const res = await fetchExpandGraph(id)
      if (res.nodes.length) {
        mergeData(res)
        renderGraph()
        ElMessage.success(`已展开「${node.data('label')}」的子网络 +${res.nodes.length} 节点`)
      } else {
        ElMessage.info('该节点没有更多可展开的邻居')
      }
    } catch (e) {
      ElMessage.error('展开失败：' + (e.message || '未知错误'))
    }
  })

  cy.on('tap', (evt) => {
    if (evt.target === cy) fitGraph()
  })
}

function fitGraph() {
  if (cy) cy.fit(undefined, 40)
}

async function loadInitial() {
  loading.value = true
  try {
    const res = await fetchInitialGraph()
    nodes.value = []
    edges.value = []
    mergeData(res)
    renderGraph()
  } catch (e) {
    ElMessage.error('加载失败：' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 窗口尺寸变化时重绘画布并重新适配视图（容器高度随 --app-scale 补偿而变化）
function handleWindowResize() {
  if (cy) {
    cy.resize()
    cy.fit(undefined, 40)
  }
}

onMounted(() => {
  initCytoscape()
  loadInitial()
  loadStats()
  window.addEventListener('resize', handleWindowResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleWindowResize)
  if (cy) {
    cy.destroy()
    cy = null
  }
})
</script>

<style scoped>
.graph-page {
  max-width: 1400px;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--ink);
  margin: 0;
}

.graph-toolbar {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.legend {
  display: flex;
  gap: 14px;
  font-size: 13px;
}

.lg-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--muted);
}

.lg-item::before {
  content: '';
  width: 12px;
  height: 12px;
  border-radius: 50%;
  display: inline-block;
}

.lg-paper::before { background: #2d9df5; }
.lg-author::before { background: #f5a623; }
.lg-institution::before { background: #7bb661; }
.lg-topic::before { background: #9b7bf0; }
.lg-venue::before { background: #e06c9f; }

.toolbar-actions {
  margin-left: auto;
}

.stats-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.hint {
  font-size: 12px;
  color: var(--accent);
  cursor: default;
}

.graph-main {
  display: flex;
  gap: 16px;
  align-items: stretch;
  flex: 1;
  min-height: 0;
}

.graph-container {
  position: relative;
  flex: 1;
  min-height: 480px;
  border: 1px solid #1e3a5e;
  border-radius: 8px;
  background: linear-gradient(180deg, #0d2340 0%, #0a1e38 100%);
  overflow: hidden;
}

.graph-stats-panel {
  width: 300px;
  flex-shrink: 0;
  overflow-y: auto;
  border: 1px solid #1e3a5e;
  border-radius: 8px;
  background-color: var(--paper);
  padding: 16px;
  min-height: 480px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.panel-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--ink);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 4px;
  background-color: var(--bg);
  border: 1px solid #1e3a5e;
  border-radius: 6px;
}

.stat-label {
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 4px;
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--accent);
}

.rank-section {
  margin-bottom: 16px;
}

.rank-section h4 {
  margin: 0 0 6px;
  font-size: 14px;
  color: var(--ink);
  border-left: 3px solid var(--accent);
  padding-left: 8px;
}

.rank-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.rank-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 5px 4px;
  border-bottom: 1px dashed #1e3a5e44;
  font-size: 13px;
}

.rank-name {
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-count {
  color: var(--accent);
  font-weight: 600;
  flex-shrink: 0;
}

.graph-canvas {
  width: 100%;
  height: 100%;
}

.graph-loading {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #8296ae;
  gap: 12px;
  background: rgba(10, 30, 56, 0.5);
}
</style>
