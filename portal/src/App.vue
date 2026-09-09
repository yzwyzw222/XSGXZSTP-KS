<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getSystems } from './services/systems'

const systems = ref([])
const loading = ref(true)
const error = ref('')
const enabledCount = computed(() => systems.value.filter(system => system.status === 'enabled').length)
let controller

async function loadSystems() {
  controller?.abort()
  controller = new AbortController()
  const current = controller
  loading.value = true
  error.value = ''
  try {
    systems.value = await getSystems(AbortSignal.any([current.signal, AbortSignal.timeout(8000)]))
  } catch (failure) {
    if (current.signal.aborted) return
    error.value = failure.name === 'TimeoutError' ? '接入状态读取超时，请重试。' : failure.message
    systems.value = []
  } finally {
    if (!current.signal.aborted) loading.value = false
  }
}

onMounted(loadSystems)
onUnmounted(() => controller?.abort())
</script>

<template>
  <a class="skip-link" href="#systems">跳至系统目录</a>
  <header class="site-header">
    <a class="brand" href="/" aria-label="学术系统统一门户首页">
      <span class="brand-mark" aria-hidden="true">学</span>
      <span>学术系统<span class="brand-subtitle">统一门户</span></span>
    </a>
    <nav aria-label="主导航">
      <a href="#systems">系统目录</a>
      <a href="#about">使用说明</a>
    </nav>
    <span class="edition">本地整合版 <span>01</span></span>
  </header>

  <main>
    <section class="hero" aria-labelledby="hero-title">
      <div class="hero-copy">
        <p class="eyebrow"><span /> 学术研究 · 系统协作</p>
        <h1 id="hero-title">从学术数据，<br />走向知识关联。</h1>
        <p class="hero-description">连接学术关系、实体抽取与成果可视化，<br class="wide-only" />为研究探索提供清晰、统一的系统入口。</p>
        <a class="primary-link" href="#systems">浏览系统目录 <span aria-hidden="true">↗</span></a>
      </div>
      <div class="research-diagram" aria-hidden="true">
        <span class="diagram-caption">研究的不同视角，在此相遇</span>
        <svg viewBox="0 0 500 330" fill="none">
          <circle cx="250" cy="170" r="136" stroke="currentColor" stroke-dasharray="2 8" />
          <circle cx="250" cy="170" r="94" stroke="currentColor" opacity=".3" />
          <path d="M250 61 353 226 137 226Z M250 170 250 61 M250 170 353 226 M250 170 137 226" stroke="currentColor" />
          <circle cx="250" cy="170" r="42" fill="#f1f3e9" stroke="currentColor" />
          <circle cx="250" cy="61" r="8" fill="#153e36" />
          <circle cx="353" cy="226" r="8" fill="#a77b46" />
          <circle cx="137" cy="226" r="8" fill="#527b85" />
          <circle cx="142" cy="87" r="3" fill="currentColor" />
          <circle cx="380" cy="131" r="3" fill="currentColor" />
          <circle cx="244" cy="306" r="3" fill="currentColor" />
          <text x="250" y="176" text-anchor="middle" fill="#153e36" stroke="none">学术知识</text>
          <text x="270" y="57" fill="#153e36" stroke="none">关系</text>
          <text x="370" y="251" fill="#153e36" stroke="none">成果</text>
          <text x="87" y="251" fill="#153e36" stroke="none">实体</text>
        </svg>
        <span class="diagram-footnote">概念示意 · 非业务数据</span>
      </div>
    </section>

    <section id="systems" class="systems-section" aria-labelledby="systems-title">
      <div class="section-heading">
        <div><p class="eyebrow">探索工具</p><h2 id="systems-title">系统目录</h2></div>
        <p v-if="!loading && !error" class="availability">共 {{ systems.length }} 个系统 <span>·</span> {{ enabledCount }} 个已启用</p>
      </div>
      <div v-if="loading" class="notice" role="status">正在读取系统接入状态…</div>
      <div v-else-if="error" class="notice error-notice" role="alert">
        <p>{{ error }}</p><button type="button" @click="loadSystems">重新读取</button>
      </div>
      <div v-else class="system-list">
        <article v-for="(system, index) in systems" :key="system.id" class="system-card" :class="system.id">
          <div class="card-top"><span class="system-number">0{{ index + 1 }}</span><span class="status" :class="system.status"><span />{{ system.status === 'enabled' ? '已启用' : '维护中' }}</span></div>
          <h3>{{ system.name }}</h3>
          <p class="system-description">{{ system.description }}</p>
          <ul class="capabilities" aria-label="系统功能"><li v-for="capability in system.capabilities" :key="capability">{{ capability }}</li></ul>
          <div class="card-bottom">
            <p>{{ system.status === 'enabled' ? '已通过接入验收，可进入系统使用。' : system.message }}</p>
            <a :href="system.path" :aria-label="`${system.name}：${system.status === 'enabled' ? '进入系统' : '查看维护说明'}`">{{ system.status === 'enabled' ? '进入系统' : '查看维护说明' }} <span aria-hidden="true">↗</span></a>
          </div>
        </article>
      </div>
    </section>

    <section id="about" class="about-section" aria-labelledby="about-title">
      <div><p class="eyebrow">开始之前</p><h2 id="about-title">每个系统，按就绪状态开放。</h2></div>
      <div class="about-copy">
        <p>门户汇集三个系统的功能与接入状态。标记为“维护中”的系统正在准备接入，完成验证后会开放入口。</p>
        <p>进入已启用系统后，请使用该系统的账号登录。各系统独立管理账号与数据，门户无需登录。</p>
      </div>
    </section>
  </main>
  <footer class="site-footer"><span>学术系统 · 统一门户</span><span>关联知识，支持探索。</span><a href="#hero-title">返回顶部 ↑</a></footer>
</template>
