<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import { easeOutCubic, prefersReducedMotion } from '@/utils/motion'

const props = withDefaults(defineProps<{
  value: number
  duration?: number
  suffix?: string
}>(), {
  duration: 600,
  suffix: '',
})

const displayedValue = ref(0)
let animationFrame: number | undefined

const formattedValue = computed(() =>
  `${Math.round(displayedValue.value).toLocaleString('zh-CN')}${props.suffix}`,
)

/** 关键指标从当前值平滑过渡，避免接口刷新造成视觉跳变。 */
function animateTo(target: number): void {
  if (animationFrame !== undefined) {
    window.cancelAnimationFrame(animationFrame)
    animationFrame = undefined
  }

  const safeTarget = Number.isFinite(target) ? target : 0
  if (prefersReducedMotion() || props.duration <= 0) {
    displayedValue.value = safeTarget
    return
  }

  const startValue = displayedValue.value
  const startedAt = window.performance.now()
  const step = (now: number): void => {
    const progress = Math.min(1, (now - startedAt) / props.duration)
    displayedValue.value = startValue + (safeTarget - startValue) * easeOutCubic(progress)
    if (progress < 1) {
      animationFrame = window.requestAnimationFrame(step)
    } else {
      animationFrame = undefined
    }
  }
  animationFrame = window.requestAnimationFrame(step)
}

watch(() => props.value, animateTo, { immediate: true })

onBeforeUnmount(() => {
  if (animationFrame !== undefined) window.cancelAnimationFrame(animationFrame)
})
</script>

<template>
  <span>{{ formattedValue }}</span>
</template>
