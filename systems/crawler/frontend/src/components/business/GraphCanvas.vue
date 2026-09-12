<script setup lang="ts">
import cytoscape, { type Core, type EventObject, type ElementDefinition, type CollectionReturnValue } from 'cytoscape'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { useMotion } from '@/composables/useMotion'
import { useTheme } from '@/composables/useTheme'
import { reconcileGraphElements } from '@/utils/graph-rendering'

const props = withDefaults(defineProps<{
  elements: ElementDefinition[]
  rootNodeId?: string
  selectedNodeId?: string
  selectedEdgeId?: string
  label: string
  height?: string
  fill?: boolean
  loading?: boolean
  addedNodeIds?: string[]
  layout?: 'concentric' | 'network' | 'cooperation'
  scopeKey?: string
  positions?: Record<string, { x: number; y: number }>
  compact?: boolean
  drilldown?: boolean
}>(), { height: 'min(58vh, 620px)', loading: false })

const emit = defineEmits<{
  (e: 'select-node', id: string): void
  (e: 'select-edge', id: string): void
  (e: 'clear-selection'): void
  (e: 'double-click-node', id: string): void
  (e: 'context-menu', value: { kind: 'node' | 'edge'; id: string; x: number; y: number }): void
  (e: 'dismiss-context'): void
}>()

const container = ref<HTMLDivElement | null>(null)
const { isDark } = useTheme()
const { reducedMotion, duration } = useMotion()
let cy: Core | null = null
let resizeObserver: ResizeObserver | null = null
let resizeFrame: number | undefined
let mounted = false
let currentScope: string | undefined
let laidOut = false
const positions = new Map<string, { x: number; y: number }>()

/** Cytoscape 使用逗号分隔的 HSL 语法，与页面共享同一组语义颜色。 */
function graphColor(name: string, fallback: string): string {
  const raw = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  if (!raw) return fallback
  if (raw.startsWith('#') || raw.startsWith('hsl') || raw.startsWith('rgb')) return raw
  return `hsl(${raw.split(/\s+/).join(', ')})`
}

