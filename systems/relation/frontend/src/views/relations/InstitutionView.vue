<template>
  <!--
    科研机构页（关系分析板块 3/5）
    ─────────────────────────────────
    以「机构」为中心，两个视图切换：
      机构内部：二部图 机构→作者→论文（三类节点沿用全站配色）+ 作者明细表（论文数/引用热度）
      机构间合作：机构—机构合作图（线宽∝跨机构合著篇数）+ 机构影响力排名（复用 institutionImpact）
    数据源：
      - relationsApi.institutionAuthors(instId)：机构下作者 + 论文数 + 引用热度合计
      - papersApi.list({size:300})：前端按 authors[].institutionId 过滤出该机构的论文（数据量小可行）
      - relationsApi.institutionCollaborations()：机构对 + 跨机构合著篇数
      - analyticsApi.institutionImpact()：机构影响力排名（复用现成接口）
    关键点（沿袭图谱页踩过的坑）：视图切换必须先 cy.elements().remove() 再重建，
    且只 add 两端都在当前节点集里的边，否则悬空边会让整图白屏。
  -->
  <div class="inst-view">
    <div class="toolbar">
      <el-select v-model="selectedInstitutionId" filterable placeholder="选择机构" style="width: 240px"
        @change="onInstitutionChange">
        <el-option v-for="i in institutionOptions" :key="i.id" :label="i.displayName" :value="i.id" />
      </el-select>
      <el-radio-group v-model="viewMode" @change="onModeChange">
        <el-radio-button label="inside">机构内部</el-radio-button>
        <el-radio-button label="collab">机构间合作</el-radio-button>
      </el-radio-group>
      <el-button type="primary" @click="refresh">刷新</el-button>
    </div>

    <div v-if="institutionOptions.length === 0 && !loadingGraph" class="empty-hint">
      暂无机构数据：请先在「数据管理」页创建机构
    </div>

    <!-- 同一个 Cytoscape 容器：两种视图共用，切换时 remove 全部元素后重建 -->
    <div v-else class="main-split">
      <div class="graph-panel">
        <div ref="cyContainer" class="cy-container" v-loading="loadingGraph" />
        <div v-if="!loadingGraph && nodeCount === 0" class="empty-hint">
          {{ viewMode === 'inside' ? '该机构暂无作者与论文数据' : '暂无机构间合作数据：需要一篇论文的署名作者来自不同机构' }}
        </div>
      </div>

      <div class="side-panel">
        <!-- 机构内部：作者明细表 -->
        <template v-if="viewMode === 'inside'">
          <h3 class="panel-title">机构作者明细</h3>
          <el-table :data="instAuthors" v-loading="loadingTable" size="small" empty-text="暂无作者数据">
            <el-table-column prop="authorName" label="作者" min-width="130" />
            <el-table-column prop="paperCount" label="论文数" width="90" sortable />
            <el-table-column prop="totalCitations" label="引用热度" width="100" sortable />
          </el-table>
        </template>
        <!-- 机构间合作：影响力排名图 -->
        <template v-else>
          <h3 class="panel-title">机构影响力排名</h3>
          <div ref="impactChart" class="chart-body" v-loading="loadingImpact" />
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 科研机构脚本：单 Cytoscape 实例 + 双视图重建
 * ─────────────────────────────────────────────
 * 流程：
 *   inside 视图 → 拉 institutionAuthors + 全量论文（前端按机构过滤）→ 建二部图
 *   collab 视图 → 拉 institutionCollaborations + institutionImpact → 建机构间图 + 影响力柱状图
 * 两个视图共用同一个 cy 实例与容器，切换时 cy.elements().remove() 清空再按新数据 add。
 */
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import cytoscape from 'cytoscape'
import {
  analyticsApi, institutionsApi, papersApi, relationsApi,
  type Institution, type InstitutionAuthorItem, type InstitutionCollabItem
} from '../../api'

