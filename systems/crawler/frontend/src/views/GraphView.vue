<script setup lang="ts">
import { ElAlert, ElButton, ElDrawer, ElInput, ElOption, ElSelect } from 'element-plus'
import { ArrowLeft, Circle, Maximize, RefreshCw, Search, Table2 } from 'lucide-vue-next'
import { storeToRefs } from 'pinia'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { DataTable } from '@/components/business'
import GraphCanvas from '@/components/business/GraphCanvas.vue'
import NodeDetail from '@/components/business/graph-overview/NodeDetail.vue'
import type { DataTableColumn } from '@/components/business/types'
import { useGraphStore } from '@/stores/graph'
import { useGraphOverviewStore } from '@/stores/graph-overview'
import type { GraphNode, GraphEdge } from '@/types/api'
import { nodeTarget, reviewStatusLabel, toCytoscapeElements } from '@/utils/graph'
import { type CooperationEvidence } from '@/utils/graph-cooperation'
import { graphDefinitions } from '@/utils/graph-presentation'

const store = useGraphOverviewStore()
const legacyStore = useGraphStore()
const { graph, visible, loading, errorMessage, history, cooperations, focusedCooperation } = storeToRefs(store)
const route = useRoute()
const router = useRouter()
const keyword = computed({ get: () => store.filters.keyword, set: value => { store.filters.keyword = value } })
const nodeType = computed({ get: () => store.filters.nodeType, set: value => { store.filters.nodeType = value } })
const relationship = computed({ get: () => store.filters.relationship, set: value => { store.filters.relationship = value } })
const selectedNodeId = computed(() => store.selection?.kind === 'node' ? store.selection.id : '')
const selectedEdgeId = computed(() => store.selection?.kind === 'edge' ? store.selection.id : '')
const drawer = ref<'detail' | 'nodes' | 'edges' | 'cooperations' | ''>('')
const canvas = ref<InstanceType<typeof GraphCanvas> | null>(null)
const stage = ref<HTMLElement | null>(null)
const menu = ref<HTMLElement | null>(null)
const context = ref<{ kind: 'node' | 'edge'; id: string; x: number; y: number } | null>(null)
const visibleCooperations = computed(() => cooperations.value.filter(item => visible.value?.edges.some(edge => edge.id === item.edge.id)))
const canvasData = computed(() => visible.value ? toCytoscapeElements({ ...visible.value, typeDefinitions: graphDefinitions(visible.value) }, graph.value ?? visible.value) : [])
const label = computed(() => `知识图谱，共${visible.value?.nodes.length ?? 0}个节点和${visible.value?.edges.length ?? 0}条关系`)
const definitions = computed(() => graphDefinitions(graph.value))
const nodeDefinitions = computed(() => definitions.value.filter(type => type.kind === 'NODE'
  && (['AUTHOR', 'ACHIEVEMENT'].includes(type.code) || graph.value?.nodes.some(node => node.type === type.code))))
const edgeDefinitions = computed(() => definitions.value.filter(type => type.kind === 'RELATIONSHIP'
  && (['AUTHORED', 'COAUTHORED'].includes(type.code) || graph.value?.edges.some(edge => edge.type === type.code))))
const selectedNode = computed(() => visible.value?.nodes.find(node => node.id === selectedNodeId.value))
const selectedEdge = computed(() => visible.value?.edges.find(edge => edge.id === selectedEdgeId.value))
const selectedType = computed(() => definitions.value.find(type => selectedNode.value
  ? type.kind === 'NODE' && type.code === selectedNode.value.type : type.kind === 'RELATIONSHIP' && type.code === selectedEdge.value?.type))
