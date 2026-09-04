<script setup lang="ts">
import cytoscape, { type Core, type EventObject } from 'cytoscape'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
  vLoading,
} from 'element-plus'

import { toErrorMessage } from '@/services/api'
import { graphApi } from '@/services/business'
import { hasPermission } from '@/services/session'
import type {
  GraphEdge,
  GraphNode,
  GraphNodeType,
  GraphRelationshipType,
  GraphResponse,
  GraphSyncStatus,
} from '@/types/api'
import {
  GRAPH_NODE_LIMIT,
  mergeGraph,
  nodeTarget,
  relationshipLabel,
  toCytoscapeElements,
} from '@/utils/graph'
import { formatDateTime } from '@/utils/format'
import { prefersReducedMotion } from '@/utils/motion'

const nodeTypes: Array<{ value: GraphNodeType; label: string }> = [
  { value: 'ACHIEVEMENT', label: '成果' },
  { value: 'AUTHOR', label: '作者' },
  { value: 'INSTITUTION', label: '机构' },
  { value: 'VENUE', label: '期刊/载体' },
  { value: 'TOPIC', label: '主题' },
]
const relationshipTypes: Array<{ value: GraphRelationshipType; label: string }> = [
  { value: 'AUTHORED', label: '创作' },
  { value: 'AFFILIATED_WITH', label: '隶属' },
  { value: 'PUBLISHED_IN', label: '发表于' },
  { value: 'HAS_TOPIC', label: '主题' },
  { value: 'CITES', label: '引用' },
]

const loading = ref(false)
const errorMessage = ref('')
const graph = ref<GraphResponse | null>(null)
const syncStatus = ref<GraphSyncStatus | null>(null)
const graphContainer = ref<HTMLDivElement | null>(null)
const selectedNodeId = ref('')
const selectedEdgeId = ref('')
const viewMode = ref<'graph' | 'nodes' | 'edges'>('graph')
let cy: Core | null = null
let edgeFlowFrame: number | undefined
let edgeDashOffset = 0
const revealTimers: number[] = []

const filters = reactive({
  centerType: 'ACHIEVEMENT' as GraphNodeType,
  centerId: undefined as number | undefined,
  depth: 1,
  nodeLimit: 100,
  publicationYearFrom: undefined as number | undefined,
  publicationYearTo: undefined as number | undefined,
  nodeTypes: [] as GraphNodeType[],
  relationshipTypes: [] as GraphRelationshipType[],
  achievementTypes: '',
})
const pathQuery = reactive({
  sourceType: 'AUTHOR' as GraphNodeType,
  sourceId: undefined as number | undefined,
  targetType: 'TOPIC' as GraphNodeType,
  targetId: undefined as number | undefined,
  maxHops: 6,
})

const selectedNode = computed<GraphNode | null>(() =>
  graph.value?.nodes.find((node) => node.id === selectedNodeId.value) ?? null,
)
const selectedEdge = computed<GraphEdge | null>(() =>
  graph.value?.edges.find((edge) => edge.id === selectedEdgeId.value) ?? null,
)
const selectedNodeTarget = computed(() => selectedNode.value ? nodeTarget(selectedNode.value) : null)
const propertyRows = computed(() => {
  const properties = selectedNode.value?.properties ?? selectedEdge.value?.properties ?? {}
  return Object.entries(properties).map(([key, value]) => ({
    key,
    value: typeof value === 'string' ? value : JSON.stringify(value),
  }))
})
const canExpandSelected = computed(() => {
  if (!selectedNode.value || (graph.value?.nodes.length ?? 0) >= GRAPH_NODE_LIMIT) {
    return false
  }
  const businessId = Number(selectedNode.value.businessId)
  return Number.isSafeInteger(businessId) && businessId > 0
})
const syncLabel = computed(() => {
  if (syncStatus.value?.rebuildInProgress) return '图投影正在重建'
  if (syncStatus.value && !syncStatus.value.neo4jAvailable) return 'Neo4j 暂不可用'
  if (syncStatus.value?.lagThresholdExceeded) return '图同步存在积压'
  return graph.value?.syncedAt ? `投影于 ${formatDateTime(graph.value.syncedAt)}` : '等待加载图数据'
})