// ====================================================================
// 状态
// ====================================================================
const cyContainer = ref<HTMLElement | null>(null)
let cy: cytoscape.Core | null = null
const impactChart = ref<HTMLElement | null>(null)
let impactChartInstance: echarts.ECharts | null = null

const institutionOptions = ref<Institution[]>([])
const selectedInstitutionId = ref<number | null>(null)
const viewMode = ref<'inside' | 'collab'>('inside')
const loadingGraph = ref(false)
const loadingTable = ref(false)
const loadingImpact = ref(false)
const nodeCount = ref(0)

/** 机构作者明细（inside 视图） */
const instAuthors = ref<InstitutionAuthorItem[]>([])
/** 机构间合作边表（collab 视图，缓存） */
let instCollabs: InstitutionCollabItem[] = []

// 配色常量（与 style.css 设计令牌一致）
const COLORS = {
  accent: '#2d9df5', green: '#35c98c', amber: '#f5a04b',
  violet: '#a77af2', ink: '#edf6ff', muted: '#8296ae', border: '#1d3657'
}

// ====================================================================
// 数据加载
// ====================================================================

async function loadInstitutionOptions() {
  try {
    const res = await institutionsApi.list({ size: 100 })
    institutionOptions.value = res.items
    if (selectedInstitutionId.value === null && res.items.length > 0) {
      selectedInstitutionId.value = res.items[0].id
    }
  } catch {
    ElMessage.error('加载机构列表失败')
  }
}

/** inside 视图：作者明细 + 该机构的论文（前端过滤，数据量小时可行） */
async function loadInside() {
  if (selectedInstitutionId.value === null) return
  const instId = selectedInstitutionId.value
  loadingGraph.value = true
  loadingTable.value = true
  try {
    instAuthors.value = await relationsApi.institutionAuthors(instId, 200)
    const papers = await papersApi.list({ size: 300 })
    const myPapers = papers.items.filter(p =>
      (p.authors ?? []).some(a => a.institutionId === instId))
    buildBipartiteGraph(myPapers)
  } catch {
    ElMessage.error('加载机构内部数据失败')
  } finally {
    loadingGraph.value = false
    loadingTable.value = false
  }
}

/** collab 视图：机构间合作边表 + 影响力排名 */
async function loadCollab() {
  loadingGraph.value = true
  loadingImpact.value = true
  try {
    instCollabs = await relationsApi.institutionCollaborations(200)
    buildCollabGraph()
    const impact = await analyticsApi.institutionImpact(20)
    await nextTick()
    renderImpactChart(impact)
  } catch {
    ElMessage.error('加载机构间合作数据失败')
  } finally {
    loadingGraph.value = false
    loadingImpact.value = false
  }
}

// ====================================================================
// 图谱构建（Cytoscape，两个视图共用一个实例）
// ====================================================================

/** 二部图：机构 →（AFFILIATED）→ 作者 →（AUTHORED）→ 论文 */
function buildBipartiteGraph(papers: { id: number; title: string; authors: { authorId: number; institutionId: number | null }[] }[]) {
  if (!cy || selectedInstitutionId.value === null) return
  const instId = selectedInstitutionId.value
  const instName = institutionOptions.value.find(i => i.id === instId)?.displayName ?? `机构#${instId}`

  const nodes: cytoscape.ElementDefinition[] = [
    { data: { id: `i${instId}`, type: 'institution', label: instName, size: 34 } }
  ]
  const edges: cytoscape.ElementDefinition[] = []

  // 作者节点（来自 institutionAuthors）+ 机构→作者边
  for (const a of instAuthors.value) {
    nodes.push({ data: { id: `a${a.authorId}`, type: 'author', label: a.authorName, size: 24 } })
    edges.push({ data: { id: `aff_${instId}_${a.authorId}`, source: `i${instId}`, target: `a${a.authorId}`, type: 'AFFILIATED' } })
  }

  // 论文节点 + 作者→论文边（论文已按机构过滤，两端必然都在节点集内）
  for (const p of papers) {
    nodes.push({ data: { id: `p${p.id}`, type: 'paper', label: p.title, size: 18 } })
    for (const a of p.authors) {
      if (a.institutionId === instId) {
        edges.push({ data: { id: `auth_${a.authorId}_${p.id}`, source: `a${a.authorId}`, target: `p${p.id}`, type: 'AUTHORED' } })
      }
    }
  }

  cy.elements().remove()
  cy.add(nodes)
  cy.add(edges)
  nodeCount.value = nodes.length
  cy.layout({ name: 'cose', animate: false, nodeRepulsion: () => 8000, idealEdgeLength: () => 100 } as any).run()
}

