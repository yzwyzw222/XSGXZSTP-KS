<script setup lang="ts">
import { ElAlert, ElButton, ElOption, ElSelect, ElTabPane, ElTabs } from 'element-plus'
import { ChevronDown, FileText, Waypoints } from 'lucide-vue-next'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { FilterField, LoadingSkeleton, PageHeader, PanelSection } from '@/components/business'
import GraphEntityPicker from '@/components/business/GraphEntityPicker.vue'
import GraphPathForm from '@/components/business/GraphPathForm.vue'
import GraphSavedQueries from '@/components/business/GraphSavedQueries.vue'
import GraphWorkspace from '@/components/business/GraphWorkspace.vue'
import type { GraphPathQuery } from '@/components/business/types'
import YearPicker from '@/components/business/YearPicker.vue'
import { storeToRefs } from 'pinia'
import { useGraphStore } from '@/stores/graph'
import { graphApi } from '@/services/business'
import { useSessionStore } from '@/stores/session'
import type {
  GraphNode, GraphNodeType, GraphRelationshipType,
} from '@/types/api'
import { achievementTypeOptions } from '@/utils/filter-options'
import { splitValues } from '@/utils/format'
import { GRAPH_NODE_LIMIT } from '@/utils/graph'
import { readSavedQueries, type GraphFilters, type SavedGraphQuery } from '@/utils/graph-query'

const nodeTypes: Array<{ value: GraphNodeType; label: string }> = [
  { value: 'ACHIEVEMENT', label: '成果' },
  { value: 'AUTHOR', label: '作者' },
  { value: 'INSTITUTION', label: '机构' },
  { value: 'VENUE', label: '期刊/载体' },
  { value: 'TOPIC', label: '主题' },
]
const queryOpen = ref(false)
const relationshipTypes: Array<{ value: GraphRelationshipType; label: string }> = [
  { value: 'AUTHORED', label: '创作' },
  { value: 'SUPERVISED', label: '指导' },
  { value: 'PRODUCED_AT', label: '所属机构' },
  { value: 'AFFILIATED_WITH', label: '隶属' },
  { value: 'PUBLISHED_IN', label: '发表于' },
  { value: 'HAS_TOPIC', label: '主题' },
  { value: 'CITES', label: '引用' },
]
const depthOptions = [
  { value: '1', label: '1 跳' },
  { value: '2', label: '2 跳（最大）' },
]
const nodeLimitOptions = [
  { value: '50', label: '50 个' },
  { value: '100', label: '100 个（默认）' },
  { value: '200', label: '200 个' },
  { value: '300', label: '300 个（最大）' },
]

const sessionStore = useSessionStore()
const currentUserId = computed(() => sessionStore.currentUserId)

const graphStore = useGraphStore()
graphStore.reset()
const { loading, errorMessage, graph, addedNodeIds, focus } = storeToRefs(graphStore)
watch(graph, value => { if (value) queryOpen.value = false })
const loadGraph = graphStore.load
const route = useRoute()
/** 图请求序号：新查询、组件卸载都会使在途响应作废。 */
const activeTab = ref<'basic' | 'filters' | 'advanced'>('basic')
const scopeOpen = ref(false)
const savedTemplates = ref<SavedGraphQuery[]>([])

const filters = reactive<GraphFilters>({
  centerType: 'ACHIEVEMENT' as GraphNodeType,
  centerId: '',
  depth: '2',
  nodeLimit: '100',
  publicationYearFrom: '',
  publicationYearTo: '',
  nodeTypes: [] as GraphNodeType[],
  relationshipTypes: [] as GraphRelationshipType[],
  achievementTypes: '',
})

/** 年份以面板选择，筛选值仍保存为字符串以兼容常用查询快照。 */
const yearFromModel = computed<number | undefined>({
  get: () => (filters.publicationYearFrom.trim() ? Number(filters.publicationYearFrom) : undefined),
  set: (value) => { filters.publicationYearFrom = value === undefined ? '' : String(value) },
})
const yearToModel = computed<number | undefined>({
  get: () => (filters.publicationYearTo.trim() ? Number(filters.publicationYearTo) : undefined),
  set: (value) => { filters.publicationYearTo = value === undefined ? '' : String(value) },
})
const selectedAchievementTypes = computed<string[]>(() => splitValues(filters.achievementTypes))

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
    includeCoauthors: true,
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

