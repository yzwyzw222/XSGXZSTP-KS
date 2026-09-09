<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'

const props = defineProps({
  type: { type: String, required: true },
  items: { type: Array, required: true },
  label: { type: String, required: true },
})
const canvas = ref(null)
let observer

// 根据实际容器与像素密度绘制演示统计，缩放后保持图表清晰。
function draw() {
  const element = canvas.value
  if (!element) return
  const { width, height } = element.getBoundingClientRect()
  if (!width || !height) return
  const ratio = window.devicePixelRatio || 1
  element.width = Math.round(width * ratio)
  element.height = Math.round(height * ratio)
  const context = element.getContext('2d')
  if (!context) return
  context.scale(ratio, ratio)
  const items = props.items.filter(item => Number.isFinite(item.value) && item.value >= 0)
  if (!items.length) return
  if (props.type === 'donut') drawDonut(context, width, height, items)
  else drawLine(context, width, height, items)
}

function drawDonut(context, width, height, items) {
  const total = items.reduce((sum, item) => sum + item.value, 0)
  if (!total) return
  const radius = Math.min(width, height) * 0.46
  let angle = -Math.PI / 2
  for (const item of items) {
    const end = angle + item.value / total * Math.PI * 2
    context.beginPath()
    context.arc(width / 2, height / 2, radius, angle, end)
    context.arc(width / 2, height / 2, radius * 0.53, end, angle, true)
    context.closePath()
    context.fillStyle = item.color
    context.fill()
    context.strokeStyle = '#071c36'
    context.lineWidth = 1
    context.stroke()
    angle = end
  }
}

function drawLine(context, width, height, items) {
  const left = 29
  const right = width - 14
  const top = 12
  const bottom = height - 26
  const max = Math.max(30, ...items.map(item => item.value))
  const points = items.map((item, index) => ({ x: left + index * (right - left) / Math.max(1, items.length - 1), y: bottom - item.value / max * (bottom - top) }))
  context.font = '10px "Microsoft YaHei", sans-serif'
  context.lineWidth = 0.6
  for (let index = 0; index <= 3; index++) {
    const y = bottom - index / 3 * (bottom - top)
    context.strokeStyle = '#204568'
    context.setLineDash([2, 3])
    context.beginPath()
    context.moveTo(left, y)
    context.lineTo(right, y)
    context.stroke()
    context.fillStyle = '#a6c8ee'
    context.textAlign = 'right'
    context.fillText(index === 0 ? '0' : `${Math.round(max * index / 3)}万`, left - 7, y + 4)
  }
  items.forEach((item, index) => {
    context.beginPath()
    context.moveTo(points[index].x, top)
    context.lineTo(points[index].x, bottom)
    context.stroke()
    context.textAlign = 'center'
    context.fillText(item.label, points[index].x, height - 5)
  })
  context.setLineDash([])
  context.beginPath()
  context.moveTo(points[0].x, bottom)
  points.forEach(point => context.lineTo(point.x, point.y))
  context.lineTo(points.at(-1).x, bottom)
  context.closePath()
  context.fillStyle = '#187ec633'
  context.fill()
  context.beginPath()
  points.forEach((point, index) => index === 0 ? context.moveTo(point.x, point.y) : context.lineTo(point.x, point.y))
  context.strokeStyle = '#66d8ff'
  context.lineWidth = 2
  context.shadowColor = '#188dff'
  context.shadowBlur = 8
  context.stroke()
  points.forEach(point => {
    context.beginPath()
    context.arc(point.x, point.y, 3.4, 0, Math.PI * 2)
    context.fillStyle = '#d6fbff'
    context.fill()
  })
}

onMounted(() => {
  observer = new ResizeObserver(draw)
  observer.observe(canvas.value)
  draw()
})
watch(() => props.items, draw, { deep: true, flush: 'post' })
onUnmounted(() => observer?.disconnect())
</script>

<template>
  <canvas ref="canvas" class="portal-chart" :class="`chart-${type}`" role="img" :aria-label="label">{{ label }}</canvas>
</template>