async function loadCenter(): Promise<void> {
  if (!filters.centerId) {
    errorMessage.value = '请输入大于0的中心节点业务ID。'
    return
  }
  await loadGraph(() => graphApi.subgraph({
    centerType: filters.centerType,
    centerId: filters.centerId!,
    depth: filters.depth,
    nodeLimit: filters.nodeLimit,
    relationshipTypes: filters.relationshipTypes.length ? filters.relationshipTypes : undefined,
    nodeTypes: filters.nodeTypes.length ? filters.nodeTypes : undefined,
    publicationYearFrom: filters.publicationYearFrom,
    publicationYearTo: filters.publicationYearTo,
    achievementTypes: splitAchievementTypes(),
  }), false)
}

async function loadPath(): Promise<void> {
  if (!pathQuery.sourceId || !pathQuery.targetId) {
    errorMessage.value = '请输入路径起点和终点的业务ID。'
    return
  }
  await loadGraph(() => graphApi.path({
    sourceType: pathQuery.sourceType,
    sourceId: pathQuery.sourceId!,
    targetType: pathQuery.targetType,
    targetId: pathQuery.targetId!,
    maxHops: pathQuery.maxHops,
  }), false)
}

async function expandSelected(): Promise<void> {
  const node = selectedNode.value
  if (!node || !canExpandSelected.value) return
  await loadGraph(() => graphApi.subgraph({
    centerType: node.type,
    centerId: Number(node.businessId),
    depth: 1,
    nodeLimit: Math.min(filters.nodeLimit, GRAPH_NODE_LIMIT - (graph.value?.nodes.length ?? 0)),
    relationshipTypes: filters.relationshipTypes.length ? filters.relationshipTypes : undefined,
    nodeTypes: filters.nodeTypes.length ? filters.nodeTypes : undefined,
    publicationYearFrom: filters.publicationYearFrom,
    publicationYearTo: filters.publicationYearTo,
    achievementTypes: splitAchievementTypes(),
  }), true)
}

async function loadGraph(request: () => Promise<GraphResponse>, merge: boolean): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await request()
    const existingNodeIds = new Set(graph.value?.nodes.map((node) => node.id) ?? [])
    graph.value = mergeGraph(merge ? graph.value : null, response)
    selectedNodeId.value = response.rootNodeId
    selectedEdgeId.value = ''
    viewMode.value = 'graph'
    await nextTick()
    renderGraph(merge ? response.nodes.filter((node) => !existingNodeIds.has(node.id)).map((node) => node.id) : [])
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function loadSyncStatus(): Promise<void> {
  if (!hasPermission('GRAPH_SYNC_READ')) return
  try {
    syncStatus.value = await graphApi.syncStatus()
  } catch {
    syncStatus.value = null
  }
}

function renderGraph(addedNodeIds: string[] = []): void {
  if (!graphContainer.value || !graph.value || viewMode.value !== 'graph') return
  if (!cy) {
    cy = cytoscape({
      container: graphContainer.value,
      minZoom: 0.25,
      maxZoom: 2.5,
      style: [
        {
          selector: 'node',
          style: {
            'background-color': '#4c695c',
            'border-color': '#bcd8f3',
            'border-width': 2,
            color: '#e9f4ff',
            label: 'data(label)',
            'font-family': 'Segoe UI, Microsoft YaHei, sans-serif',
            'font-size': 11,
            'text-background-color': '#09172a',
            'text-background-opacity': 0.9,
            'text-background-padding': '3px',
            'text-max-width': '110px',
            'text-valign': 'bottom',
            'text-margin-y': 8,
            'text-wrap': 'ellipsis',
            width: 34,
            height: 34,
          },
        },
        { selector: 'node[nodeType = "ACHIEVEMENT"]', style: { 'background-color': '#38a8ff', width: 46, height: 46 } },
        { selector: 'node[nodeType = "AUTHOR"]', style: { 'background-color': '#27b9d5' } },
        { selector: 'node[nodeType = "INSTITUTION"]', style: { 'background-color': '#f5a04b', shape: 'round-rectangle' } },
        { selector: 'node[nodeType = "VENUE"]', style: { 'background-color': '#a77af2', shape: 'diamond' } },
        { selector: 'node[nodeType = "TOPIC"]', style: { 'background-color': '#35c98c', shape: 'hexagon' } },
        {
          selector: 'edge',
          style: {
            'curve-style': 'bezier',
            'line-color': '#4d6b89',
            'target-arrow-color': '#4d6b89',
            'target-arrow-shape': 'triangle',
            label: 'data(label)',
            color: '#8ca3ba',
            'font-size': 8,
            'text-background-color': '#09172a',
            'text-background-opacity': 0.8,
            'text-background-padding': '2px',
            width: 1.3,
          },
        },
        { selector: 'node.is-dimmed', style: { opacity: 0.15 } },
        { selector: 'edge.is-dimmed', style: { opacity: 0.15 } },
        { selector: 'node.is-focused', style: { opacity: 1, 'border-color': '#f4fbff', 'border-width': 3 } },
        { selector: 'edge.is-focused', style: { opacity: 1, width: 3, 'line-color': '#5bc5ff', 'target-arrow-color': '#5bc5ff', 'line-style': 'dashed', 'line-dash-pattern': [7, 5] } },
        { selector: ':selected', style: { 'border-color': '#f4fbff', 'border-width': 4, 'line-color': '#5bc5ff', 'target-arrow-color': '#5bc5ff' } },
      ],
    })
    cy.on('tap', 'node', selectNode)
    cy.on('tap', 'edge', selectEdge)
    cy.on('mouseover', 'node', focusNode)
    cy.on('mouseout', 'node', clearNodeFocus)
  }
  cy.elements().remove()
  cy.add(toCytoscapeElements(graph.value))
  for (const nodeId of addedNodeIds) cy.getElementById(nodeId).style('opacity', 0)
  cy.one('layoutstop', () => revealAddedNodes(addedNodeIds))
  cy.layout({
    name: 'cose',
    animate: !prefersReducedMotion(),
    animationDuration: prefersReducedMotion() ? 0 : 800,
    fit: true,
    padding: 42,
    randomize: true,
    nodeRepulsion: () => 5200,
    idealEdgeLength: () => 82,
    gravity: 0.16,
  }).run()
  const root = cy.getElementById(graph.value.rootNodeId)
  if (root.nonempty()) root.select()
}

