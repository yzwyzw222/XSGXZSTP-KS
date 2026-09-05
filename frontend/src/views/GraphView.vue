<script setup lang="ts">
import type { ColumnDef } from '@tanstack/vue-table'
import { Waypoints } from 'lucide-vue-next'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { DataTable, FilterField, PageHeader, PanelSection, StatusPill } from '@/components/business'
import GraphCanvas from '@/components/business/GraphCanvas.vue'
import GraphEntityPicker from '@/components/business/GraphEntityPicker.vue'
import GraphSavedQueries from '@/components/business/GraphSavedQueries.vue'
import { Alert, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger } from '@/components/ui/select'
import { toErrorMessage } from '@/services/api'
import { graphApi } from '@/services/business'
import { hasPermission } from '@/services/session'
import type {
  GraphEdge, GraphNode, GraphNodeType, GraphRelationshipType, GraphResponse, GraphSyncStatus,
} from '@/types/api'
import { GRAPH_NODE_LIMIT, mergeGraph, nodeTarget, relationshipLabel, toCytoscapeElements } from '@/utils/graph'
import { formatDateTime, splitValues } from '@/utils/format'
import type { GraphFilters } from '@/utils/graph-query'

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
const route = useRoute()
let graphRequestVersion = 0
const errorMessage = ref('')
const graph = ref<GraphResponse | null>(null)
const syncStatus = ref<GraphSyncStatus | null>(null)
const selectedNodeId = ref('')
const selectedEdgeId = ref('')
const viewMode = ref<'graph' | 'nodes' | 'edges'>('graph')
const addedNodeIds = ref<string[]>([])

const filters = reactive<GraphFilters>({
  centerType: 'ACHIEVEMENT' as GraphNodeType,
  centerId: '',
  depth: '1',
  nodeLimit: '100',
  publicationYearFrom: '',
  publicationYearTo: '',
  nodeTypes: [] as GraphNodeType[],
  relationshipTypes: [] as GraphRelationshipType[],
  achievementTypes: '',
})
const pathQuery = reactive({
  sourceType: 'AUTHOR' as GraphNodeType,
  sourceId: '',
  targetType: 'TOPIC' as GraphNodeType,
  targetId: '',
  maxHops: '6',
})

const selectedNode = computed<GraphNode | null>(() =>
  graph.value?.nodes.find((node) => node.id === selectedNodeId.value) ?? null)
const selectedEdge = computed<GraphEdge | null>(() =>
  graph.value?.edges.find((edge) => edge.id === selectedEdgeId.value) ?? null)
const selectedNodeTarget = computed(() => selectedNode.value ? nodeTarget(selectedNode.value) : null)
const propertyRows = computed(() => {
  const properties = selectedNode.value?.properties ?? selectedEdge.value?.properties ?? {}
  return Object.entries(properties).map(([key, value]) => ({
    key, value: typeof value === 'string' ? value : JSON.stringify(value),
  }))
})
const canExpandSelected = computed(() => {
  if (!selectedNode.value || (graph.value?.nodes.length ?? 0) >= GRAPH_NODE_LIMIT) return false
  const businessId = Number(selectedNode.value.businessId)
  return Number.isSafeInteger(businessId) && businessId > 0
})
const syncWarning = computed(() =>
  Boolean(syncStatus.value && (!syncStatus.value.neo4jAvailable || syncStatus.value.lagThresholdExceeded || syncStatus.value.rebuildInProgress)))
const syncLabel = computed(() => {
  if (syncStatus.value?.rebuildInProgress) return '图投影正在重建'
  if (syncStatus.value && !syncStatus.value.neo4jAvailable) return 'Neo4j 暂不可用'
  if (syncStatus.value?.lagThresholdExceeded) return '图同步存在积压'
  return graph.value?.syncedAt ? `投影于 ${formatDateTime(graph.value.syncedAt)}` : '等待加载图数据'
})
const graphElements = computed(() => graph.value ? toCytoscapeElements(graph.value) : [])
const graphLabel = computed(() =>
  `知识图谱，共${graph.value?.nodes.length ?? 0}个节点和${graph.value?.edges.length ?? 0}条关系`)

