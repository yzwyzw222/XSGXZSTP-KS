<script setup lang="ts">
import { ElButton, ElDrawer } from 'element-plus'
import { useMediaQuery } from '@vueuse/core'
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { DataTable, PanelSection } from '@/components/business'
import GraphCanvas from '@/components/business/GraphCanvas.vue'
import type { DataTableColumn } from '@/components/business/types'
import type { GraphEdge, GraphNode, GraphResponse } from '@/types/api'
import { GRAPH_NODE_LIMIT, filterGraphView, nodeTarget, relationshipLabel, reviewStatusLabel, toCytoscapeElements, typeDefinition } from '@/utils/graph'

const props = defineProps<{
  graph: GraphResponse | null
  loading: boolean
  addedNodeIds: string[]
  /** 每次查询完成后由父级更新，用于把选中元素重置到结果根节点。 */
  focus: { nodeId: string; seq: number } | null
}>()
const emit = defineEmits<{ (e: 'expand', node: GraphNode): void }>()

const selectedNodeId = ref('')
const selectedEdgeId = ref('')
const viewMode = ref<'graph' | 'nodes' | 'edges'>('graph')
const relationMode = ref<'all' | 'authorship' | 'coauthors'>('all')
const visibleGraph = computed(() => props.graph ? filterGraphView(props.graph, relationMode.value) : null)
const narrow = useMediaQuery('(max-width: 1279px)')
const inspectorOpen = ref(false)
const canvas = ref<InstanceType<typeof GraphCanvas> | null>(null)

watch(viewMode, async (value) => {
  if (value === 'graph') { await nextTick(); canvas.value?.resize() }
})

watch(() => props.focus, (value) => {
  if (!value) return
  relationMode.value = 'all'
  selectedNodeId.value = value.nodeId
  selectedEdgeId.value = ''
  viewMode.value = 'graph'
}, { immediate: true })

const selectedNode = computed<GraphNode | null>(() =>
  visibleGraph.value?.nodes.find((node) => node.id === selectedNodeId.value) ?? null)
const selectedEdge = computed<GraphEdge | null>(() =>
  visibleGraph.value?.edges.find((edge) => edge.id === selectedEdgeId.value) ?? null)
const selectedType = computed(() => selectedNode.value
  ? typeDefinition(props.graph, 'NODE', selectedNode.value.type)
  : selectedEdge.value ? typeDefinition(props.graph, 'RELATIONSHIP', selectedEdge.value.type) : undefined)
const sharedWorks = computed(() => {
  const ids = selectedEdge.value?.properties.sharedWorkIds
  return Array.isArray(ids) ? ids.map(id => ({ id: String(id), label: props.graph?.nodes.find(node => node.id === id)?.label ?? String(id) })) : []
})
const selectedNodeTarget = computed(() => selectedNode.value ? nodeTarget(selectedNode.value) : null)
const propertyRows = computed(() => {
  const properties = selectedNode.value?.properties ?? selectedEdge.value?.properties ?? {}
  return Object.entries(properties).filter(([key]) => !['derived', 'sharedWorkIds', 'sharedWorkCount', 'evidenceScope'].includes(key)).map(([key, value]) => ({
    key, value: typeof value === 'string' ? value : JSON.stringify(value),
  }))
})
const canExpandSelected = computed(() => {
  if (!selectedNode.value || (props.graph?.nodes.length ?? 0) >= GRAPH_NODE_LIMIT) return false
  const businessId = Number(selectedNode.value.businessId)
  return Number.isSafeInteger(businessId) && businessId > 0
})
const graphElements = computed(() => visibleGraph.value ? toCytoscapeElements(visibleGraph.value, props.graph ?? visibleGraph.value) : [])
const graphLabel = computed(() =>
  `知识图谱，共${visibleGraph.value?.nodes.length ?? 0}个节点和${visibleGraph.value?.edges.length ?? 0}条关系`)
const legend = computed(() => [...new Set(visibleGraph.value?.nodes.map(node => node.type) ?? [])].map(code => ({
  code, label: nodeTypeLabel(code), color: typeDefinition(props.graph, 'NODE', code)?.color,
})))

const nodeColumns: DataTableColumn<GraphNode>[] = [
  { id: 'type', accessorFn: (row) => nodeTypeLabel(row.type), header: '类型', enableSorting: false, meta: { width: '120px' } },
  { accessorKey: 'label', header: '名称', enableSorting: false },
  { accessorKey: 'businessId', header: '业务ID', enableSorting: false, meta: { width: '140px' } },
  { id: 'review', accessorFn: row => reviewStatusLabel(typeDefinition(props.graph, 'NODE', row.type)?.reviewStatus), header: '类型审核', enableSorting: false },
  { id: 'actions', header: '操作', enableSorting: false, meta: { width: '90px' } },
]
const edgeColumns: DataTableColumn<GraphEdge>[] = [
  { id: 'type', accessorFn: (row) => edgeTypeLabel(row.type), header: '关系', enableSorting: false, meta: { width: '120px' } },
  { accessorKey: 'source', header: '起点', enableSorting: false },
  { accessorKey: 'target', header: '终点', enableSorting: false },
  { id: 'review', accessorFn: row => reviewStatusLabel(typeDefinition(props.graph, 'RELATIONSHIP', row.type)?.reviewStatus), header: '类型审核', enableSorting: false },
  { id: 'actions', header: '操作', enableSorting: false, meta: { width: '90px' } },
]