function selectNode(event: EventObject): void {
  selectedNodeId.value = event.target.id()
  selectedEdgeId.value = ''
  if (event.target.data('nodeType') === 'INSTITUTION' && cy) {
    cy.animate({
      zoom: Math.min(cy.zoom() * 1.35, 2.1),
      center: { eles: event.target },
    }, {
      duration: prefersReducedMotion() ? 0 : 360,
      easing: 'ease-out-cubic',
    })
  }
}

function selectEdge(event: EventObject): void {
  selectedEdgeId.value = event.target.id()
  selectedNodeId.value = ''
}

function focusNode(event: EventObject): void {
  if (!cy) return
  const node = event.target
  const neighborhood = node.closedNeighborhood().union(node.neighborhood().nodes().closedNeighborhood())
  cy.elements().addClass('is-dimmed').removeClass('is-focused')
  neighborhood.removeClass('is-dimmed').addClass('is-focused')
  startEdgeFlow()
}

function clearNodeFocus(): void {
  cy?.elements().removeClass('is-dimmed is-focused')
  stopEdgeFlow()
}

function startEdgeFlow(): void {
  stopEdgeFlow()
  if (prefersReducedMotion()) return
  const tick = (): void => {
    edgeDashOffset = (edgeDashOffset + 1) % 24
    cy?.edges('.is-focused').style('line-dash-offset', -edgeDashOffset)
    edgeFlowFrame = window.requestAnimationFrame(tick)
  }
  edgeFlowFrame = window.requestAnimationFrame(tick)
}

function stopEdgeFlow(): void {
  if (edgeFlowFrame !== undefined) {
    window.cancelAnimationFrame(edgeFlowFrame)
    edgeFlowFrame = undefined
  }
}

function revealAddedNodes(nodeIds: string[]): void {
  if (!cy || !nodeIds.length) return
  for (const timer of revealTimers.splice(0)) window.clearTimeout(timer)
  nodeIds.forEach((nodeId, index) => {
    const timer = window.setTimeout(() => {
      cy?.getElementById(nodeId).animate(
        { style: { opacity: 1 } },
        { duration: prefersReducedMotion() ? 0 : 200, easing: 'ease-out-cubic' },
      )
    }, prefersReducedMotion() ? 0 : index * 30)
    revealTimers.push(timer)
  })
}

function switchView(mode: 'graph' | 'nodes' | 'edges'): void {
  viewMode.value = mode
  if (mode === 'graph') {
    void nextTick().then(() => renderGraph())
  }
}

function splitAchievementTypes(): string[] | undefined {
  const values = filters.achievementTypes.split(',').map((value) => value.trim()).filter(Boolean)
  return values.length ? [...new Set(values)] : undefined
}

function nodeTypeLabel(type: GraphNodeType): string {
  return nodeTypes.find((item) => item.value === type)?.label ?? type
}