const sharedWorks = computed(() => cooperations.value.find(item => item.edge.id === selectedEdgeId.value)?.works ?? [])
const scopeKey = computed(() => `${history.value.at(-1)?.id ?? 'all'}:${store.focusedCooperationId}`)
const positions = computed(() => {
  const item = focusedCooperation.value
  if (!item) return undefined
  return Object.fromEntries([
    [item.source.id, { x: 0, y: 0 }], [item.target.id, { x: 360, y: 0 }],
    ...item.works.map((work, index) => [work.id, { x: 180, y: 180 + index * 160 }]),
  ])
})
const counts = computed(() => ({
  nodes: visible.value?.nodes.length ?? 0, edges: visible.value?.edges.length ?? 0,
  nodeTypes: new Set(visible.value?.nodes.map(node => node.type)).size,
  edgeTypes: new Set(visible.value?.edges.map(edge => edge.type)).size,
}))
const nodeColumns: DataTableColumn<GraphNode>[] = [
  { accessorKey: 'label', header: '名称' },
  { id: 'type', header: '类型', accessorFn: row => definitions.value.find(type => type.kind === 'NODE' && type.code === row.type)?.displayName ?? row.type },
  { accessorKey: 'businessId', header: '业务ID' },
  { id: 'actions', header: '操作', meta: { width: '80px' } },
]
const edgeColumns: DataTableColumn<GraphEdge>[] = [
  { id: 'type', header: '关系', accessorFn: row => definitions.value.find(type => type.kind === 'RELATIONSHIP' && type.code === row.type)?.displayName ?? row.type },
  { id: 'source', header: '起点', accessorFn: row => nodeLabel(row.source) },
  { id: 'target', header: '终点', accessorFn: row => nodeLabel(row.target) },
  { id: 'works', header: '共同作品', accessorFn: row => cooperations.value.find(item => item.edge.id === row.id)?.works.map(work => work.label).join('；') || '--' },
  { id: 'actions', header: '操作', meta: { width: '80px' } },
]
const cooperationColumns: DataTableColumn<CooperationEvidence>[] = [
  { id: 'authors', header: '合作作者', accessorFn: item => `${item.source.label} × ${item.target.label}` },
  { id: 'works', header: '共同创作的作品' },
  { id: 'actions', header: '操作', meta: { width: '115px' } },
]

function nodeLabel(id: string): string { return graph.value?.nodes.find(node => node.id === id)?.label ?? id }
function clearSelection(): void { context.value = null; store.clearSelection() }
function selectNode(id: string): void { context.value = null; store.select('node', id); drawer.value = 'detail' }

/** 合作仍聚焦真实共同作品与双方创作边，切换时复用同一个画布实例。 */
function selectEdge(id: string): void {
  context.value = null
  store.select('edge', id)
  if (cooperations.value.some(item => item.edge.id === id)) {
    store.focusedCooperationId = id
    drawer.value = ''
  } else drawer.value = 'detail'
}
function leaveCooperation(): void { clearSelection() }

/** 页面只协调抽屉和菜单；请求、竞态与历史提交由概览 store 负责。 */
async function enterNode(id: string): Promise<void> {
  const node = graph.value?.nodes.find(item => item.id === id)
  if (!node) return
  drawer.value = ''
  context.value = null
  await store.enterNode(node)
}
async function navigate(index: number): Promise<void> {
  drawer.value = ''
  context.value = null
  await store.goTo(index)
}
async function refresh(): Promise<void> { context.value = null; await store.refresh() }

/** 限制菜单在画布可见范围内，焦点落到可执行操作，支持 Escape 退出。 */
async function openContext(value: NonNullable<typeof context.value>): Promise<void> {
  store.select(value.kind, value.id)
  const stageBounds = stage.value?.getBoundingClientRect()
  const canvasBounds = stage.value?.querySelector('.graph-canvas')?.getBoundingClientRect()
  const offsetX = stageBounds && canvasBounds ? canvasBounds.x - stageBounds.x : 0
  const offsetY = stageBounds && canvasBounds ? canvasBounds.y - stageBounds.y : 0
  context.value = { ...value, x: Math.max(0, Math.min(value.x + offsetX, (stage.value?.clientWidth ?? 360) - 190)),
    y: Math.max(0, Math.min(value.y + offsetY, (stage.value?.clientHeight ?? 360) - 200)) }
  await nextTick()
  menu.value?.querySelector<HTMLButtonElement>('button:not(:disabled)')?.focus()
}
function outsideMenu(event: PointerEvent): void {
  if (context.value && event.target instanceof Node && !menu.value?.contains(event.target)) context.value = null
}
function closeContext(): void { context.value = null; stage.value?.querySelector<HTMLElement>('.graph-canvas')?.focus() }

watch(relationship, value => { if (value === 'COAUTHORED') nodeType.value = '' })
watch([keyword, nodeType, relationship], () => { context.value = null })
onMounted(async () => {
  if (route.query.centerType !== undefined || route.query.centerId !== undefined || legacyStore.pendingQuery) {
    await router.replace({ path: '/graph/explore', query: route.query })
    return
  }
  store.reset()
  document.addEventListener('pointerdown', outsideMenu)
  await refresh()
})
onBeforeUnmount(() => { document.removeEventListener('pointerdown', outsideMenu); store.reset() })
</script>

