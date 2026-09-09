<script setup lang="ts">
import { DataSet } from 'vis-data'
import { Network, type Edge, type Node, type Options, type NetworkEvents } from 'vis-network/peer'
import 'vis-network/styles/vis-network.css'
import { onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, shallowRef, watch } from 'vue'

import { useMotion } from '@/composables/useMotion'
import { useTheme } from '@/composables/useTheme'
import type { CanvasGraph } from '@/utils/graph-vis'

const props = withDefaults(defineProps<{
  data: CanvasGraph
  label: string
  scopeKey: string
  loading?: boolean
  selectedNodeId?: string
  selectedEdgeId?: string
  allowCreateRelation?: boolean
  positions?: Record<string, { x: number; y: number }>
}>(), { loading: false, allowCreateRelation: false })
const emit = defineEmits<{
  'select-node': [id: string]
  'select-edge': [id: string]
  'double-click-node': [id: string]
  'clear-selection': []
  'context-menu': [value: { kind: 'node' | 'edge'; id: string; x: number; y: number }]
  'create-edge': [value: { from: string; to: string }]
  'cancel-create-edge': []
  'dismiss-context': []
}>()
const container = ref<HTMLDivElement | null>(null)
const network = shallowRef<Network | null>(null)
const nodes = new DataSet<Node & { heightConstraint?: { minimum: number } }>()
const edges = new DataSet<Edge>()
const nodeSignatures = new Map<string, string>()
const edgeSignatures = new Map<string, string>()
const { isDark } = useTheme()
const { reducedMotion, duration } = useMotion()
let active = false
let scopeKey: string | undefined
let observer: ResizeObserver | undefined
let frame: number | undefined
let clickTimer: ReturnType<typeof setTimeout> | undefined
let pendingEdge: ((edge: Edge | null) => void) | undefined
let addingEdge = false
let fitAfterStabilization = false
let previousWidth = 0
const stabilizationIterations = 400
type Interaction = { nodes: Array<string | number>; edges: Array<string | number>; pointer: { DOM: { x: number; y: number } }; event: MouseEvent }

/** vis 的字符串提示会作为 HTML 使用，统一通过 textContent 构造纯文本节点。 */
function tooltip(text: string): HTMLElement {
  const element = document.createElement('div')
  element.textContent = text
  element.style.whiteSpace = 'pre-wrap'
  element.style.maxWidth = '320px'
  element.style.overflowWrap = 'anywhere'
  return element
}

function colors(): Pick<Options, 'edges'> {
  return { edges: { font: { color: isDark.value ? '#e2e8f0' : '#334155', strokeWidth: 3,
    strokeColor: isDark.value ? '#182538' : '#ffffff', size: 12, align: 'middle' } } }
}

/** 临时连线始终以 null 结束；持久化成功后的业务 props 才能生成真实关系。 */
function finishRelation(): void {
  const callback = pendingEdge
  pendingEdge = undefined
  const wasAdding = addingEdge
  addingEdge = false
  callback?.(null)
  if (wasAdding) { network.value?.disableEditMode(); emit('cancel-create-edge') }
}
function startRelation(): void {
  if (!active || props.loading || !props.allowCreateRelation) return
  finishRelation()
  addingEdge = true
  network.value?.addEdgeMode()
}

function options(): Options {
  return {
    autoResize: true, width: '100%', height: '100%',
    nodes: { shape: 'circle', font: { size: 14, color: '#fff' }, borderWidth: 2, margin: { top: 5, bottom: 5, left: 5, right: 5 } },
    edges: { ...colors().edges, arrows: 'to', smooth: { enabled: true, type: 'continuous', roundness: .2 } },
    interaction: { hover: true, dragNodes: true, dragView: true, zoomView: true, multiselect: false, tooltipDelay: 200 },
    layout: { improvedLayout: false, randomSeed: 17 },
    physics: { enabled: true, stabilization: { enabled: true, iterations: stabilizationIterations, updateInterval: 50, fit: false },
      barnesHut: { gravitationalConstant: -8000, centralGravity: .03, springLength: 220, springConstant: .02, avoidOverlap: 1 } },
    manipulation: {
      enabled: false, addNode: false, editEdge: false, deleteNode: false, deleteEdge: false,
      addEdge: (edge: Edge, callback: (value: Edge | null) => void) => {
        if (!addingEdge || !props.allowCreateRelation || props.loading || edge.from === undefined || edge.to === undefined) { callback(null); return }
        pendingEdge?.(null)
        pendingEdge = callback
        emit('create-edge', { from: String(edge.from), to: String(edge.to) })
      },
    },
  }
}

