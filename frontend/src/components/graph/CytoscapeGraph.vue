<template>
  <div class="cytoscape-graph" ref="containerRef">
    <LoadingOverlay :visible="loading" text="加载图谱中..." />
    <div class="cytoscape-graph__canvas" ref="cyRef" />
    <slot />
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import cytoscape from 'cytoscape'
import { buildCytoscapeStyle, cytoscapeLayout } from '../../utils/graphStyle.js'
import LoadingOverlay from '../common/LoadingOverlay.vue'

const props = defineProps({
  graphData: { type: Object, default: null },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['node-click', 'edge-click', 'ready', 'node-limit-reached'])

const cyRef = ref(null)
const containerRef = ref(null)
let cy = null

function initGraph() {
  if (!cyRef.value) return

  cy = cytoscape({
    container: cyRef.value,
    style: buildCytoscapeStyle(),
    layout: cytoscapeLayout,
    wheelSensitivity: 0.3,
    minZoom: 0.2,
    maxZoom: 3
  })

  cy.on('tap', 'node', (evt) => {
    emit('node-click', evt.target.data())
  })

  cy.on('tap', 'edge', (evt) => {
    emit('edge-click', evt.target.data())
  })

  emit('ready', cy)
}

function updateGraph(data) {
  if (!cy || !data) return

  cy.elements().remove()

  const allNodes = (data.nodes || []).map(n => ({
    data: {
      id: n.data?.id || n.id,
      label: n.data?.label || n.label || '',
      nodeType: n.data?.type || n.type || 'ENTITY',
      ...n.data
    }
  }))

  const edges = (data.edges || []).map((e, i) => ({
    data: {
      id: e.data?.id || `e-${i}`,
      source: e.data?.source || e.source,
      target: e.data?.target || e.target,
      label: e.data?.label || e.label || '',
      edgeType: e.data?.type || e.type || ''
    }
  }))

  const nodes = allNodes.length > 300 ? allNodes.slice(0, 300) : allNodes
  if (allNodes.length > 300) {
    emit('node-limit-reached', { total: allNodes.length, shown: 300 })
  }

  cy.add([...nodes, ...edges])
  cy.layout(cytoscapeLayout).run()
  cy.fit()
}

onMounted(() => {
  initGraph()
  if (props.graphData) {
    updateGraph(props.graphData)
  }
})

watch(() => props.graphData, (val) => {
  updateGraph(val)
}, { deep: true })

onBeforeUnmount(() => {
  if (cy) {
    cy.destroy()
    cy = null
  }
})

defineExpose({
  getCy: () => cy,
  fit: () => cy?.fit(),
  zoom: (level) => cy?.zoom(level)
})
</script>

<style lang="scss" scoped>
.cytoscape-graph {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 400px;
  background: var(--bg);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  overflow: hidden;

  &__canvas {
    width: 100%;
    height: 100%;
  }
}
</style>