async function loadPath(query: GraphPathQuery): Promise<void> {
  const sourceId = Number(query.sourceId)
  const targetId = Number(query.targetId)
  if (!Number.isSafeInteger(sourceId) || sourceId < 1 || !Number.isSafeInteger(targetId) || targetId < 1) {
    errorMessage.value = '请输入大于0的路径起点和终点业务ID。'
    return
  }
  await loadGraph(() => graphApi.path({
    sourceType: query.sourceType,
    sourceId,
    targetType: query.targetType,
    targetId,
    maxHops: Number(query.maxHops) || 6,
  }), false)
}

async function expandSelected(node: GraphNode): Promise<void> {
  // 图谱节点硬上限 300，达到上限后不再展开。
  if ((graph.value?.nodes.length ?? 0) >= GRAPH_NODE_LIMIT) return
  const businessId = Number(node.businessId)
  if (!Number.isSafeInteger(businessId) || businessId < 1) return
  await loadGraph(() => graphApi.subgraph({
    includeCoauthors: true,
    centerType: node.type,
    centerId: businessId,
    depth: 1,
    nodeLimit: Math.min(Number(filters.nodeLimit) || 100, GRAPH_NODE_LIMIT - (graph.value?.nodes.length ?? 0)),
    relationshipTypes: filters.relationshipTypes.length ? filters.relationshipTypes : undefined,
    nodeTypes: filters.nodeTypes.length ? filters.nodeTypes : undefined,
    publicationYearFrom: numOrUndef(filters.publicationYearFrom),
    publicationYearTo: numOrUndef(filters.publicationYearTo),
    achievementTypes: splitAchievementTypes(),
  }), true)
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
function toggleAchievementType(type: string): void {
  const next = selectedAchievementTypes.value.includes(type)
    ? selectedAchievementTypes.value.filter((item) => item !== type)
    : [...selectedAchievementTypes.value, type]
  filters.achievementTypes = next.join(',')
}

function splitAchievementTypes(): string[] | undefined {
  const values = splitValues(filters.achievementTypes)
  return values.length ? [...new Set(values)] : undefined
}

function nodeTypeLabel(type: GraphNodeType): string {
  return nodeTypes.find((item) => item.value === type)?.label ?? type
}

function refreshSavedTemplates(): void {
  savedTemplates.value = readSavedQueries(currentUserId.value).queries
}

async function restoreQuery(value: GraphFilters): Promise<void> {
  Object.assign(filters, value)
  activeTab.value = 'basic'
  await nextTick()
  filters.centerId = value.centerId
  await loadCenter()
}

/** 详情页只传类型和规范ID；常用查询页通过当前账号的图谱 store 交接筛选。 */
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
onMounted(async () => {
  refreshSavedTemplates()
  const pending = graphStore.takeQuery()
  if (pending) {
    Object.assign(filters, pending)
    await nextTick()
    await loadCenter()
    return
  }
  await loadRouteCenter()
})
onBeforeUnmount(graphStore.reset)
</script>

<template>
  <section class="page-stack">
    <PageHeader
      title="高级查询"
      description="从作者和作品出发，探索创作关系与有共同作品依据的作者合作。"
    >
      <template #actions>
        <ElButton v-if="graph" plain @click="queryOpen = !queryOpen">{{ queryOpen ? '返回图谱' : '查询条件' }}</ElButton>
      </template>
    </PageHeader>

    <div v-show="queryOpen || !graph" class="graph-query-controls" :class="graph ? 'graph-query-controls--full' : ''">
    <div class="flex flex-wrap items-center gap-x-5 gap-y-2 text-sm text-muted-foreground">
      <RouterLink class="text-primary hover:underline" to="/graph/path">路径分析</RouterLink>
      <RouterLink class="text-primary hover:underline" to="/graph/queries">常用查询</RouterLink>
    </div>

    <GraphSavedQueries :filters="filters" @restore="restoreQuery" @changed="refreshSavedTemplates" />

    <!-- 查询模式标签页：按任务物理隔离，减少单屏表单数量 -->
    <ElTabs v-model="activeTab">
      <ElTabPane label="基础浏览" name="basic">
        <PanelSection>
          <div class="grid grid-cols-1 items-end gap-4 lg:grid-cols-[150px_minmax(0,1fr)_auto]">
            <FilterField label="中心类型">
              <ElSelect v-model="filters.centerType" placeholder="选择中心类型" filterable>
                <ElOption
                  v-for="item in nodeTypes"
                  :key="item.value"
                  :value="item.value"
                  :label="item.label"
                />
              </ElSelect>
            </FilterField>
            <GraphEntityPicker v-model="filters.centerId" :type="filters.centerType" label="中心" />
            <ElButton type="primary" :loading="loading" @click="loadCenter">加载图谱</ElButton>
          </div>

          <div class="mt-3">
            <ElButton
              text
              type="primary"
              :aria-expanded="scopeOpen"
              @click="scopeOpen = !scopeOpen"
            >
              <ChevronDown class="mr-1 size-4 transition-transform" :class="scopeOpen ? '' : '-rotate-90'" aria-hidden="true" />
              查询预置（展开/折叠选项）
            </ElButton>
            <span class="ml-2 text-xs text-muted-foreground">深度 ≤ 2 · 最多 300 个节点 · 成果统计以成果目录为准</span>
            <Transition name="content-switch">
            <div v-show="scopeOpen" class="mt-3 grid grid-cols-2 gap-4 lg:grid-cols-4">
              <FilterField label="查询深度">
                <ElSelect v-model="filters.depth" placeholder="选择深度" filterable>
                  <ElOption
                    v-for="item in depthOptions"
                    :key="item.value"
                    :value="item.value"
                    :label="item.label"
                  />
                </ElSelect>
              </FilterField>
              <FilterField label="本次节点上限">
                <ElSelect v-model="filters.nodeLimit" placeholder="选择节点上限" filterable>
                  <ElOption
                    v-for="item in nodeLimitOptions"
                    :key="item.value"
                    :value="item.value"
                    :label="item.label"
                  />
                </ElSelect>
              </FilterField>
              <FilterField label="起始年份">
                <YearPicker v-model="yearFromModel" aria-label="选择起始年份" />
              </FilterField>
              <FilterField label="结束年份">
                <YearPicker v-model="yearToModel" aria-label="选择结束年份" />
              </FilterField>
            </div>
            </Transition>
          </div>
        </PanelSection>
      </ElTabPane>

      <ElTabPane label="关系筛选" name="filters">
        <p class="text-sm leading-relaxed text-muted-foreground">
          类型过滤作用于基础浏览的加载与图内展开；空选择表示全部。
        </p>
        <PanelSection title="类型过滤" subtitle="节点、关系与成果类型均可多选，组合后由服务端过滤。" class="mt-3">
          <div class="grid grid-cols-1 gap-4 lg:grid-cols-3">
            <div class="space-y-2">
              <h3 class="text-sm font-medium text-muted-foreground">节点类型</h3>
              <div class="flex flex-wrap gap-1.5">
                <ElButton
                  v-for="item in nodeTypes"
                  :key="item.value"
                  round
                  size="small"
                  :type="filters.nodeTypes.includes(item.value) ? 'primary' : 'default'"
                  :plain="!filters.nodeTypes.includes(item.value)"
                  :aria-pressed="filters.nodeTypes.includes(item.value)"
                  @click="toggleNodeType(item.value)"
                >
                  {{ item.label }}
                </ElButton>
              </div>
              <span class="block text-xs text-muted-foreground/80">空选择表示全部节点</span>
            </div>
            <div class="space-y-2">
              <h3 class="text-sm font-medium text-muted-foreground">关系类型</h3>
              <div class="flex flex-wrap gap-1.5">
                <ElButton
                  v-for="item in relationshipTypes"
                  :key="item.value"
                  round
                  size="small"
                  :type="filters.relationshipTypes.includes(item.value) ? 'primary' : 'default'"
                  :plain="!filters.relationshipTypes.includes(item.value)"
                  :aria-pressed="filters.relationshipTypes.includes(item.value)"
                  @click="toggleRelationshipType(item.value)"
                >
                  {{ item.label }}
                </ElButton>
              </div>
              <span class="block text-xs text-muted-foreground/80">空选择表示全部关系</span>
            </div>
            <div class="space-y-2">
              <h3 class="text-sm font-medium text-muted-foreground">成果类型</h3>
              <div class="flex flex-wrap gap-1.5">
                <ElButton
                  v-for="item in achievementTypeOptions"
                  :key="item.value"
                  round
                  size="small"
                  :type="selectedAchievementTypes.includes(item.value) ? 'primary' : 'default'"
                  :plain="!selectedAchievementTypes.includes(item.value)"
                  :aria-pressed="selectedAchievementTypes.includes(item.value)"
                  @click="toggleAchievementType(item.value)"
                >
                  {{ item.value }}
                </ElButton>
              </div>
              <span class="block text-xs text-muted-foreground/80">空选择表示全部类型</span>
            </div>
          </div>
          <div class="mt-4 flex justify-end border-t border-border pt-4">
            <ElButton type="primary" :loading="loading" @click="loadCenter">应用过滤并加载</ElButton>
          </div>
        </PanelSection>
      </ElTabPane>

      <ElTabPane label="高级查询" name="advanced">
        <p class="text-sm leading-relaxed text-muted-foreground">
          指定起点与终点节点，查询图投影中的最短路径；结果以图形和表格呈现。
        </p>
        <PanelSection title="两点间最短路径" subtitle="最大跳数6；路径查询不会绕过服务端的 hop 上限。" class="mt-3">
          <GraphPathForm @submit="loadPath" />
        </PanelSection>
      </ElTabPane>
    </ElTabs>
    </div>

    <ElAlert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" show-icon />
    <ElAlert
      v-if="graph?.truncated"
      type="warning"
      :closable="false"
      :title="graph.narrowingSuggestion || '图结果已达到服务端限制，请缩小过滤范围。'"
      show-icon
    />

    <!-- 图工作区保留视图实例；窄屏通过抽屉查看元素详情。 -->
    <GraphWorkspace
      v-if="graph"
      v-show="!queryOpen"
      :graph="graph"
      :loading="loading"
      :added-node-ids="addedNodeIds"
      :focus="focus"
      @expand="expandSelected"
    />

    <!-- 空态 -->
    <LoadingSkeleton v-else-if="loading" :rows="6" />
    <PanelSection v-else>
      <div class="flex flex-col items-center gap-3 px-6 py-10 text-center">
        <Waypoints class="size-10 text-muted-foreground/50" aria-hidden="true" />
        <strong class="text-base font-semibold">快速开始查询</strong>
        <p class="max-w-md text-sm leading-relaxed text-muted-foreground">
          通过上方查询配置检索并定位特定节点，以开启局部图谱探索；关系筛选与高级查询标签页分别提供类型过滤和两点间最短路径。
        </p>
      </div>
      <div v-if="savedTemplates.length" class="border-t border-border px-4 pb-4 pt-3">
        <h3 class="text-sm font-medium">常用查询模板</h3>
        <div class="mt-2 grid gap-2 sm:grid-cols-2">
          <button
            v-for="item in savedTemplates.slice(0, 4)"
            :key="item.name"
            type="button"
            class="flex items-center gap-2 rounded-md border border-border bg-muted/30 px-3 py-2 text-left text-sm transition-colors hover:border-primary/50 hover:bg-accent"
            @click="restoreQuery(item.filters)"
          >
            <FileText class="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
            <span class="min-w-0 flex-1 truncate">{{ item.name }}</span>
            <span class="shrink-0 text-xs text-muted-foreground">{{ nodeTypeLabel(item.filters.centerType) }} #{{ item.filters.centerId }}</span>
          </button>
        </div>
      </div>
      <p v-else class="border-t border-border px-4 pb-4 pt-3 text-xs text-muted-foreground">
        暂无常用查询模板。在上方“常用查询”面板保存筛选后，可在此一键复用。
      </p>
    </PanelSection>
  </section>
</template>

<style scoped>
.graph-query-controls { display: grid; gap: var(--space-3); min-height: 0; max-height: 60%; overflow: auto; flex-shrink: 1; }
.graph-query-controls--full { flex: 1; max-height: none; }
</style>