<template>
  <section class="graph-overview" aria-labelledby="graph-title">
    <header class="graph-heading">
      <h1 id="graph-title"><Circle :size="17" aria-hidden="true" />图谱概览</h1>
    </header>
    <div class="overview-toolbar">
      <ElInput v-model="keyword" class="overview-search" aria-label="搜索当前图谱" placeholder="搜索当前图谱中的名称或 ID" clearable :maxlength="200"><template #suffix><Search :size="16" /></template></ElInput>
      <ElSelect v-model="nodeType" :disabled="relationship === 'COAUTHORED'" aria-label="节点类型" placeholder="全部节点类型" clearable><ElOption v-for="type in nodeDefinitions" :key="type.code" :label="type.displayName" :value="type.code" /></ElSelect>
      <ElSelect v-model="relationship" aria-label="关系类型" placeholder="全部关系类型" clearable><ElOption v-for="type in edgeDefinitions" :key="type.code" :label="type.displayName" :value="type.code" /></ElSelect>
      <div class="overview-actions"><ElButton type="primary" :loading="loading" @click="refresh"><RefreshCw v-if="!loading" :size="15" class="mr-1.5" />刷新图谱</ElButton><ElButton @click="router.push('/graph/settings/nodes')">节点样式</ElButton><ElButton @click="router.push('/graph/settings/edges')">关系样式</ElButton></div>
    </div>
    <p v-if="loading" role="status" class="overview-scope">{{ graph ? '正在更新图谱，当前显示上次成功结果…' : '正在读取图谱…' }}</p>
    <ElAlert v-if="errorMessage" class="overview-notice" type="error" :closable="false" :title="graph ? errorMessage + '；保留上次成功结果' : errorMessage" show-icon />
    <ElAlert v-if="graph?.truncated" class="overview-notice" type="warning" :closable="false" title="当前显示受限网络，可通过高级查询进一步定位作者或作品。" show-icon />
    <p v-if="relationship === 'COAUTHORED'" class="px-4 pb-2 text-xs text-muted-foreground">合作视图同时保留共同作品和双方创作连线，点击合作线可单独查看。</p>
    <div ref="stage" class="overview-stage" :class="{ 'has-cooperation': focusedCooperation }">
      <GraphCanvas ref="canvas" :elements="canvasData" fill compact drilldown layout="network" :label="label" :loading="loading" :scope-key="scopeKey" :positions="positions" :selected-node-id="selectedNodeId" :selected-edge-id="selectedEdgeId" @select-node="selectNode" @select-edge="selectEdge" @double-click-node="enterNode" @clear-selection="leaveCooperation" @context-menu="openContext" @dismiss-context="context = null" />
      <div v-if="context" ref="menu" class="graph-context-menu" role="menu" aria-label="图谱操作" :style="{ left: context.x + 'px', top: context.y + 'px' }" @keydown.esc.stop.prevent="closeContext">
        <button role="menuitem" @click="context.kind === 'node' ? selectNode(context.id) : selectEdge(context.id)">查看详情</button>
        <button v-if="context.kind === 'node'" role="menuitem" @click="enterNode(context.id)">查看两跳子图</button>
        <button v-if="context.kind === 'node'" role="menuitem" disabled title="当前未提供节点编辑接口">编辑节点（暂不可用）</button>
        <button role="menuitem" disabled title="当前未提供删除接口">{{ context.kind === 'node' ? '删除节点' : '删除关系' }}（暂不可用）</button>
      </div>
      <div class="canvas-controls">
        <nav v-if="history.length" class="canvas-history" aria-label="图谱浏览历史"><ElButton text size="small" :disabled="loading" @click="navigate(history.length - 2)"><ArrowLeft :size="15" class="mr-1" />返回上一级</ElButton><ElButton text size="small" :disabled="loading" @click="navigate(-1)">全部</ElButton><span aria-hidden="true">|</span></nav>
        <ElButton text size="small" :disabled="!visibleCooperations.length" @click="drawer = 'cooperations'">合作作品</ElButton><span aria-hidden="true">|</span><ElButton text size="small" :disabled="!counts.nodes" @click="canvas?.fit()"><Maximize :size="15" class="mr-1" />适应画布</ElButton><span aria-hidden="true">|</span><ElButton text size="small" @click="drawer = 'nodes'"><Table2 :size="15" class="mr-1" />节点表</ElButton><ElButton text size="small" @click="drawer = 'edges'">关系表</ElButton>
      </div>
      <dl v-if="graph && !focusedCooperation" class="canvas-statistics" aria-label="当前图谱统计"><div class="statistics-heading">当前视图</div><div><dt>图谱节点数：</dt><dd>{{ counts.nodes }}</dd></div><div><dt>节点类型数：</dt><dd>{{ counts.nodeTypes }}</dd></div><div><dt>关系类型数：</dt><dd>{{ counts.edgeTypes }}</dd></div><div><dt>关系数量：</dt><dd>{{ counts.edges }}</dd></div></dl>
      <section v-if="focusedCooperation" class="cooperation-panel" aria-label="合作作品详情">
        <div class="flex items-center justify-between gap-2"><h2 class="text-sm font-semibold">共同创作</h2><ElButton link type="primary" @click="leaveCooperation">返回完整图谱</ElButton></div>
        <p class="cooperation-authors">{{ focusedCooperation.source.label }} <span>×</span> {{ focusedCooperation.target.label }}</p>
        <p class="text-xs text-muted-foreground">共同作品依据（{{ focusedCooperation.works.length }}）</p>
        <ul class="cooperation-works"><li v-for="work in focusedCooperation.works" :key="work.id"><RouterLink :to="nodeTarget(work) ?? '/catalog'">《{{ work.label }}》</RouterLink><span>{{ focusedCooperation.source.label }}、{{ focusedCooperation.target.label }}共同创作</span></li></ul>
        <p v-if="focusedCooperation.incomplete" role="status" class="text-sm">部分作品依据未完整返回，请通过高级查询核对。</p>
        <p class="text-xs text-muted-foreground">画布保留两位作者、共同作品及双方创作连线。以上依据仅覆盖当前读取的图谱。</p>
      </section>
      <div v-if="!counts.nodes" class="overview-empty" role="status"><p>{{ loading ? '正在读取图谱…' : errorMessage ? '图谱读取失败，请刷新重试' : graph?.nodes.length ? '没有匹配的节点或关系' : '暂无已同步的作者和作品' }}</p><span v-if="!loading && !errorMessage">{{ graph?.nodes.length ? '调整搜索或下拉筛选，查看其他结果。' : '数据完成图投影后，可在这里浏览创作与合作关系。' }}</span></div>
      <ul class="canvas-legend" aria-label="节点类型图例"><li v-for="type in nodeDefinitions" :key="type.code"><span :style="{ backgroundColor: type.color }" />{{ type.displayName }}</li><li class="legend-note">有向连线：创作 · 无向虚线：合作</li></ul>
    </div>
    <footer class="overview-footer"><span>统计仅覆盖当前视图</span><span>合作依据来自当前网络的共同作品</span><span>类型颜色和尺寸沿用已保存配置</span></footer>
    <ElDrawer :model-value="Boolean(drawer)" :title="drawer === 'nodes' ? '节点表' : drawer === 'edges' ? '关系表' : drawer === 'cooperations' ? '合作作品' : '图谱详情'" size="min(720px, 100vw)" @update:model-value="value => { if (!value) drawer = '' }">
      <template v-if="drawer === 'cooperations'">
        <p class="mb-4 text-sm text-muted-foreground">每组合作作者与他们共同创作的作品，可在图中逐组查看。</p>
        <DataTable :columns="cooperationColumns" :data="visibleCooperations" :get-row-id="item => item.edge.id" empty-text="当前筛选没有合作关系">
          <template #cell-works="{ row }"><ul class="grid gap-2"><li v-for="work in row.works" :key="work.id">{{ work.label }}</li></ul><span v-if="row.incomplete" class="text-xs text-muted-foreground">部分依据未返回</span></template>
          <template #cell-actions="{ row }"><ElButton link type="primary" @click="selectEdge(row.edge.id)">在图中查看</ElButton></template>
        </DataTable>
      </template>
      <DataTable v-else-if="drawer === 'nodes'" :columns="nodeColumns" :data="visible?.nodes ?? []" :get-row-id="row => row.id" empty-text="暂无匹配节点"><template #cell-actions="{ row }"><ElButton link type="primary" @click="selectNode(row.id)">详情</ElButton></template></DataTable>
      <DataTable v-else-if="drawer === 'edges'" :columns="edgeColumns" :data="visible?.edges ?? []" :get-row-id="row => row.id" empty-text="暂无匹配关系"><template #cell-actions="{ row }"><ElButton link type="primary" @click="selectEdge(row.id)">详情</ElButton></template></DataTable>
      <div v-else-if="drawer === 'detail'" class="grid gap-4 text-sm">
        <template v-if="selectedNode || selectedEdge">
          <h2 class="text-base font-semibold">{{ selectedNode?.label ?? `${nodeLabel(selectedEdge!.source)} — ${nodeLabel(selectedEdge!.target)}` }}</h2>
          <p>类型：{{ selectedType?.displayName ?? '--' }}</p><p>类型审核：{{ reviewStatusLabel(selectedType?.reviewStatus) }} · {{ selectedType?.size ?? '--' }} px</p>
          <template v-if="selectedEdge?.type === 'COAUTHORED'"><p>合作关系由当前图谱中的共同作品推导，不代表全库合作总量。</p><h3 class="font-medium">共同作品依据（{{ sharedWorks.length }}）</h3><ul class="grid gap-2"><li v-for="work in sharedWorks" :key="work.id"><RouterLink :to="nodeTarget(work) ?? '/catalog'" class="text-primary">{{ work.label }}</RouterLink></li></ul></template>
          <NodeDetail v-if="selectedNode" :node="selectedNode" :loading="loading" @explore="enterNode(selectedNode.id)" />
          <template v-if="selectedEdge"><p>关系ID：{{ selectedEdge.id }}</p><ElButton disabled title="当前未提供关系删除接口">删除关系（暂不可用）</ElButton></template>
        </template><p v-else>选择节点或关系，查看类型配置与作品依据。</p>
      </div>
    </ElDrawer>
  </section>