const nodeColumns: ColumnDef<GraphNode, any>[] = [
  { id: 'type', accessorFn: (row) => nodeTypeLabel(row.type), header: '类型', enableSorting: false, meta: { width: '120px' } },
  { accessorKey: 'label', header: '名称', enableSorting: false },
  { accessorKey: 'businessId', header: '业务ID', enableSorting: false, meta: { width: '140px' } },
  { id: 'actions', header: '操作', enableSorting: false, meta: { width: '90px' } },
]
const edgeColumns: ColumnDef<GraphEdge, any>[] = [
  { id: 'type', accessorFn: (row) => relationshipLabel(row.type), header: '关系', enableSorting: false, meta: { width: '120px' } },
  { accessorKey: 'source', header: '起点', enableSorting: false },
  { accessorKey: 'target', header: '终点', enableSorting: false },
  { id: 'actions', header: '操作', enableSorting: false, meta: { width: '90px' } },
]

function numOrUndef(value: string): number | undefined {
  const trimmed = value.trim()
  return trimmed === '' ? undefined : Number(trimmed)
}

async function loadCenter(): Promise<void> {
  const centerId = Number(filters.centerId)
  if (!filters.centerId.trim() || !Number.isSafeInteger(centerId) || centerId < 1) {
    errorMessage.value = '请输入大于0的中心节点业务ID。'
    return
  }
  await loadGraph(() => graphApi.subgraph({
    centerType: filters.centerType,
    centerId,
    depth: Number(filters.depth) || 1,
    nodeLimit: Number(filters.nodeLimit) || 100,
    relationshipTypes: filters.relationshipTypes.length ? filters.relationshipTypes : undefined,
    nodeTypes: filters.nodeTypes.length ? filters.nodeTypes : undefined,
    publicationYearFrom: numOrUndef(filters.publicationYearFrom),
    publicationYearTo: numOrUndef(filters.publicationYearTo),
    achievementTypes: splitAchievementTypes(),
  }), false)
}

async function loadPath(): Promise<void> {
  const sourceId = Number(pathQuery.sourceId)
  const targetId = Number(pathQuery.targetId)
  if (!Number.isSafeInteger(sourceId) || sourceId < 1 || !Number.isSafeInteger(targetId) || targetId < 1) {
    errorMessage.value = '请输入大于0的路径起点和终点业务ID。'
    return
  }
  await loadGraph(() => graphApi.path({
    sourceType: pathQuery.sourceType,
    sourceId,
    targetType: pathQuery.targetType,
    targetId,
    maxHops: Number(pathQuery.maxHops) || 6,
  }), false)
}

async function expandSelected(): Promise<void> {
  const node = selectedNode.value
  if (!node || !canExpandSelected.value) return
  await loadGraph(() => graphApi.subgraph({
    centerType: node.type,
    centerId: Number(node.businessId),
    depth: 1,
    nodeLimit: Math.min(Number(filters.nodeLimit) || 100, GRAPH_NODE_LIMIT - (graph.value?.nodes.length ?? 0)),
    relationshipTypes: filters.relationshipTypes.length ? filters.relationshipTypes : undefined,
    nodeTypes: filters.nodeTypes.length ? filters.nodeTypes : undefined,
    publicationYearFrom: numOrUndef(filters.publicationYearFrom),
    publicationYearTo: numOrUndef(filters.publicationYearTo),
    achievementTypes: splitAchievementTypes(),
  }), true)
}

