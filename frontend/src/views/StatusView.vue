<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import { fetchLiveness } from '@/services/health'

type RequestState = 'idle' | 'loading' | 'up' | 'down'

const state = ref<RequestState>('idle')
const detail = ref('尚未发起检查')
let controller: AbortController | undefined

const stateLabel = computed(() => {
  switch (state.value) {
    case 'loading':
      return '检查中'
    case 'up':
      return '后端可用'
    case 'down':
      return '连接异常'
    default:
      return '等待检查'
  }
})

async function checkHealth() {
  controller?.abort()
  controller = new AbortController()
  state.value = 'loading'
  detail.value = '正在通过 Vite 代理访问 Spring Boot liveness 端点'

  try {
    const response = await fetchLiveness(controller.signal)
    state.value = response.status === 'UP' ? 'up' : 'down'
    detail.value = `后端报告状态：${response.status}`
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      return
    }
    state.value = 'down'
    detail.value = error instanceof Error ? error.message : '发生未知连接错误'
  }
}

onMounted(checkHealth)
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section class="status-section">
    <div class="section-heading">
      <p class="eyebrow">RUNTIME CHECK</p>
      <h1>运行状态</h1>
      <p>该检查从浏览器经 <code>/api</code> 代理访问后端存活端点，用于证明阶段 1 的前后端链路。</p>
    </div>

    <article class="status-card" :data-state="state">
      <div class="status-line">
        <span class="status-dot" aria-hidden="true"></span>
        <div>
          <small>SPRING BOOT LIVENESS</small>
          <h2>{{ stateLabel }}</h2>
        </div>
      </div>
      <p>{{ detail }}</p>
      <button type="button" :disabled="state === 'loading'" @click="checkHealth">
        {{ state === 'loading' ? '正在检查' : '重新检查' }}
      </button>
    </article>

    <p class="status-note">
      存活状态只表示应用进程可响应；MySQL 与 Neo4j 的就绪状态由后端 readiness 分组单独验证。
    </p>
  </section>
</template>