</template>

<style scoped>
.overview-scope { padding: 0 16px 8px; font-size: 12px; color: hsl(var(--muted-foreground)); }
.statistics-heading { font-weight: 600; padding-bottom: 4px; }
.graph-context-menu { position: absolute; z-index: 3; width: 188px; padding: 4px; border: 1px solid hsl(var(--border)); border-radius: 6px; background: hsl(var(--popover)); box-shadow: var(--shadow-md); display: grid; }
.graph-context-menu button { text-align: left; padding: 8px 10px; font-size: 13px; border-radius: 4px; }
.graph-context-menu button:focus-visible, .graph-context-menu button:hover:not(:disabled) { outline: none; background: hsl(var(--accent)); }
.graph-context-menu button:disabled { color: hsl(var(--muted-foreground)); cursor: not-allowed; }
.graph-overview { margin: 12px; height: calc(100% - 24px); min-height: 0; display: flex; flex-direction: column; overflow: hidden; background: hsl(var(--card)); border: 1px solid hsl(var(--border)); min-width: 0; }
.graph-overview > :not(.overview-stage) { flex-shrink: 0; }
.graph-heading { min-height: 40px; padding: 8px 16px; background: linear-gradient(90deg, #0b345d, #071e3b); display: flex; align-items: center; justify-content: space-between; gap: 12px; border-bottom: 1px solid hsl(var(--border)); }
.graph-heading h1 { display: flex; align-items: center; gap: 6px; font-size: 16px; font-weight: 600; }
.graph-heading h1 svg { color: hsl(var(--primary)); stroke-width: 3; }
.graph-links { display: flex; gap: 16px; font-size: 12px; color: hsl(var(--muted-foreground)); }
.graph-links a:hover { color: hsl(var(--primary)); }
.overview-toolbar { padding: 8px 12px; display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.overview-toolbar .overview-search { width: 280px; }
.overview-toolbar :deep(.el-select) { width: 160px; }
.overview-actions { display: flex; gap: 8px; margin-left: auto; }
.overview-actions :deep(.el-button + .el-button) { margin-left: 0; }
.overview-stage { position: relative; flex: 1; min-height: 0; overflow: hidden; }
.overview-stage.has-cooperation :deep(.graph-canvas) { width: calc(100% - 350px); }
.cooperation-panel { position: absolute; right: 12px; top: 16px; width: 326px; max-height: calc(100% - 32px); overflow: auto; padding: 16px; display: grid; gap: 14px; border: 1px solid hsl(var(--border)); background: hsl(var(--card)); border-radius: 6px; }
.cooperation-authors { font-size: 16px; font-weight: 600; overflow-wrap: anywhere; }
.cooperation-authors span { color: hsl(var(--muted-foreground)); margin: 0 5px; }
.cooperation-works { display: grid; gap: 14px; font-size: 14px; overflow-wrap: anywhere; }
.cooperation-works a { color: hsl(var(--primary)); }
.cooperation-works span { display: block; font-size: 12px; color: hsl(var(--muted-foreground)); margin-top: 5px; }
.overview-stage :deep(.graph-canvas) { min-height: 0; background: hsl(var(--card)) !important; }
.canvas-controls, .canvas-statistics, .canvas-legend { position: absolute; z-index: 1; background: hsl(var(--card) / .96); }
.canvas-controls { top: 16px; left: 12px; display: flex; align-items: center; padding: 4px; border: 1px solid hsl(var(--border)); border-radius: 4px; color: hsl(var(--muted-foreground)); }
.canvas-history { display: flex; align-items: center; }
.canvas-controls :deep(.el-button + .el-button) { margin-left: 0; }
.canvas-statistics { top: 16px; right: 12px; padding: 14px 18px; border: 1px solid hsl(var(--border)); font-size: 13px; line-height: 1.9; }
.canvas-statistics div { display: flex; justify-content: space-between; gap: 10px; }
.canvas-statistics dd { font-variant-numeric: tabular-nums; }
.canvas-legend { left: 12px; bottom: 12px; display: flex; gap: 16px; align-items: center; flex-wrap: wrap; padding: 8px 12px; font-size: 12px; }
.canvas-legend li { display: flex; gap: 6px; align-items: center; }
.canvas-legend li > span { width: 12px; height: 12px; border-radius: 50%; }
.legend-note { color: hsl(var(--muted-foreground)); }
.overview-footer { border-top: 1px solid hsl(var(--border)); padding: 10px 16px; display: flex; flex-wrap: wrap; gap: 6px 20px; font-size: 11px; color: hsl(var(--muted-foreground)); }
.overview-notice { margin: 0 12px 8px; width: auto; }
.overview-empty { position: absolute; inset: 64px 24px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; text-align: center; pointer-events: none; font-size: 14px; }
.overview-empty span { font-size: 12px; color: hsl(var(--muted-foreground)); }
@media (min-width: 768px) {
  .overview-stage.has-cooperation .canvas-controls { right: 350px; flex-wrap: wrap; }
}
@media (max-width: 767px) {
  .graph-overview { margin: 8px; height: calc(100% - 16px); }
  .graph-heading { flex-wrap: wrap; padding: 8px 12px; gap: 8px; }
  .graph-links { gap: 12px; }
  .overview-toolbar { padding: 8px; gap: 8px; }
  .overview-toolbar .overview-search { width: 100%; }
  .overview-toolbar :deep(.el-select) { width: calc(50% - 4px); }
  .overview-actions { width: 100%; margin-left: 0; }
  .overview-stage { display: grid; grid-template-columns: minmax(0, 1fr); grid-template-rows: auto auto minmax(0, 1fr) auto; }
  .overview-stage :deep(.graph-canvas) { grid-column: 1; grid-row: 3; }
  .overview-stage.has-cooperation { grid-template-rows: auto minmax(0, 1fr) minmax(0, 1fr) auto; }
  .overview-stage.has-cooperation :deep(.graph-canvas) { grid-row: 2; width: 100%; }
  .cooperation-panel { position: static; grid-column: 1; grid-row: 3; width: auto; min-height: 0; max-height: 100%; margin: 0 8px; padding: 12px; gap: 8px; }
  .canvas-controls { position: static; grid-column: 1; grid-row: 1; flex-wrap: wrap; margin: 0 8px 8px; }
  .canvas-statistics { position: static; grid-column: 1; grid-row: 2; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); font-size: 11px; line-height: 1.5; padding: 8px; margin: 0 8px; }
  .canvas-statistics .statistics-heading { grid-column: 1 / -1; }
  .canvas-statistics > div:not(.statistics-heading) { flex-direction: column; gap: 0; }
  .canvas-statistics dd { font-size: 14px; font-weight: 600; }
  .canvas-legend { position: static; grid-column: 1; grid-row: 4; gap: 4px 12px; padding: 8px; }
  .overview-empty { inset: 144px 16px 56px; }
  .overview-footer { padding: 8px 12px; gap: 4px 12px; }
}
</style>