function nodeTypeLabel(type: string): string {
  const definition = typeDefinition(props.graph, 'NODE', type)
  if (definition) return definition.displayName
  return {
    ACHIEVEMENT: '成果', AUTHOR: '作者', INSTITUTION: '机构', VENUE: '期刊/载体', TOPIC: '主题',
  }[type] ?? type
}

function selectNode(id: string): void {
  selectedNodeId.value = id
  selectedEdgeId.value = ''
  if (narrow.value) inspectorOpen.value = true
}
function selectEdge(id: string): void {
  selectedEdgeId.value = id
  selectedNodeId.value = ''
  if (narrow.value) inspectorOpen.value = true
}

function edgeTypeLabel(type: string): string {
  return typeDefinition(props.graph, 'RELATIONSHIP', type)?.displayName ?? relationshipLabel(type)
}

watch(relationMode, async () => {
  clearSelection()
  await nextTick()
  canvas.value?.fit()
}, { flush: 'sync' })
function clearSelection(): void {
  selectedNodeId.value = ''
  selectedEdgeId.value = ''
}
</script>

<template>
  <div class="graph-workspace grid grid-cols-1 items-start gap-4 xl:grid-cols-[minmax(0,3fr)_minmax(280px,1fr)]">
    <PanelSection :padded="false" class="overflow-hidden">
      <template #title>
        <div class="min-w-0">
          <h2 class="truncate text-sm font-semibold">{{ visibleGraph?.nodes.length ?? 0 }} 个节点 · {{ visibleGraph?.edges.length ?? 0 }} 条关系</h2>
          <span class="mono-evidence block text-xs text-muted-foreground">Trace {{ graph?.traceId }}</span>
        </div>
      </template>
      <template #actions>
        <ElButton v-if="narrow" plain size="small" @click="inspectorOpen = true">查看图谱详情</ElButton>
        <div class="flex items-center gap-1 rounded-md border border-border p-0.5" role="group" aria-label="图谱视图切换">
          <ElButton
            v-for="mode in ([['graph', '图形'], ['nodes', '节点表'], ['edges', '关系表']] as const)"
            :key="mode[0]"
            text
            size="small"
            :type="viewMode === mode[0] ? 'primary' : 'default'"
            :aria-pressed="viewMode === mode[0]"
            @click="viewMode = mode[0]"
          >
            {{ mode[1] }}
          </ElButton>
        </div>
      </template>

      <div class="flex flex-wrap gap-2 border-b border-border px-4 py-3" role="group" aria-label="关系展示范围">
        <ElButton v-for="mode in ([['all', '全部关系'], ['authorship', '作者—作品'], ['coauthors', '作者—作者']] as const)" :key="mode[0]" size="small" :type="relationMode === mode[0] ? 'primary' : 'default'" :aria-pressed="relationMode === mode[0]" @click="relationMode = mode[0]">{{ mode[1] }}</ElButton>
        <p class="w-full text-xs text-muted-foreground">合作虚线根据当前子图中的共同作品生成；查询作者时选择 2 跳可同时读取共同作者。</p>
        <p v-if="!visibleGraph?.nodes.length" class="w-full text-sm">当前结果没有{{ relationMode === 'coauthors' ? '有共同作品依据的作者合作' : '匹配的' }}关系，可扩大查询深度或调整中心节点。</p>
      </div>

      <!-- 图例与画布共用本次响应中的 MySQL 类型配置。 -->
      <ul v-if="viewMode === 'graph'" class="flex flex-wrap gap-x-4 gap-y-1 border-b border-border px-4 py-2 text-xs text-muted-foreground" aria-label="节点类型图例">
        <li v-for="item in legend" :key="item.code" class="flex items-center gap-1.5">
          <span class="size-2.5 rounded-full" :style="{ background: item.color ?? `hsl(var(--graph-${item.code.toLowerCase()}))` }" aria-hidden="true" />
          {{ item.label }}
        </li>
      </ul>

      <div v-show="viewMode === 'graph'">
        <div class="graph-tools flex flex-wrap items-center gap-2 border-b border-border px-4 py-2">
          <ElButton text size="small" @click="canvas?.fit()">重置视图</ElButton>
          <ElButton text size="small" :disabled="!selectedNode && !selectedEdge" @click="canvas?.focus()">聚焦所选</ElButton>
          <ElButton text size="small" :disabled="!selectedNode && !selectedEdge" @click="clearSelection">取消选择</ElButton>
          <span class="ml-auto text-xs text-muted-foreground">拖动节点 · 滚轮缩放</span>
        </div>
        <GraphCanvas
          ref="canvas"
          :elements="graphElements"
          :root-node-id="visibleGraph?.rootNodeId ?? ''"
          :selected-node-id="selectedNodeId"
          :selected-edge-id="selectedEdgeId"
          :label="graphLabel"
          :loading="loading"
          :added-node-ids="addedNodeIds"
          @select-node="selectNode"
          @select-edge="selectEdge"
          @clear-selection="clearSelection"
        />
      </div>

      <div v-if="viewMode === 'nodes'" class="p-4">
        <DataTable
          :columns="nodeColumns"
          :data="visibleGraph?.nodes ?? []"
          :get-row-id="(row) => row.id"
          empty-text="暂无节点"
          dense
        >
          <template #cell-actions="{ row }">
            <ElButton link type="primary" @click="selectNode(row.id)">查看</ElButton>
          </template>
        </DataTable>
      </div>
      <div v-if="viewMode === 'edges'" class="p-4">
        <DataTable
          :columns="edgeColumns"
          :data="visibleGraph?.edges ?? []"
          :get-row-id="(row) => row.id"
          empty-text="暂无关系"
          dense
        >
          <template #cell-actions="{ row }">
            <ElButton link type="primary" @click="selectEdge(row.id)">查看</ElButton>
          </template>
        </DataTable>
      </div>
    </PanelSection>

    <!-- 检查器 -->
    <component
      :is="narrow ? ElDrawer : PanelSection"
      v-bind="narrow ? { modelValue: inspectorOpen, title: '图谱详情', size: 'min(420px, 100vw)', appendToBody: true, destroyOnClose: true } : {}"
      :class="narrow ? 'aacv-drawer' : 'xl:sticky xl:top-20'"
      aria-live="polite"
      @update:model-value="(value: boolean) => { inspectorOpen = value }"
    >
      <Transition name="content-switch">
      <div :key="selectedNodeId || selectedEdgeId || 'empty'">
      <template v-if="selectedNode">
        <span class="eyebrow">节点 · {{ nodeTypeLabel(selectedNode.type) }}</span>
        <h2 class="mt-2 break-words text-base font-semibold leading-relaxed">{{ selectedNode.label }}</h2>
        <p class="mono-evidence mt-1 text-xs text-muted-foreground">业务ID · {{ selectedNode.businessId }}</p>
        <div class="my-4 flex items-center justify-between gap-3 border-b border-border pb-4">
          <ElButton plain size="small" :disabled="!canExpandSelected" @click="emit('expand', selectedNode)">展开一跳</ElButton>
          <RouterLink v-if="selectedNodeTarget" class="inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline" :to="selectedNodeTarget">
            进入业务详情 →
          </RouterLink>
        </div>
      </template>
      <template v-else-if="selectedEdge">
        <span class="eyebrow">关系</span>
        <h2 class="mt-2 text-base font-semibold">{{ edgeTypeLabel(selectedEdge.type) }}</h2>
        <p class="mono-evidence mt-1 break-words text-xs text-muted-foreground">{{ selectedEdge.source }} → {{ selectedEdge.target }}</p>
        <div v-if="selectedEdge.type === 'COAUTHORED'" class="mt-4 text-sm">
          <p>派生合作 · 当前子图共同作品 {{ sharedWorks.length }} 部</p>
          <ul class="mt-2 list-inside list-disc"><li v-for="work in sharedWorks" :key="work.id">{{ work.label }}</li></ul>
          <p class="mt-2 text-xs text-muted-foreground">依据 Neo4j 创作关系计算，不代表全库合作次数。</p>
        </div>
      </template>
      <template v-else>
        <span class="eyebrow">检查器</span>
        <h2 class="mt-2 text-base font-semibold">选择图中元素</h2>
        <p class="mt-1 text-sm text-muted-foreground">点击节点或关系，查看有限摘要、主动展开或进入对应业务页面。</p>
      </template>

      <p v-if="selectedType" class="my-3 text-sm">类型审核：{{ reviewStatusLabel(selectedType.reviewStatus) }} · {{ selectedType.size }} px</p>
      <dl v-if="selectedNode || selectedEdge" class="grid grid-cols-[minmax(90px,0.7fr)_minmax(0,1.3fr)] gap-y-1">
        <template v-for="item in propertyRows" :key="item.key">
          <dt class="border-b border-border py-2 text-xs text-muted-foreground">{{ item.key }}</dt>
          <dd class="break-words border-b border-border py-2 text-sm">{{ item.value }}</dd>
        </template>
      </dl>
      <p v-if="(selectedNode || selectedEdge) && !propertyRows.length" class="mt-3 text-sm text-muted-foreground">该元素没有额外摘要属性。</p>
      </div>
      </Transition>
    </component>
  </div>
</template>
