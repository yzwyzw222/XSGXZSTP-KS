<script setup lang="ts">
import cytoscape, { type Core, type EventObject, type ElementDefinition } from 'cytoscape'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { useTheme } from '@/composables/useTheme'
import { cn } from '@/lib/utils'
import { prefersReducedMotion } from '@/utils/motion'

const props = withDefaults(defineProps<{
  elements: ElementDefinition[]
  rootNodeId?: string
  label: string
  height?: string
  loading?: boolean
  addedNodeIds?: string[]
}>(), { height: 'min(66vh, 700px)', loading: false })

const emit = defineEmits<{
  (e: 'select-node', id: string): void
  (e: 'select-edge', id: string): void
}>()

const container = ref<HTMLDivElement | null>(null)
const { isDark } = useTheme()
let cy: Core | null = null
let edgeFlowFrame: number | undefined
let edgeDashOffset = 0
const revealTimers: number[] = []

/** 读取图谱 token 并转换为 cytoscape 可用的 hsl() 字符串。 */
function graphColor(name: string, fallback: string): string {
  if (typeof window === 'undefined') return fallback
  const raw = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  if (!raw) return fallback
  if (raw.startsWith('#') || raw.startsWith('hsl') || raw.startsWith('rgb')) return raw
  return `hsl(${raw})`
}

function palette() {
  return {
    achievement: graphColor('--graph-achievement', '#38a8ff'),
    author: graphColor('--graph-author', '#27b9d5'),
    institution: graphColor('--graph-institution', '#f5a04b'),
    venue: graphColor('--graph-venue', '#a77af2'),
    topic: graphColor('--graph-topic', '#35c98c'),
    edge: graphColor('--graph-edge', '#4d6b89'),
    canvas: graphColor('--graph-canvas', '#08172a'),
    text: graphColor('--foreground', '#e9f4ff'),
    textBg: graphColor('--card', '#09172a'),
    focus: graphColor('--primary', '#5bc5ff'),
    border: isDark.value ? '#f4fbff' : '#0b2545',
  }
}

function buildStyle(c: ReturnType<typeof palette>) {
  return [
    {
      selector: 'node',
      style: {
        'background-color': c.topic,
        'border-color': c.border,
        'border-width': 2,
        color: c.text,
        label: 'data(label)',
        'font-family': 'Inter, Segoe UI, Microsoft YaHei, sans-serif',
        'font-size': 11,
        'text-background-color': c.textBg,
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
    { selector: 'node[nodeType = "ACHIEVEMENT"]', style: { 'background-color': c.achievement, width: 46, height: 46 } },
    { selector: 'node[nodeType = "AUTHOR"]', style: { 'background-color': c.author } },
    { selector: 'node[nodeType = "INSTITUTION"]', style: { 'background-color': c.institution, shape: 'round-rectangle' } },
    { selector: 'node[nodeType = "VENUE"]', style: { 'background-color': c.venue, shape: 'diamond' } },
    { selector: 'node[nodeType = "TOPIC"]', style: { 'background-color': c.topic, shape: 'hexagon' } },
    {
      selector: 'edge',
      style: {
        'curve-style': 'bezier',
        'line-color': c.edge,
        'target-arrow-color': c.edge,
        'target-arrow-shape': 'triangle',
        label: 'data(label)',
        color: c.text,
        'font-size': 8,
        'text-background-color': c.textBg,
        'text-background-opacity': 0.8,
        'text-background-padding': '2px',
        width: 1.3,
      },
    },
    { selector: 'node.is-dimmed', style: { opacity: 0.15 } },
    { selector: 'edge.is-dimmed', style: { opacity: 0.15 } },
    { selector: 'node.is-focused', style: { opacity: 1, 'border-color': c.border, 'border-width': 3 } },
    { selector: 'edge.is-focused', style: { opacity: 1, width: 3, 'line-color': c.focus, 'target-arrow-color': c.focus, 'line-style': 'dashed', 'line-dash-pattern': [7, 5] } },
    { selector: ':selected', style: { 'border-color': c.border, 'border-width': 4, 'line-color': c.focus, 'target-arrow-color': c.focus } },
  ]
}

function selectNode(event: EventObject): void {
  emit('select-node', event.target.id())
}
function selectEdge(event: EventObject): void {
  emit('select-edge', event.target.id())
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

function render(): void {
  if (!container.value) return
  if (!cy) {
    cy = cytoscape({
      container: container.value,
      minZoom: 0.25,
      maxZoom: 2.5,
      style: buildStyle(palette()) as cytoscape.StylesheetStyle[],
    })
    cy.on('tap', 'node', selectNode)
    cy.on('tap', 'edge', selectEdge)
    cy.on('mouseover', 'node', focusNode)
    cy.on('mouseout', 'node', clearNodeFocus)
  }
  cy.elements().remove()
  cy.add(props.elements)
  for (const nodeId of props.addedNodeIds ?? []) cy.getElementById(nodeId).style('opacity', 0)
  cy.one('layoutstop', () => revealAddedNodes(props.addedNodeIds ?? []))
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
  } as cytoscape.LayoutOptions).run()
  if (props.rootNodeId) {
    const root = cy.getElementById(props.rootNodeId)
    if (root.nonempty()) root.select()
  }
}

function restyle(): void {
  if (!cy) return
  cy.style(buildStyle(palette()) as cytoscape.StylesheetStyle[]).update()
}

function resize(): void {
  cy?.resize()
  cy?.fit(undefined, 42)
}

watch(() => props.elements, () => void nextTick(render), { deep: false })
watch(isDark, () => void nextTick(restyle))

onMounted(() => {
  void nextTick(render)
  window.addEventListener('resize', resize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  stopEdgeFlow()
  for (const timer of revealTimers.splice(0)) window.clearTimeout(timer)
  cy?.destroy()
  cy = null
})

defineExpose({ resize, fit: () => cy?.fit(undefined, 42) })
</script>

<template>
  <div
    ref="container"
    :class="cn('graph-canvas w-full rounded-lg', loading && 'opacity-60')"
    :style="{ height: props.height, minHeight: '430px', background: 'hsl(var(--graph-canvas))' }"
    role="img"
    :aria-label="label"
  />
</template>