/** 机构—机构合作图：线宽∝跨机构合著篇数（最大值归一化到 1~6px） */
function buildCollabGraph() {
  if (!cy) return
  const nodeMap = new Map<string, { id: string; label: string; degree: number }>()
  for (const e of instCollabs) {
    for (const [id, label] of [[e.inst1Id, e.inst1], [e.inst2Id, e.inst2]] as const) {
      const key = `i${id}`
      const node = nodeMap.get(key)
      if (node) node.degree += 1
      else nodeMap.set(key, { id: key, label, degree: 1 })
    }
  }

  const maxCount = instCollabs.reduce((m, e) => Math.max(m, e.paperCount), 1)
  const edges: cytoscape.ElementDefinition[] = instCollabs.map(e => ({
    data: {
      id: `ic_${e.inst1Id}_${e.inst2Id}`,
      source: `i${e.inst1Id}`,
      target: `i${e.inst2Id}`,
      type: 'INST_COLLAB',
      label: String(e.paperCount),
      widthPx: 1 + ((e.paperCount - 1) / (maxCount - 1 || 1)) * 5
    }
  }))
  const nodes: cytoscape.ElementDefinition[] = Array.from(nodeMap.values()).map(n => ({
    data: { id: n.id, type: 'institution', label: n.label, size: 22 + Math.min(n.degree, 6) * 3 }
  }))

  cy.elements().remove()
  cy.add(nodes)
  cy.add(edges)
  nodeCount.value = nodes.length
  cy.layout({ name: 'cose', animate: false, nodeRepulsion: () => 8000, idealEdgeLength: () => 140 } as any).run()
}

// ====================================================================
// 影响力排名图（ECharts，横向堆叠柱状：论文数 + 总被引）
// ====================================================================
function renderImpactChart(data: { institution: string; paperCount: number; totalCitations: number }[]) {
  if (!impactChart.value) return
  if (!impactChartInstance) {
    impactChartInstance = echarts.init(impactChart.value, undefined, { renderer: 'canvas' })
  }
  const names = data.map(d => d.institution)
  impactChartInstance.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['论文数', '总被引'], textStyle: { color: COLORS.muted } },
    grid: { left: 130, right: 20, bottom: 30, top: 40 },
    xAxis: {
      type: 'value',
      axisLabel: { color: COLORS.muted },
      splitLine: { lineStyle: { color: COLORS.border } }
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: names,
      axisLabel: { color: COLORS.ink, fontSize: 10 }
    },
    series: [
      { name: '论文数', type: 'bar', stack: 'total', data: data.map(d => d.paperCount), itemStyle: { color: COLORS.violet } },
      { name: '总被引', type: 'bar', stack: 'total', data: data.map(d => d.totalCitations), itemStyle: { color: COLORS.accent } }
    ]
  }, true)
}

// ====================================================================
// 交互与生命周期
// ====================================================================

/** 视图切换：清空画布后按新模式重建（cy 实例不变，容器不变） */
async function onModeChange() {
  await rebuild()
}

/** 机构切换：仅 inside 视图需要重拉（collab 视图是全局数据） */
async function onInstitutionChange() {
  if (viewMode.value === 'inside') await loadInside()
}

async function rebuild() {
  if (viewMode.value === 'inside') await loadInside()
  else await loadCollab()
}

async function refresh() {
  await rebuild()
}

function handleResize() {
  impactChartInstance?.resize()
}

