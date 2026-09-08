<script setup lang="ts">
import { ElAlert, ElButton } from 'element-plus'
import { onBeforeUnmount, ref, watch } from 'vue'

import { PageHeader, PanelSection } from '@/components/business'
import GraphPathForm from '@/components/business/GraphPathForm.vue'
import GraphWorkspace from '@/components/business/GraphWorkspace.vue'
import type { GraphPathQuery } from '@/components/business/types'
import { storeToRefs } from 'pinia'
import { useGraphStore } from '@/stores/graph'
import { graphApi } from '@/services/business'
import type { GraphNode } from '@/types/api'
import { GRAPH_NODE_LIMIT } from '@/utils/graph'

const graphStore = useGraphStore()
graphStore.reset()
const { loading, errorMessage, graph, addedNodeIds, focus } = storeToRefs(graphStore)
const loadGraph = graphStore.load
const queryOpen = ref(false)
watch(graph, value => { if (value) queryOpen.value = false })

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

/** 路径结果同样支持从选中节点继续展开，展开时不附加类型过滤。 */
async function expandSelected(node: GraphNode): Promise<void> {
  if ((graph.value?.nodes.length ?? 0) >= GRAPH_NODE_LIMIT) return
  const businessId = Number(node.businessId)
  if (!Number.isSafeInteger(businessId) || businessId < 1) return
  await loadGraph(() => graphApi.subgraph({
    centerType: node.type,
    centerId: businessId,
    depth: 1,
    nodeLimit: Math.min(100, GRAPH_NODE_LIMIT - (graph.value?.nodes.length ?? 0)),
  }), true)
}


onBeforeUnmount(graphStore.reset)
</script>

<template>
  <section class="page-stack">
    <PageHeader
      title="路径分析"
      description="指定起点与终点节点，查询图投影中的最短路径；hop 上限为6，结果可继续展开探索。"
    ><template #actions><ElButton v-if="graph" plain @click="queryOpen = !queryOpen">{{ queryOpen ? '返回图谱' : '路径配置' }}</ElButton></template></PageHeader>

    <PanelSection v-show="queryOpen || !graph" title="路径配置" subtitle="起点与终点均可按名称检索或直接填写业务ID。">
      <GraphPathForm @submit="loadPath" />
    </PanelSection>

    <ElAlert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" show-icon />
    <ElAlert
      v-if="graph?.truncated"
      type="warning"
      :closable="false"
      :title="graph.narrowingSuggestion || '图结果已达到服务端限制，请缩小过滤范围。'"
      show-icon
    />

    <GraphWorkspace
      v-if="graph"
      v-show="!queryOpen"
      :graph="graph"
      :loading="loading"
      :added-node-ids="addedNodeIds"
      :focus="focus"
      @expand="expandSelected"
    />

    <PanelSection v-else>
      <div class="flex flex-col items-center gap-3 px-6 py-14 text-center">
        <strong class="text-base font-semibold">配置一条路径查询</strong>
        <p class="max-w-md text-sm leading-relaxed text-muted-foreground">
          选择起点与终点类型并检索名称，或直接填写业务ID；查询结果将以图形和表格两种视图呈现。
        </p>
      </div>
    </PanelSection>
  </section>
</template>