async function loadGraph(request: () => Promise<GraphResponse>, merge: boolean): Promise<void> {
  const version = ++graphRequestVersion
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await request()
    if (version !== graphRequestVersion) return
    const existingNodeIds = new Set(graph.value?.nodes.map((node) => node.id) ?? [])
    addedNodeIds.value = merge
      ? response.nodes.filter((node) => !existingNodeIds.has(node.id)).map((node) => node.id)
      : []
    graph.value = mergeGraph(merge ? graph.value : null, response)
    selectedNodeId.value = response.rootNodeId
    selectedEdgeId.value = ''
    viewMode.value = 'graph'
    await nextTick()
  } catch (error) {
    if (version === graphRequestVersion) errorMessage.value = toErrorMessage(error)
  } finally {
    if (version === graphRequestVersion) loading.value = false
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

function toggleNodeType(type: GraphNodeType): void {
  const index = filters.nodeTypes.indexOf(type)
  if (index >= 0) filters.nodeTypes.splice(index, 1)
  else filters.nodeTypes.push(type)
}
function toggleRelationshipType(type: GraphRelationshipType): void {
  const index = filters.relationshipTypes.indexOf(type)
  if (index >= 0) filters.relationshipTypes.splice(index, 1)
  else filters.relationshipTypes.push(type)
}

function splitAchievementTypes(): string[] | undefined {
  const values = splitValues(filters.achievementTypes)
  return values.length ? [...new Set(values)] : undefined
}

function nodeTypeLabel(type: GraphNodeType): string {
  return nodeTypes.find((item) => item.value === type)?.label ?? type
}

function selectNode(id: string): void {
  selectedNodeId.value = id
  selectedEdgeId.value = ''
}
function selectEdge(id: string): void {
  selectedEdgeId.value = id
  selectedNodeId.value = ''
}

async function restoreQuery(value: GraphFilters): Promise<void> {
  Object.assign(filters, value)
  await nextTick()
  filters.centerId = value.centerId
  await loadCenter()
}

/** 详情页只传类型和规范ID；不把路由参数当作完整服务端查询。 */
async function loadRouteCenter(): Promise<void> {
  const { centerType, centerId } = route.query
  if (centerType === undefined && centerId === undefined) return
  if (typeof centerType !== 'string' || !nodeTypes.some((item) => item.value === centerType)
    || typeof centerId !== 'string' || !/^\d+$/.test(centerId)
    || !Number.isSafeInteger(Number(centerId)) || Number(centerId) < 1) {
    errorMessage.value = '图谱入口参数无效，请重新选择中心节点。'
    return
  }
  filters.centerType = centerType as GraphNodeType
  await nextTick()
  filters.centerId = centerId
  await loadCenter()
}

watch(() => [route.query.centerType, route.query.centerId], loadRouteCenter)
onMounted(() => { void loadSyncStatus(); void loadRouteCenter() })
onBeforeUnmount(() => { graphRequestVersion++ })
</script>

<template>
  <section class="page-stack">
    <PageHeader
      title="知识图谱"
      description="搜索论文、作者或机构，探索它们之间的关系。图谱为局部数据视图，成果与统计以 MySQL 规范目录为准。"
    >
      <template #actions>
        <div class="flex flex-wrap items-center gap-2">
          <StatusPill :status="syncWarning ? 'DEGRADED' : 'UP'" :pulse="!syncWarning" />
          <span class="text-sm font-medium">{{ syncLabel }}</span>
          <span v-if="syncStatus" class="text-xs text-muted-foreground">
            待处理 {{ syncStatus.pendingCount }} · 死信 {{ syncStatus.deadCount }}
          </span>
        </div>
      </template>
    </PageHeader>

    <GraphSavedQueries :filters="filters" @restore="restoreQuery" />

    <!-- 过滤区 -->
    <PanelSection title="子图过滤" subtitle="深度最大2，累计最多300个节点；空类型过滤表示全部。">
      <div class="grid grid-cols-1 items-start gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <FilterField label="中心类型">
          <Select v-model="filters.centerType">
            <SelectTrigger placeholder="选择中心类型" />
            <SelectContent>
              <SelectItem v-for="item in nodeTypes" :key="item.value" :value="item.value">{{ item.label }}</SelectItem>
            </SelectContent>
          </Select>
        </FilterField>
        <GraphEntityPicker v-model="filters.centerId" :type="filters.centerType" label="中心" class="sm:col-span-2" />
        <FilterField label="查询深度"><Input v-model="filters.depth" type="number" min="1" max="2" /></FilterField>
        <FilterField label="本次节点上限"><Input v-model="filters.nodeLimit" type="number" min="1" max="300" /></FilterField>
        <FilterField label="起始年份"><Input v-model="filters.publicationYearFrom" type="number" min="1000" max="9999" /></FilterField>
        <FilterField label="结束年份"><Input v-model="filters.publicationYearTo" type="number" min="1000" max="9999" /></FilterField>
        <FilterField label="成果类型" class="sm:col-span-2" hint="多个类型使用英文逗号分隔">
          <Input v-model="filters.achievementTypes" placeholder="如 article, review" @keydown.enter="loadCenter" />
        </FilterField>
      </div>

      <div class="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <div class="space-y-2">
          <span class="text-sm font-medium text-muted-foreground">节点类型</span>
          <div class="flex flex-wrap gap-1.5">
            <button
              v-for="item in nodeTypes"
              :key="item.value"
              type="button"
              :aria-pressed="filters.nodeTypes.includes(item.value)"
              class="rounded-full border px-3 py-1 text-xs transition-colors"
              :class="filters.nodeTypes.includes(item.value)
                ? 'border-primary bg-primary/12 text-primary'
                : 'border-border text-muted-foreground hover:border-primary/50 hover:text-foreground'"
              @click="toggleNodeType(item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>
        <div class="space-y-2">
          <span class="text-sm font-medium text-muted-foreground">关系类型</span>
          <div class="flex flex-wrap gap-1.5">
            <button
              v-for="item in relationshipTypes"
              :key="item.value"
              type="button"
              :aria-pressed="filters.relationshipTypes.includes(item.value)"
              class="rounded-full border px-3 py-1 text-xs transition-colors"
              :class="filters.relationshipTypes.includes(item.value)
                ? 'border-primary bg-primary/12 text-primary'
                : 'border-border text-muted-foreground hover:border-primary/50 hover:text-foreground'"
              @click="toggleRelationshipType(item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>
      </div>

      <div class="mt-4 flex justify-end border-t border-border pt-4">
        <Button :loading="loading" @click="loadCenter">加载中心子图</Button>
      </div>

      <!-- 路径查询 -->
      <details class="mt-4 rounded-lg border border-border bg-muted/30 px-4">
        <summary class="cursor-pointer py-3 text-sm font-medium text-primary">查询两点间最短路径</summary>
        <div class="grid grid-cols-1 items-end gap-3 pb-4 sm:grid-cols-2 lg:grid-cols-6">
          <FilterField label="起点类型">
            <Select v-model="pathQuery.sourceType">
              <SelectTrigger placeholder="起点类型" />
              <SelectContent><SelectItem v-for="item in nodeTypes" :key="item.value" :value="item.value">{{ item.label }}</SelectItem></SelectContent>
            </Select>
          </FilterField>
          <GraphEntityPicker v-model="pathQuery.sourceId" :type="pathQuery.sourceType" label="起点" />
          <FilterField label="终点类型">
            <Select v-model="pathQuery.targetType">
              <SelectTrigger placeholder="终点类型" />
              <SelectContent><SelectItem v-for="item in nodeTypes" :key="item.value" :value="item.value">{{ item.label }}</SelectItem></SelectContent>
            </Select>
          </FilterField>
          <GraphEntityPicker v-model="pathQuery.targetId" :type="pathQuery.targetType" label="终点" />
          <FilterField label="最大跳数"><Input v-model="pathQuery.maxHops" type="number" min="1" max="6" /></FilterField>
          <Button variant="outline" :loading="loading" @click="loadPath">查询路径</Button>
        </div>
      </details>
    </PanelSection>

    <Alert v-if="errorMessage" variant="destructive"><AlertTitle>{{ errorMessage }}</AlertTitle></Alert>
    <Alert v-if="graph?.truncated" variant="warning">
      <AlertTitle>{{ graph.narrowingSuggestion || '图结果已达到服务端限制，请缩小过滤范围。' }}</AlertTitle>
    </Alert>

    <!-- 图工作区 -->
    <div v-if="graph" class="grid grid-cols-1 items-start gap-4 xl:grid-cols-[minmax(0,3fr)_minmax(280px,1fr)]">
      <PanelSection :padded="false" class="overflow-hidden">
        <template #title>
          <div class="min-w-0">
            <h2 class="truncate text-sm font-semibold">{{ graph.nodes.length }} 个节点 · {{ graph.edges.length }} 条关系</h2>
            <span class="mono-evidence block text-xs text-muted-foreground">Trace {{ graph.traceId }}</span>
          </div>
        </template>
        <template #actions>
          <div class="flex items-center gap-1 rounded-md border border-border p-0.5" role="group" aria-label="图谱视图切换">
            <button
              v-for="mode in ([['graph', '图形'], ['nodes', '节点表'], ['edges', '关系表']] as const)"
              :key="mode[0]"
              type="button"
              :aria-pressed="viewMode === mode[0]"
              class="rounded px-2.5 py-1 text-xs font-medium transition-colors"
              :class="viewMode === mode[0] ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground'"
              @click="viewMode = mode[0]"
            >
              {{ mode[1] }}
            </button>
          </div>
        </template>

        <!-- 图例 -->
        <ul v-if="viewMode === 'graph'" class="flex flex-wrap gap-x-4 gap-y-1 border-b border-border px-4 py-2 text-xs text-muted-foreground" aria-label="节点类型图例">
          <li v-for="item in nodeTypes" :key="item.value" class="flex items-center gap-1.5">
            <span class="size-2.5 rounded-full" :class="{
              'bg-graph-achievement': item.value === 'ACHIEVEMENT',
              'bg-graph-author': item.value === 'AUTHOR',
              'bg-graph-institution': item.value === 'INSTITUTION',
              'bg-graph-venue': item.value === 'VENUE',
              'bg-graph-topic': item.value === 'TOPIC',
            }" aria-hidden="true" />
            {{ item.label }}
          </li>
        </ul>

        <div v-show="viewMode === 'graph'" class="p-2">
          <GraphCanvas
            :elements="graphElements"
            :root-node-id="graph.rootNodeId"
            :label="graphLabel"
            :loading="loading"
            :added-node-ids="addedNodeIds"
            @select-node="selectNode"
            @select-edge="selectEdge"
          />
        </div>

        <div v-if="viewMode === 'nodes'" class="p-4">
          <DataTable
            :columns="nodeColumns"
            :data="graph.nodes"
            :get-row-id="(row) => row.id"
            empty-text="暂无节点"
            dense
          >
            <template #cell-actions="{ row }">
              <Button variant="link" size="sm" class="h-auto p-0" @click="selectNode(row.id)">查看</Button>
            </template>
          </DataTable>
        </div>
        <div v-if="viewMode === 'edges'" class="p-4">
          <DataTable
            :columns="edgeColumns"
            :data="graph.edges"
            :get-row-id="(row) => row.id"
            empty-text="暂无关系"
            dense
          >
            <template #cell-actions="{ row }">
              <Button variant="link" size="sm" class="h-auto p-0" @click="selectEdge(row.id)">查看</Button>
            </template>
          </DataTable>
        </div>
      </PanelSection>

      <!-- 检查器 -->
      <PanelSection class="xl:sticky xl:top-20" aria-live="polite">
        <template v-if="selectedNode">
          <span class="eyebrow">节点 · {{ nodeTypeLabel(selectedNode.type) }}</span>
          <h2 class="mt-1 break-words text-xl font-semibold">{{ selectedNode.label }}</h2>
          <p class="mono-evidence mt-1 text-xs text-muted-foreground">业务ID · {{ selectedNode.businessId }}</p>
          <div class="my-4 flex items-center justify-between gap-3 border-b border-border pb-4">
            <Button variant="outline" size="sm" :disabled="!canExpandSelected" @click="expandSelected">展开一跳</Button>
            <RouterLink v-if="selectedNodeTarget" class="inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline" :to="selectedNodeTarget">
              进入业务详情 →
            </RouterLink>
          </div>
        </template>
        <template v-else-if="selectedEdge">
          <span class="eyebrow">关系</span>
          <h2 class="mt-1 text-xl font-semibold">{{ relationshipLabel(selectedEdge.type) }}</h2>
          <p class="mono-evidence mt-1 break-words text-xs text-muted-foreground">{{ selectedEdge.source }} → {{ selectedEdge.target }}</p>
        </template>
        <template v-else>
          <span class="eyebrow">检查器</span>
          <h2 class="mt-1 text-xl font-semibold">选择图中元素</h2>
          <p class="mt-1 text-sm text-muted-foreground">点击节点或关系，查看有限摘要、主动展开或进入对应业务页面。</p>
        </template>

        <dl v-if="selectedNode || selectedEdge" class="grid grid-cols-[minmax(90px,0.7fr)_minmax(0,1.3fr)] gap-y-1">
          <template v-for="item in propertyRows" :key="item.key">
            <dt class="border-b border-border py-2 text-xs text-muted-foreground">{{ item.key }}</dt>
            <dd class="break-words border-b border-border py-2 text-sm">{{ item.value }}</dd>
          </template>
        </dl>
        <p v-if="(selectedNode || selectedEdge) && !propertyRows.length" class="mt-3 text-sm text-muted-foreground">该元素没有额外摘要属性。</p>
      </PanelSection>
    </div>

    <!-- 空态 -->
    <PanelSection v-else>
      <div class="flex flex-col items-center gap-3 px-6 py-14 text-center">
        <Waypoints class="size-10 text-muted-foreground/50" aria-hidden="true" />
        <strong class="text-base font-semibold">从一个确定的业务节点开始</strong>
        <p class="max-w-md text-sm text-muted-foreground">
          选择节点类型并输入业务ID。这里不会自动请求大范围图谱，也不会绕过服务端的深度和节点上限。
        </p>
      </div>
    </PanelSection>
  </section>
</template>