function buildStyle(): cytoscape.StylesheetStyle[] {
  const primary = graphColor('--primary', '#2363b8')
  const edge = graphColor('--graph-edge', '#7690a8')
  const surface = graphColor('--card', '#ffffff')
  const text = graphColor('--foreground', '#1d2939')
  const transition = {
    'transition-property': 'opacity, line-color, target-arrow-color, border-color',
    'transition-duration': `${duration('fast')}ms`,
  }
  return [
    { selector: 'node', style: {
      'background-color': graphColor('--graph-topic', '#278566'),
      'border-color': surface, 'border-width': 2,
      color: text, label: 'data(label)',
      'font-family': getComputedStyle(document.documentElement).getPropertyValue('--font-sans').trim().replace(/'/g, '"'),
      'font-size': 12, 'text-background-color': surface, 'text-background-opacity': .92,
      'text-background-padding': '3px', 'text-max-width': '150px',
      'text-valign': 'bottom', 'text-margin-y': 10, 'text-wrap': 'ellipsis',
      width: 30, height: 30, ...transition,
    } },
    { selector: 'node[nodeType = "ACHIEVEMENT"]', style: { 'background-color': graphColor('--graph-achievement', primary), width: 40, height: 40 } },
    { selector: 'node[nodeType = "AUTHOR"]', style: { 'background-color': graphColor('--graph-author', '#258ca3') } },
    { selector: 'node[nodeType = "INSTITUTION"]', style: { 'background-color': graphColor('--graph-institution', '#ba7b35'), shape: 'round-rectangle' } },
    { selector: 'node[nodeType = "VENUE"]', style: { 'background-color': graphColor('--graph-venue', '#8365b6'), shape: 'diamond' } },
    { selector: 'node[nodeType = "TOPIC"]', style: { shape: 'hexagon' } },
    { selector: 'edge', style: {
      'curve-style': 'bezier', 'line-color': edge, 'target-arrow-color': edge,
      'target-arrow-shape': 'triangle', 'arrow-scale': .8,
      label: 'data(label)', color: text, 'font-size': 10,
      'text-background-color': surface, 'text-background-opacity': .92,
      'text-background-padding': '3px', width: 1.2, ...transition,
    } },
    { selector: 'node[displayColor]', style: { 'background-color': 'data(displayColor)' } },
    { selector: 'node[displaySize]', style: { width: 'data(displaySize)', height: 'data(displaySize)' } },
    { selector: 'edge[displayColor]', style: { 'line-color': 'data(displayColor)', 'target-arrow-color': 'data(displayColor)' } },
    { selector: 'edge[displaySize]', style: { width: 'data(displaySize)' } },
    { selector: 'node[reviewStatus = "PENDING"]', style: { 'border-style': 'dashed' } },
    { selector: 'node[reviewStatus = "REJECTED"]', style: { 'border-style': 'dotted' } },
    { selector: 'edge[reviewStatus = "PENDING"]', style: { 'line-style': 'dotted' } },
    { selector: 'edge[reviewStatus = "REJECTED"]', style: { 'line-style': 'dashed' } },
    { selector: 'edge[relationshipType = "COAUTHORED"]', style: {
      'target-arrow-shape': 'none', 'line-style': 'dashed', 'text-wrap': 'wrap', 'text-max-width': '190px',
    } },
    { selector: 'edge[labelMode = "interaction"]', style: { label: '' } },
    { selector: 'edge[labelMode = "interaction"]:selected, edge[labelMode = "interaction"].is-hovered', style: { label: 'data(label)' } },
    ...(props.compact ? [{ selector: 'node', style: {
      shape: 'ellipse', 'text-valign': 'center', 'text-margin-y': 0,
      'text-background-opacity': 0, color: '#ffffff', 'font-size': 11, 'min-zoomed-font-size': 6,
      label: (node: cytoscape.NodeSingular) => {
        const label = Array.from(String(node.data('label')))
        return label.slice(0, 4).join('') + (label.length > 4 ? '…' : '')
      },
    } }] : []),
    { selector: '.is-dimmed', style: { opacity: .22 } },
    { selector: 'edge.is-focused', style: { 'line-color': primary, 'target-arrow-color': primary, width: 2 } },
    { selector: 'node:selected', style: { 'border-color': primary, 'border-width': 3, 'border-opacity': 1 } },
    { selector: 'edge:selected', style: { 'line-color': primary, 'target-arrow-color': primary, width: 3 } },
  ] as cytoscape.StylesheetStyle[]
}

function selection(): CollectionReturnValue | undefined {
  const id = props.selectedNodeId || props.selectedEdgeId
  return id ? cy?.getElementById(id) : undefined
}

/** 选中合作线时同时高亮共同作品及双方创作路径，避免把证据节点淡出。 */
function relatedElements(element: CollectionReturnValue): CollectionReturnValue {
  if (element.group() === 'nodes') return element.closedNeighborhood()
  let related = element.union(element.connectedNodes())
  const ids = element.data('evidenceIds')
  if (cy && Array.isArray(ids)) for (const id of ids) {
    if (typeof id === 'string') related = related.union(cy.getElementById(id))
  }
  return related
}

function focusElements(element = selection()): void {
  if (!cy) return
  cy.batch(() => {
    cy!.elements().removeClass('is-dimmed is-focused')
    if (!element?.nonempty()) return
    const related = relatedElements(element)
    cy!.elements().difference(related).addClass('is-dimmed')
    related.addClass('is-focused')
  })
}

function syncSelection(): void {
  if (!cy) return
  cy.elements(':selected').unselect()
  selection()?.select()
  focusElements()
}

function stopAnimations(jumpToEnd = false): void {
  cy?.stop(true, jumpToEnd)
  cy?.elements().stop(true, jumpToEnd)
}

function fit(elements?: CollectionReturnValue, animated = true): void {
  if (!cy || cy.nodes().empty()) return
  cy.stop(true, false)
  const visible = elements?.nonempty() ? elements : cy.elements()
  // 先计算目标视口再恢复当前位置，镜头移动只响应明确的聚焦或重置操作。
  const previous = { pan: { ...cy.pan() }, zoom: cy.zoom() }
  cy.fit(visible, 48)
  if (cy.zoom() > 1.2) { cy.zoom(1.2); cy.center(visible) }
  const target = { pan: { ...cy.pan() }, zoom: cy.zoom() }
  if (animated && !reducedMotion.value) {
    cy.viewport(previous)
    cy.animate(target, { duration: duration('slow'), easing: 'ease-out-cubic', queue: false })
  }
}

function render(): void {
  if (!mounted || !container.value?.clientWidth || !container.value.clientHeight) return
  if (!cy) {
    cy = cytoscape({
      container: container.value, minZoom: .15, maxZoom: 2.5, style: buildStyle(),
      selectionType: 'single',
    })
    cy.on(props.drilldown ? 'onetap' : 'tap', 'node', (event: EventObject) => emit('select-node', event.target.id()))
    cy.on(props.drilldown ? 'onetap' : 'tap', 'edge', (event: EventObject) => emit('select-edge', event.target.id()))
    cy.on('dbltap', 'node', (event: EventObject) => { if (props.drilldown) emit('double-click-node', event.target.id()) })
    cy.on('cxttap', 'node, edge', (event: EventObject) => {
      if (!props.drilldown) return
      emit('context-menu', { kind: event.target.isNode() ? 'node' : 'edge', id: event.target.id(), ...event.renderedPosition })
    })
    cy.on('pan zoom drag', () => emit('dismiss-context'))
    cy.on('tap', event => { if (event.target === cy) emit('clear-selection') })
    cy.on('mouseover', 'node', (event: EventObject) => {
      focusElements(event.target)
      if (container.value) container.value.title = String(event.target.data('label'))
    })
    cy.on('mouseout', 'node', () => { focusElements(); if (container.value) container.value.title = '' })
    cy.on('mouseover', 'edge', (event: EventObject) => { event.target.addClass('is-hovered') })
    cy.on('mouseout', 'edge', (event: EventObject) => { event.target.removeClass('is-hovered') })
  }
  stopAnimations()
  if (currentScope !== props.scopeKey) {
    currentScope = props.scopeKey
    cy.elements().remove()
    positions.clear()
    laidOut = false
  }
  cy.nodes().forEach(node => { positions.set(node.id(), { ...node.position() }) })
  while (positions.size > 300) positions.delete(positions.keys().next().value!)
  const change = reconcileGraphElements(cy, props.elements, props.rootNodeId)
  if (change.initial && (!laidOut || props.scopeKey === undefined) && cy.nodes().nonempty()) {
    if (props.scopeKey === undefined) positions.clear()
    if (props.positions) {
      cy.nodes().positions(node => props.positions?.[node.id()] ?? { x: 0, y: 0 })
    } else if (props.layout === 'cooperation') {
      // 合作详情独立排列作者和共同作品，避免复用全网坐标后挤在同一区域。
      cy.nodes('[nodeType = "AUTHOR"]').positions((_node, index) => ({ x: index * 360, y: 0 }))
      cy.nodes('[nodeType = "ACHIEVEMENT"]').positions((_node, index) => ({ x: 180, y: 180 + index * 160 }))
    } else if (props.layout === 'network') {
      const columns = Math.ceil(Math.sqrt(cy.nodes().length))
      cy.nodes().positions((_node, index) => ({ x: index % columns * 120, y: Math.floor(index / columns) * 120 }))
      cy.layout({
        name: 'cose', animate: false, fit: false, randomize: false,
        componentSpacing: 120, nodeRepulsion: () => 8000,
        idealEdgeLength: () => 100, nodeOverlap: 24, padding: 48,
      }).run()
      if (props.compact) {
        // 扩展较短的坐标轴以适应宽屏，不压缩节点间距或改变关系。
        const box = cy.nodes().boundingBox({ includeLabels: false })
        const aspect = Math.max(0.5, (container.value.clientWidth - 96) / Math.max(1, container.value.clientHeight - 96))
        if (box.w > 0 && box.h > 0) {
          const scaleX = Math.max(1, box.h * aspect / box.w)
          const scaleY = Math.max(1, box.w / aspect / box.h)
          cy.nodes().positions(node => ({ x: node.position('x') * scaleX, y: node.position('y') * scaleY }))
        }
      }
    }
    else cy.layout({
      name: 'concentric', animate: false, fit: false, padding: 48,
      minNodeSpacing: 72, avoidOverlap: true,
      concentric: node => node.id() === props.rootNodeId ? 1000 : node.degree(),
      levelWidth: () => 1000,
    }).run()
    fit(undefined, false)
    laidOut = true
  } else {
    for (const id of change.addedIds) {
      const position = positions.get(id)
      if (position) { cy.getElementById(id).position(position); change.targets.delete(id) }
    }
    for (const [id, position] of change.targets) {
      const node = cy.getElementById(id)
      if (reducedMotion.value) node.position(position)
      else node.animate({ position }, { duration: duration('slow'), easing: 'ease-out-cubic', queue: false })
    }
  }
  syncSelection()
}

function resize(): void {
  if (resizeFrame !== undefined) cancelAnimationFrame(resizeFrame)
  resizeFrame = requestAnimationFrame(() => {
    resizeFrame = undefined
    if (!mounted || !container.value?.clientWidth) return
    if (cy) cy.resize()
    else render()
  })
}

function restyle(): void { cy?.style(buildStyle()).update() }
function visibilityChanged(): void { if (document.hidden) stopAnimations(true) }

watch(() => [props.elements, props.scopeKey, props.positions], render, { flush: 'post' })
watch(() => [props.selectedNodeId, props.selectedEdgeId], syncSelection, { flush: 'post' })
watch(isDark, restyle, { flush: 'post' })
watch(reducedMotion, () => { stopAnimations(true); restyle() }, { flush: 'post' })
onMounted(() => {
  mounted = true
  render()
  if (typeof ResizeObserver === 'function' && container.value) {
    resizeObserver = new ResizeObserver(resize)
    resizeObserver.observe(container.value)
  } else window.addEventListener('resize', resize)
  document.addEventListener('visibilitychange', visibilityChanged)
})
onBeforeUnmount(() => {
  mounted = false
  if (resizeFrame !== undefined) cancelAnimationFrame(resizeFrame)
  resizeObserver?.disconnect()
  window.removeEventListener('resize', resize)
  document.removeEventListener('visibilitychange', visibilityChanged)
  stopAnimations()
  cy?.destroy()
  cy = null
})

defineExpose({
  resize, fit: () => { cy?.resize(); fit() },
  focus: (id?: string) => {
    const selected = id ? cy?.getElementById(id) : selection()
    if (selected?.nonempty()) fit(relatedElements(selected))
  },
})
</script>

<template>
  <div
    ref="container"
    class="graph-canvas w-full"
    :class="{ 'opacity-60': loading }"
    :style="{ height: fill ? '100%' : props.height, minHeight: fill ? '0' : '360px', background: 'hsl(var(--graph-canvas))' }"
    role="img"
    :aria-label="label"
    :aria-busy="loading"
    tabindex="0"
    @contextmenu.prevent
    @keydown.esc="emit('clear-selection'); emit('dismiss-context')"
  />
</template>
