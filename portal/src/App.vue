<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import PortalIcon from './components/PortalIcon.vue'
import PortalChart from './components/PortalChart.vue'
import { fields, metrics, researchTopics, systemVisuals, trend } from './data/overview'
import { getSystems } from './services/systems'

defineProps({ user: { type: Object, required: true }, signOutPending: Boolean, signOutError: { type: String, default: '' } })
defineEmits(['sign-out'])

const systems = ref([])
const loading = ref(true)
const error = ref('')
const query = ref('')
const period = ref('5')
const showAllTopics = ref(false)
const activeNav = ref('home')
const aboutDialog = ref(null)
const dialogTitle = ref('平台介绍')
const enabledCount = computed(() => systems.value.filter(system => system.status === 'enabled').length)
const visibleSystems = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase()
  return systems.value.filter(system => [system.name, system.description, ...(Array.isArray(system.capabilities) ? system.capabilities : []),
    ...systemVisuals[system.id].keywords].join(' ').toLocaleLowerCase().includes(keyword))
})
const visibleTopics = computed(() => showAllTopics.value ? researchTopics : researchTopics.slice(0, 12))
const visibleTrend = computed(() => trend.slice(-Number(period.value)))
let controller

// 保留现有状态读取、超时和取消机制，读取失败时不展示业务链接。
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

function openAbout(title) {
  dialogTitle.value = title
  aboutDialog.value?.showModal()
}

function selectTopic(topic) {
  query.value = query.value === topic ? '' : topic
  document.getElementById('systems')?.scrollIntoView({ behavior: 'auto', block: 'nearest' })
}

onMounted(loadSystems)
onUnmounted(() => controller?.abort())
</script>