function fit(): void {
  if (!active || !nodes.length) return
  network.value?.fit({ animation: reducedMotion.value ? false : { duration: duration('slow'), easingFunction: 'easeOutCubic' } })
}

function syncSelection(): void {
  network.value?.setSelection({
    nodes: props.selectedNodeId && nodes.get(props.selectedNodeId) ? [props.selectedNodeId] : [],
    edges: props.selectedEdgeId && edges.get(props.selectedEdgeId) ? [props.selectedEdgeId] : [],
  }, { unselectAll: true, highlightEdges: false })
}

/** DataSet 仅为渲染副本；同一范围的更新保留位置和视角，删除时先移除边。 */
function syncData(): void {
  if (!active || !network.value) return
  const changedScope = scopeKey !== props.scopeKey
  scopeKey = props.scopeKey
  const nextNodes = new Set(props.data.nodes.map(node => node.id))
  const nextEdges = new Set(props.data.edges.map(edge => edge.id))
  const oldPositions = network.value.getPositions()
  if (changedScope) { clearTimeout(clickTimer); finishRelation() }
  for (const id of edges.getIds()) if (!nextEdges.has(String(id))) { edges.remove(id); edgeSignatures.delete(String(id)) }
  for (const id of nodes.getIds()) if (!nextNodes.has(String(id))) { nodes.remove(id); nodeSignatures.delete(String(id)) }
  const initial = !nodes.length && props.data.nodes.length > 0
  const aspectRatio = (container.value?.clientWidth || 640) / (container.value?.clientHeight || 520)
  const columns = Math.max(1, Math.ceil(Math.sqrt(props.data.nodes.length * aspectRatio)))
  const rows = Math.ceil(props.data.nodes.length / columns)
  const spacing = Math.max(180, ...props.data.nodes.map(node => Math.max(node.widthConstraint, node.heightConstraint) + 100))
  for (const [index, node] of props.data.nodes.entries()) {
    const position = props.positions?.[node.id]
    const signature = JSON.stringify([node, position])
    if (nodeSignatures.get(node.id) === signature && !changedScope) continue
    const neighbor = props.data.edges.find(edge => edge.from === node.id || edge.to === node.id)
    const anchor = neighbor ? oldPositions[neighbor.from === node.id ? neighbor.to : neighbor.from] : undefined
    // 首次和换图按二维网格铺开；同一范围仍优先保留用户已拖动的位置。
    const spread = { x: (index % columns - (columns - 1) / 2) * spacing, y: (Math.floor(index / columns) - (rows - 1) / 2) * spacing }
    const existing = initial || changedScope ? undefined : oldPositions[node.id]
    const nearby = !initial && !changedScope && anchor
      ? { x: anchor.x + Math.cos(index * 2.4) * spacing, y: anchor.y + Math.sin(index * 2.4) * spacing } : undefined
    const start = position ?? existing ?? nearby ?? spread
    nodes.update({ ...node, ...start, title: tooltip(node.title),
      color: { background: node.color, border: node.color, highlight: { background: node.color, border: '#f59e0b' }, hover: { background: node.color, border: '#f59e0b' } },
      widthConstraint: node.widthConstraint, heightConstraint: { minimum: node.heightConstraint },
    })
    nodeSignatures.set(node.id, signature)
  }
  for (const edge of props.data.edges) {
    const signature = JSON.stringify(edge)
    if (edgeSignatures.get(edge.id) === signature) continue
    edges.update({ ...edge, title: tooltip(edge.title),
      arrows: { to: { enabled: edge.directed } }, dashes: !edge.directed,
      color: { color: edge.color, highlight: '#f59e0b', hover: '#f59e0b', inherit: false },
    })
    edgeSignatures.set(edge.id, signature)
  }
  syncSelection()
  if (initial || changedScope) {
    fitAfterStabilization = true
    if (props.positions || document.hidden) {
      network.value.setOptions({ physics: { enabled: false } })
      fitAfterStabilization = false
      fit()
    } else {
      network.value.setOptions({ physics: { enabled: true } })
      network.value.stabilize(stabilizationIterations)
    }
  }
}