function resizeGraph(): void {
  cy?.resize()
  cy?.fit(undefined, 42)
}

onMounted(() => {
  void loadSyncStatus()
  window.addEventListener('resize', resizeGraph)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeGraph)
  stopEdgeFlow()
  for (const timer of revealTimers.splice(0)) window.clearTimeout(timer)
  cy?.destroy()
  cy = null
})
</script>

<template>
  <section class="page-stack graph-page">
    <header class="page-heading graph-heading">
      <div>
        <span class="eyebrow">KNOWLEDGE GRAPH / LOCAL VIEW</span>
        <h1>知识图谱</h1>
        <p>从一个业务节点出发，查看受限局部关系或查询一条确定性最短路径。图数据来自可重建的 Neo4j 投影，成果与统计仍以 MySQL 为准。</p>
      </div>
      <div class="graph-status" :class="{ warning: syncStatus && (!syncStatus.neo4jAvailable || syncStatus.lagThresholdExceeded || syncStatus.rebuildInProgress) }">
        <span class="eyebrow">GRAPH STATUS</span>
        <strong>{{ syncLabel }}</strong>
        <small v-if="syncStatus">待处理 {{ syncStatus.pendingCount }} · 死信 {{ syncStatus.deadCount }}</small>
      </div>
    </header>

    <div class="filter-panel graph-filter-panel">
      <div class="graph-filter-grid">
        <label><span>中心类型</span><ElSelect v-model="filters.centerType"><ElOption v-for="item in nodeTypes" :key="item.value" :label="item.label" :value="item.value" /></ElSelect></label>
        <label><span>中心业务ID</span><ElInputNumber v-model="filters.centerId" :min="1" :step="1" controls-position="right" /></label>
        <label><span>查询深度</span><ElInputNumber v-model="filters.depth" :min="1" :max="2" controls-position="right" /></label>
        <label><span>本次节点上限</span><ElInputNumber v-model="filters.nodeLimit" :min="1" :max="300" controls-position="right" /></label>
        <label><span>起始年份</span><ElInputNumber v-model="filters.publicationYearFrom" :min="1000" :max="9999" controls-position="right" /></label>
        <label><span>结束年份</span><ElInputNumber v-model="filters.publicationYearTo" :min="1000" :max="9999" controls-position="right" /></label>
        <label><span>节点类型</span><ElSelect v-model="filters.nodeTypes" multiple collapse-tags placeholder="全部"><ElOption v-for="item in nodeTypes" :key="item.value" :label="item.label" :value="item.value" /></ElSelect></label>
        <label><span>关系类型</span><ElSelect v-model="filters.relationshipTypes" multiple collapse-tags placeholder="全部"><ElOption v-for="item in relationshipTypes" :key="item.value" :label="item.label" :value="item.value" /></ElSelect></label>
        <label class="graph-wide-filter"><span>成果类型</span><ElInput v-model="filters.achievementTypes" placeholder="多个类型使用英文逗号分隔" @keyup.enter="loadCenter" /></label>
      </div>
      <div class="filter-footer">
        <span class="meta-line">深度最大2，累计最多300个节点；空类型过滤表示全部。</span>
        <ElButton type="primary" :loading="loading" @click="loadCenter">加载中心子图</ElButton>
      </div>

      <details class="path-query">
        <summary>查询两点间最短路径</summary>
        <div class="path-query-grid">
          <ElSelect v-model="pathQuery.sourceType" aria-label="路径起点类型"><ElOption v-for="item in nodeTypes" :key="item.value" :label="item.label" :value="item.value" /></ElSelect>
          <ElInputNumber v-model="pathQuery.sourceId" :min="1" aria-label="路径起点业务ID" placeholder="起点ID" />
          <span aria-hidden="true">→</span>
          <ElSelect v-model="pathQuery.targetType" aria-label="路径终点类型"><ElOption v-for="item in nodeTypes" :key="item.value" :label="item.label" :value="item.value" /></ElSelect>
          <ElInputNumber v-model="pathQuery.targetId" :min="1" aria-label="路径终点业务ID" placeholder="终点ID" />
          <ElInputNumber v-model="pathQuery.maxHops" :min="1" :max="6" aria-label="最大跳数" />
          <ElButton :loading="loading" @click="loadPath">查询路径</ElButton>
        </div>
      </details>
    </div>

    <ElAlert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
    <ElAlert
      v-if="graph?.truncated"
      :title="graph.narrowingSuggestion || '图结果已达到服务端限制，请缩小过滤范围。'"
      type="warning"
      :closable="false"
      show-icon
    />

    <div v-if="graph" class="graph-workspace" v-loading="loading">
      <div class="graph-stage content-panel">
        <div class="toolbar graph-toolbar">
          <div>
            <strong>{{ graph.nodes.length }} 个节点 · {{ graph.edges.length }} 条关系</strong>
            <span class="meta-line">Trace {{ graph.traceId }}</span>
          </div>
          <ul class="graph-legend" aria-label="节点类型图例">
            <li v-for="item in nodeTypes" :key="item.value" :class="`graph-legend-${item.value.toLowerCase()}`">
              <span aria-hidden="true" />{{ item.label }}
            </li>
          </ul>
          <div class="view-switch" aria-label="图谱视图切换">
            <button :class="{ active: viewMode === 'graph' }" @click="switchView('graph')">图形</button>
            <button :class="{ active: viewMode === 'nodes' }" @click="switchView('nodes')">节点表</button>
            <button :class="{ active: viewMode === 'edges' }" @click="switchView('edges')">关系表</button>
          </div>
        </div>

        <div v-show="viewMode === 'graph'" ref="graphContainer" class="graph-canvas" role="img" :aria-label="`知识图谱，共${graph.nodes.length}个节点和${graph.edges.length}条关系`" />
        <ElTable v-if="viewMode === 'nodes'" :data="graph.nodes" max-height="620" empty-text="暂无节点">
          <ElTableColumn label="类型" width="120"><template #default="{ row }"><ElTag effect="plain">{{ nodeTypeLabel(row.type) }}</ElTag></template></ElTableColumn>
          <ElTableColumn prop="label" label="名称" min-width="260" />
          <ElTableColumn prop="businessId" label="业务ID" min-width="140" />
          <ElTableColumn label="操作" width="100"><template #default="{ row }"><ElButton link type="primary" @click="selectedNodeId = row.id; selectedEdgeId = ''">查看</ElButton></template></ElTableColumn>
        </ElTable>
        <ElTable v-if="viewMode === 'edges'" :data="graph.edges" max-height="620" empty-text="暂无关系">
          <ElTableColumn label="关系" width="120"><template #default="{ row }">{{ relationshipLabel(row.type) }}</template></ElTableColumn>
          <ElTableColumn prop="source" label="起点" min-width="180" />
          <ElTableColumn prop="target" label="终点" min-width="180" />
          <ElTableColumn label="操作" width="100"><template #default="{ row }"><ElButton link type="primary" @click="selectedEdgeId = row.id; selectedNodeId = ''">查看</ElButton></template></ElTableColumn>
        </ElTable>
      </div>

      <aside class="graph-inspector content-panel" aria-live="polite">
        <template v-if="selectedNode">
          <span class="eyebrow">NODE / {{ selectedNode.type }}</span>
          <h2>{{ selectedNode.label }}</h2>
          <p class="graph-business-id">业务ID · {{ selectedNode.businessId }}</p>
          <div class="inspector-actions">
            <ElButton type="primary" plain :disabled="!canExpandSelected" @click="expandSelected">展开一跳</ElButton>
            <RouterLink v-if="selectedNodeTarget" class="text-link" :to="selectedNodeTarget">进入业务详情 →</RouterLink>
          </div>
        </template>
        <template v-else-if="selectedEdge">
          <span class="eyebrow">RELATIONSHIP</span>
          <h2>{{ relationshipLabel(selectedEdge.type) }}</h2>
          <p class="graph-business-id">{{ selectedEdge.source }} → {{ selectedEdge.target }}</p>
        </template>
        <template v-else>
          <span class="eyebrow">INSPECTOR</span>
          <h2>选择图中元素</h2>
          <p class="meta-line">点击节点或关系，查看有限摘要、主动展开或进入对应业务页面。</p>
        </template>

        <dl v-if="selectedNode || selectedEdge" class="property-list">
          <template v-for="item in propertyRows" :key="item.key">
            <dt>{{ item.key }}</dt>
            <dd>{{ item.value }}</dd>
          </template>
        </dl>
        <p v-if="(selectedNode || selectedEdge) && !propertyRows.length" class="meta-line">该元素没有额外摘要属性。</p>
      </aside>
    </div>

    <div v-else class="empty-panel graph-empty">
      <strong>从一个确定的业务节点开始</strong>
      <p>选择节点类型并输入业务ID。这里不会自动请求大范围图谱，也不会绕过服务端的深度和节点上限。</p>
    </div>
  </section>
</template>