<template>
  <div class="portal-shell">
    <a class="skip-link" href="#systems">跳至系统目录</a>
    <header class="site-header">
      <a class="brand" href="/" aria-label="学术智能平台首页">
        <PortalIcon name="mortarboard-fill" class="brand-mark" />
        <span class="brand-copy">学术智能平台<small>AI FOR A BETTER RESEARCH</small></span>
      </a>
      <nav class="main-nav" aria-label="主导航">
        <a href="#home" :class="{ active: activeNav === 'home' }" @click="activeNav = 'home'; query = ''"><PortalIcon name="house-door-fill" />首页</a>
        <button type="button" @click="openAbout('平台介绍')"><PortalIcon name="file-earmark-richtext" />平台介绍</button>
        <a href="#data-overview" :class="{ active: activeNav === 'data' }" @click="activeNav = 'data'"><PortalIcon name="layers" />数据资源</a>
        <a href="#systems" :class="{ active: activeNav === 'tools' }" @click="activeNav = 'tools'; query = ''"><PortalIcon name="tools" />科研工具</a>
        <button type="button" @click="openAbout('帮助中心')"><PortalIcon name="question-circle-fill" />帮助中心</button>
      </nav>
      <form class="header-search" role="search" @submit.prevent>
        <PortalIcon name="search" />
        <input v-model="query" aria-label="搜索系统与研究方向" type="search" placeholder="搜索系统、功能、研究关键词…" autocomplete="off" />
      </form>
      <div class="header-actions">
        <details class="header-menu notification-menu">
          <summary aria-label="接入状态通知"><PortalIcon name="bell-fill" /></summary>
          <div class="menu-popover">
            <strong>系统接入动态</strong>
            <p v-if="loading">正在读取系统接入状态…</p>
            <p v-else-if="error">{{ error }}</p>
            <template v-else><p>{{ systems.length }} 个系统，{{ enabledCount }} 个已启用。</p><p>维护中的系统将在完成接入验证后开放。</p></template>
          </div>
        </details>
        <details class="header-menu profile-menu">
          <summary aria-label="账号菜单"><PortalIcon name="person-circle" class="avatar" /><span>{{ user.username }}</span><PortalIcon name="chevron-down" class="chevron" /></summary>
          <div class="menu-popover"><strong>已登录学术智能平台</strong><p>各子系统共享本次登录状态。</p><p v-if="signOutError" role="alert">{{ signOutError }}</p><p v-if="user.roles.includes('ADMIN')"><a href="/crawler/users">管理平台账号</a></p><button type="button" :disabled="signOutPending" @click="$emit('sign-out')">{{ signOutPending ? '正在退出…' : '退出登录' }}</button></div>
        </details>
      </div>
    </header>

    <main id="home">
      <p v-if="signOutError" class="notice error-notice" role="alert">{{ signOutError }}</p>
      <section class="hero" aria-labelledby="hero-title">
        <div class="hero-motto hero-motto-left" aria-hidden="true"><span>数据连接知识<br />知识驱动创新</span><small>CONNECTING KNOWLEDGE<br />EMPOWERING RESEARCH</small></div>
        <div class="hero-copy">
          <h1 id="hero-title">学术智能平台统一入口</h1>
          <p class="hero-description">整合学术关系<span>知识图谱</span>、实体抽取与成果可视化的统一门户</p>
          <p class="hero-tagline">让学术数据更有价值 <span>·</span> 用智能技术推动科研创新</p>
        </div>
        <div class="hero-motto hero-motto-right" aria-hidden="true"><span>全球视野<br />数据智能<br />开放共享<br />智见未来</span><small>GLOBAL VISION<br />DATA INTELLIGENCE<br />OPEN SCIENCE</small></div>
      </section>

      <div class="dashboard">
        <aside class="side-column left-column" aria-label="学术合作与研究领域演示">
          <section class="data-panel cooperation-panel" aria-labelledby="cooperation-title">
            <div class="panel-heading"><h2 id="cooperation-title">全球学术合作网络</h2><span class="demo-label">示例</span></div>
            <img class="cooperation-map" src="/assets/portal/images/cooperation-map.webp" alt="全球学术合作网络概念示意图，非真实合作关系" width="1672" height="941" />
            <div class="cooperation-stats"><p><strong>128<span>+</span></strong><span>国家/地区</span></p><p><strong>5,420<span>+</span></strong><span>合作机构</span></p></div>
            <ul class="map-legend" aria-label="合作频率图例"><li>高频合作</li><li>中频合作</li><li>低频合作</li></ul>
          </section>
          <section class="data-panel fields-panel" aria-labelledby="fields-title">
            <div class="panel-heading"><h2 id="fields-title">研究领域分布</h2><span class="demo-label">示例</span></div>
            <div class="fields-content">
              <PortalChart type="donut" :items="fields" label="研究领域分布演示数据，详细占比见右侧图例" />
              <ul class="fields-legend"><li v-for="field in fields" :key="field.label"><span class="legend-dot" :style="{ backgroundColor: field.color }" /><span>{{ field.label }}</span><span>{{ field.value }}%</span></li></ul>
            </div>
          </section>
        </aside>

        <div class="center-column">
          <section id="systems" class="systems-section" aria-labelledby="systems-title" :aria-busy="loading">
            <h2 id="systems-title" class="sr-only">系统目录</h2>
            <div v-if="query.trim() && !loading && !error" class="search-summary" role="status"><span>“{{ query.trim() }}” · 找到 {{ visibleSystems.length }} 个系统</span><button type="button" @click="query = ''">清除筛选</button></div>
            <div v-if="loading" class="notice" role="status"><PortalIcon name="layers" /><p>正在读取系统接入状态…</p></div>
            <div v-else-if="error" class="notice error-notice" role="alert"><PortalIcon name="question-circle-fill" /><p>{{ error }}</p><button class="entry-button" type="button" @click="loadSystems">重新读取<PortalIcon name="arrow-right" /></button></div>
            <div v-else-if="!visibleSystems.length" class="notice" role="status"><PortalIcon name="search" /><p>未找到匹配的系统</p><span>试试“知识图谱”“实体抽取”或“可视化”。</span><button type="button" class="entry-button" @click="query = ''">查看全部系统<PortalIcon name="arrow-right" /></button></div>
            <div v-else class="system-list">
              <article v-for="system in visibleSystems" :key="system.id" class="system-card" :class="system.id">
                <div class="card-top"><span class="system-number">{{ systemVisuals[system.id].number }}</span><span class="status" :class="system.status">{{ system.status === 'enabled' ? '已启用' : '维护中' }}</span></div>
                <div class="system-art" aria-hidden="true">
                  <img :src="systemVisuals[system.id].image" alt="" width="1536" height="1024" />
                  <span class="art-caption">{{ systemVisuals[system.id].caption }}</span>
                  <template v-if="system.id === 'extraction'"><div class="entity-tags entity-tags-left"><span>作者</span><span>机构</span><span>关键词</span></div><div class="entity-tags entity-tags-right"><span>实体抽取</span><span>数据融合</span><span>知识图谱</span></div></template>
                </div>
                <div class="card-copy"><h3>{{ system.name }}</h3><p class="system-description">{{ systemVisuals[system.id].description }}</p></div>
                <div class="card-bottom"><a class="entry-button" :href="system.path" :aria-label="system.name + '：' + (system.status === 'enabled' ? '进入系统' : '查看维护说明')" :title="system.status === 'enabled' ? system.description : system.message">{{ system.status === 'enabled' ? '进入平台' : '查看维护说明' }}<PortalIcon name="arrow-right" /></a></div>
              </article>
            </div>
          </section>

          <section id="data-overview" class="data-panel overview-panel" aria-labelledby="overview-title">
            <div class="panel-heading"><h2 id="overview-title"><PortalIcon name="layers" />数据总览</h2><span class="demo-label">演示数据 · 非实时统计</span></div>
            <dl class="metrics"><div v-for="metric in metrics" :key="metric.label" class="metric"><PortalIcon :name="metric.icon" /><div><dt>{{ metric.label }}</dt><dd>{{ metric.value }}</dd><span :class="['metric-change', { neutral: !metric.change }]">{{ metric.change || '图表模板' }}<PortalIcon v-if="metric.change" name="arrow-up" /></span></div></div></dl>
          </section>
        </div>

        <aside class="side-column right-column" aria-label="研究趋势演示与关键词">
          <section class="data-panel trend-panel" aria-labelledby="trend-title">
            <div class="panel-heading"><h2 id="trend-title">学术发展趋势</h2><select v-model="period" aria-label="趋势时间范围"><option value="5">近5年</option><option value="3">近3年</option></select></div>
            <PortalChart type="line" :items="visibleTrend" :label="'学术发展趋势演示数据：' + visibleTrend.map(item => item.label + ' 年 ' + item.value + ' 万篇').join('，')" />
            <span class="chart-note">示例趋势 · 单位：万篇</span>
          </section>
          <section class="data-panel topics-panel" aria-labelledby="topics-title">
            <div class="panel-heading"><h2 id="topics-title">热门研究关键词</h2><button type="button" :aria-expanded="showAllTopics" @click="showAllTopics = !showAllTopics">{{ showAllTopics ? '收起' : '更多' }}<PortalIcon name="arrow-right" /></button></div>
            <div class="topic-list"><button v-for="topic in visibleTopics" :key="topic" type="button" :class="{ selected: query === topic }" :aria-pressed="query === topic" @click="selectTopic(topic)">{{ topic }}</button></div>
            <p class="topics-hint">选择关键词，发现相关科研工具</p>
          </section>
        </aside>
      </div>
    </main>

    <footer class="site-footer"><p>学术无界 · 智能未来<small>BOUNDLESS RESEARCH &nbsp; INFINITE POSSIBILITIES</small></p><div class="footer-center"><span>AI × KNOWLEDGE × RESEARCH</span><span>FOR A BETTER TOMORROW</span><p v-if="!loading && !error">{{ systems.length }} 个系统接入 · {{ enabledCount }} 个已启用</p></div><p>开放 / 合作 / 创新 / 共享<small>OPEN &nbsp; COLLABORATION &nbsp; INNOVATION &nbsp; SHARING</small></p></footer>

    <dialog id="about" ref="aboutDialog" class="about-dialog" aria-labelledby="about-title" @click="event => { if (event.target === aboutDialog) aboutDialog.close() }">
      <div class="dialog-heading"><h2 id="about-title">{{ dialogTitle }}</h2><button type="button" aria-label="关闭说明" @click="aboutDialog.close()"><PortalIcon name="x-lg" /></button></div>
      <p class="dialog-intro">连接学术关系、实体抽取与成果可视化，为研究探索提供统一入口。</p>
      <h3>每个系统，按就绪状态开放。</h3>
      <p>门户汇集四个系统的功能与接入状态。点击已启用系统的卡片进入平台；标记为“维护中”的系统可查看维护说明。</p>
      <h3>从研究方向，找到合适的工具。</h3>
      <p>顶部搜索框可按系统名称、功能或研究关键词筛选入口。点击热门关键词也可筛选，清除搜索即可显示全部系统。</p>
      <h3>账号与数据</h3>
      <p>统一登录后可直接进入四个子系统。账号由平台管理员统一管理，业务数据仍由各系统独立存储；退出登录会同时结束所有系统的访问。</p>
      <p class="dialog-note">合作网络、研究领域、趋势及数据总览使用演示数据，仅用于展示平台概念；系统接入状态来自当前入口服务。</p>
      <form method="dialog"><button class="entry-button">开始探索<PortalIcon name="arrow-right" /></button></form>
    </dialog>
  </div>
</template>