function stabilized(): void {
  network.value?.setOptions({ physics: { enabled: false } })
  if (fitAfterStabilization) { fitAfterStabilization = false; fit() }
}
function click(event: Interaction): void {
  emit('dismiss-context')
  clearTimeout(clickTimer)
  if (addingEdge || props.loading) return
  clickTimer = setTimeout(() => {
    if (!active) return
    if (event.nodes.length && nodes.get(event.nodes[0]!)) emit('select-node', String(event.nodes[0]))
    else if (event.edges.length && edges.get(event.edges[0]!)) emit('select-edge', String(event.edges[0]))
    else emit('clear-selection')
  }, 250)
}
function doubleClick(event: Interaction): void {
  clearTimeout(clickTimer)
  if (active && !addingEdge && !props.loading && event.nodes.length) emit('double-click-node', String(event.nodes[0]))
}
function contextMenu(event: Interaction): void {
  event.event.preventDefault()
  clearTimeout(clickTimer)
  if (!active || addingEdge || props.loading) return
  const point = event.pointer.DOM
  const node = network.value?.getNodeAt(point)
  const edge = node === undefined ? network.value?.getEdgeAt(point) : undefined
  if (node !== undefined || edge !== undefined) emit('context-menu', {
    kind: node !== undefined ? 'node' : 'edge', id: String(node ?? edge), x: point.x, y: point.y,
  })
  else emit('dismiss-context')
}
const listeners: Array<[NetworkEvents, (event: Interaction) => void]> = [
  ['click', click], ['doubleClick', doubleClick], ['oncontext', contextMenu],
  ['stabilizationIterationsDone', stabilized], ['stabilized', stabilized],
  ['dragStart', () => { emit('dismiss-context'); clearTimeout(clickTimer) }], ['zoom', () => emit('dismiss-context')],
]
function resize(): void {
  if (frame !== undefined) cancelAnimationFrame(frame)
  frame = requestAnimationFrame(() => {
    frame = undefined
    if (active && container.value?.clientWidth && container.value.clientHeight) {
      const width = container.value.clientWidth
      network.value?.redraw()
      // 跨桌面与窄屏尺寸时重新适配，避免自动缩放把节点压成不可读的小点。
      if (previousWidth && (width / previousWidth < .75 || width / previousWidth > 1.5)) network.value?.fit({ animation: false })
      previousWidth = width
    }
  })
}
function pause(): void {
  active = false
  clearTimeout(clickTimer)
  if (frame !== undefined) cancelAnimationFrame(frame)
  frame = undefined
  observer?.disconnect()
  finishRelation()
  stopMotion()
}
/** 停用和减少动画时同时结束物理模拟与尚未完成的镜头动画。 */
function stopMotion(): void {
  const instance = network.value
  if (!instance) return
  instance.moveTo({ position: instance.getViewPosition(), scale: instance.getScale(), animation: false })
  instance.stopSimulation()
}
function visibilityChanged(): void {
  if (document.hidden) { stopMotion(); clearTimeout(clickTimer); finishRelation() }
  else if (active) resize()
}
watch(() => [props.data, props.scopeKey, props.positions], syncData, { flush: 'post' })
watch(() => [props.selectedNodeId, props.selectedEdgeId], syncSelection, { flush: 'post' })
watch(() => [props.loading, props.allowCreateRelation], () => { if (props.loading || !props.allowCreateRelation) finishRelation() })
watch(isDark, () => network.value?.setOptions(colors()))
watch(reducedMotion, () => { if (reducedMotion.value) { stopMotion(); resize() } })
onMounted(() => {
  if (!container.value) return
  active = true
  previousWidth = container.value.clientWidth
  network.value = new Network(container.value, { nodes, edges }, options())
  listeners.forEach(([name, listener]) => network.value!.on(name, listener))
  if (typeof ResizeObserver === 'function') { observer = new ResizeObserver(resize); observer.observe(container.value) }
  window.addEventListener('resize', resize)
  document.addEventListener('visibilitychange', visibilityChanged)
  syncData()
})
onDeactivated(pause)
onActivated(() => { active = true; if (container.value) observer?.observe(container.value); syncData(); resize() })
onBeforeUnmount(() => {
  pause()
  window.removeEventListener('resize', resize)
  document.removeEventListener('visibilitychange', visibilityChanged)
  listeners.forEach(([name, listener]) => network.value?.off(name, listener))
  network.value?.destroy()
  network.value = null
  edges.clear(); nodes.clear(); nodeSignatures.clear(); edgeSignatures.clear()
})
defineExpose({ fit, resize, startRelation, finishRelation })
</script>

<template>
  <div ref="container" class="graph-canvas" role="img" :aria-label="label" :aria-busy="loading" tabindex="0" @keydown.esc="finishRelation" />
</template>

<style scoped>
.graph-canvas { width: 100%; height: 100%; min-height: 360px; background: hsl(var(--card)); }
</style>