/** Cytoscape 初始化：节点配色与全站一致（institution=紫 / author=绿 / paper=蓝） */
function initCy() {
  if (!cyContainer.value) return
  cy = cytoscape({
    container: cyContainer.value,
    style: [
      {
        selector: 'node',
        style: {
          label: 'data(label)',
          'text-valign': 'bottom',
          'text-halign': 'center',
          'font-size': '10px',
          color: '#edf6ff',
          'text-max-width': '90px',
          'text-wrap': 'ellipsis',
          'background-color': '#2d9df5',
          width: 'data(size)' as any,
          height: 'data(size)' as any
        }
      },
      { selector: 'node[type="paper"]', style: { 'background-color': '#2d9df5' } },
      { selector: 'node[type="author"]', style: { 'background-color': '#35c98c' } },
      { selector: 'node[type="institution"]', style: { 'background-color': '#a77af2' } },
      // 机构内部：灰色细线带箭头（从属关系有方向）
      {
        selector: 'edge',
        style: {
          'line-color': '#3a5070',
          'target-arrow-color': '#3a5070',
          'target-arrow-shape': 'triangle',
          width: 1,
          'curve-style': 'unbundled-bezier'
        }
      },
      // 机构间合作边：紫色无箭头（合作是无向关系），线宽∝合著篇数，边上标篇数
      {
        selector: 'edge[type="INST_COLLAB"]',
        style: {
          'line-color': '#a77af2',
          width: 'data(widthPx)' as any,
          'target-arrow-shape': 'none',
          'curve-style': 'bezier',
          opacity: 0.85,
          label: 'data(label)',
          'font-size': '8px',
          color: '#cbb7f7',
          'text-rotation': 'autorotate',
          'text-background-color': '#0d1b2a',
          'text-background-opacity': 0.75,
          'text-background-padding': '1px'
        }
      },
      {
        selector: 'node:selected',
        style: { 'border-width': 3, 'border-color': '#57bcff' }
      }
    ],
    layout: { name: 'cose', animate: false },
    wheelSensitivity: 0.3
  })
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  // 先置 loading，防止"暂无机构数据"提示在机构列表到达前闪现
  loadingGraph.value = true
  // 顺序关键：必须先拉机构列表（决定 v-else 分支是否渲染容器），
  // 等 Vue 更新 DOM（nextTick）之后才能拿得到 cyContainer 去初始化 Cytoscape；
  // 反过来写会因容器尚未挂载而提前 return，机构下拉永远加载不出来
  await loadInstitutionOptions()
  await nextTick()
  if (cyContainer.value) {
    initCy()
    await rebuild()
    // 开发模式下暴露实例，便于控制台调试与自动化测试
    if (import.meta.env.DEV) {
      (window as any).__cy = cy
    }
  } else {
    loadingGraph.value = false
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  impactChartInstance?.dispose()
  impactChartInstance = null
  cy?.destroy()
  cy = null
})
</script>

<style scoped>
.inst-view {
  padding: var(--space-4);
}

.toolbar {
  display: flex;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  align-items: center;
  flex-wrap: wrap;
}

/* 左右分栏：左图右表/图；≤900px 上下堆叠 */
.main-split {
  display: flex;
  gap: var(--space-4);
  align-items: flex-start;
}

.graph-panel {
  flex: 1 1 55%;
  min-width: 0;
}

.side-panel {
  flex: 1 1 45%;
  min-width: 0;
  background: var(--paper);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-4);
}

.panel-title {
  margin: 0 0 var(--space-3) 0;
  font-size: 15px;
}

@media (max-width: 900px) {
  .main-split {
    flex-direction: column;
  }

  .graph-panel,
  .side-panel {
    width: 100%;
  }
}

.cy-container {
  height: 460px;
  background: var(--paper-deep);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
}

.chart-body {
  width: 100%;
  height: 380px;
}

.empty-hint {
  text-align: center;
  color: var(--muted);
  padding: var(--space-6);
}
</style>
