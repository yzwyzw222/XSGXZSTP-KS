<template>
  <div class="scholar-profile">
    <h2 class="page-title">学者画像</h2>
    <p class="page-desc">按学者姓名检索，聚合该学者的全部科研信息。</p>

    <!-- 检索区 -->
    <div class="search-bar">
      <el-autocomplete
        v-model="keyword"
        :fetch-suggestions="querySearch"
        placeholder="输入学者姓名，如：姚期智"
        clearable
        class="search-input"
        value-key="name"
        @select="onSelect"
        @keyup.enter="onSearch"
      >
        <template #default="{ item }">
          <div class="suggestion">
            <span>{{ item.name }}</span>
            <span class="suggestion-meta">{{ item.paperCount }} 篇论文<template v-if="item.institution"> · {{ item.institution }}</template></span>
          </div>
        </template>
      </el-autocomplete>
      <el-button type="primary" :loading="profileLoading" @click="onSearch">检索</el-button>
    </div>

    <el-empty v-if="!profile && !profileLoading" description="输入学者姓名开始检索" />

    <!-- 画像区 -->
    <template v-if="profile">
      <div class="profile-header">
        <div class="scholar-name">{{ profile.name }}</div>
        <div class="scholar-meta">
          <span v-if="mainInstitution">{{ mainInstitution }}</span>
          <span v-if="profile.firstYear">活跃年份：{{ profile.firstYear }} — {{ profile.lastYear }}</span>
        </div>
      </div>

      <div class="stat-cards">
        <div class="stat-card"><span class="num">{{ profile.totalPapers }}</span><span class="label">论文总数</span></div>
        <div class="stat-card"><span class="num">{{ profile.firstAuthorPapers }}</span><span class="label">第一作者</span></div>
        <div class="stat-card"><span class="num">{{ profile.correspondingPapers }}</span><span class="label">通讯作者</span></div>
        <div class="stat-card"><span class="num">{{ profile.coauthors.length }}</span><span class="label">合作者</span></div>
        <div class="stat-card"><span class="num">{{ profile.institutions.length }}</span><span class="label">所属机构</span></div>
      </div>

      <div class="chart-grid">
        <div class="chart-card">
          <h3>发表趋势</h3>
          <div ref="trendRef" class="chart"></div>
        </div>
        <div class="chart-card">
          <h3>研究方向分布</h3>
          <div ref="topicRef" class="chart"></div>
        </div>
        <div class="chart-card">
          <h3>来源期刊/平台分布</h3>
          <div ref="venueRef" class="chart"></div>
        </div>
        <div class="chart-card">
          <h3>合作者排名</h3>
          <div ref="coauthorRef" class="chart"></div>
        </div>
      </div>

      <div class="chart-card paper-list">
        <h3>论文清单（{{ profile.papers.length }} 篇）</h3>
        <el-table :data="profile.papers" stripe>
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="title" label="题名" min-width="320" show-overflow-tooltip />
          <el-table-column prop="year" label="年份" width="80" />
          <el-table-column prop="venue" label="来源" min-width="160" show-overflow-tooltip />
          <el-table-column prop="paperType" label="类型" width="80" />
          <el-table-column label="作者角色" width="150">
            <template #default="{ row }">
              <el-tag v-if="row.firstAuthor" type="primary" size="small">第一作者</el-tag>
              <el-tag v-if="row.corresponding" type="success" size="small">通讯作者</el-tag>
              <span v-if="!row.firstAuthor && !row.corresponding" class="muted">合作参与</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { searchScholars, fetchScholarProfile } from '../api/scholar'

const keyword = ref('')
const profile = ref(null)
const profileLoading = ref(false)

const trendRef = ref(null)
const topicRef = ref(null)
const venueRef = ref(null)
const coauthorRef = ref(null)
let charts = []

const AXIS_COLOR = '#8296ae'
const SPLIT_LINE = '#1e3a5e'

const mainInstitution = computed(() =>
  profile.value?.institutions?.[0]?.name || ''
)

async function querySearch(query, cb) {
  try {
    const list = await searchScholars(query || '')
    cb(list.map((s) => ({ ...s, value: s.name })))
  } catch {
    cb([])
  }
}

function onSelect(item) {
  keyword.value = item.name
  loadProfile(item.id)
}

async function onSearch() {
  const kw = keyword.value.trim()
  if (!kw) {
    ElMessage.warning('请输入学者姓名')
    return
  }
  const list = await searchScholars(kw)
  if (list.length === 0) {
    ElMessage.info('未找到该学者')
    profile.value = null
    return
  }
  if (list.length === 1) {
    loadProfile(list[0].id)
    return
  }
  // 多个候选：取论文数最多的，并提示
  ElMessage.info(`匹配到 ${list.length} 位学者，已展示论文数最多的「${list[0].name}」`)
  loadProfile(list[0].id)
}

async function loadProfile(id) {
  profileLoading.value = true
  try {
    const data = await fetchScholarProfile(id)
    profile.value = data
    await nextTick()
    renderCharts()
  } catch (e) {
    ElMessage.error('画像加载失败')
    profile.value = null
  } finally {
    profileLoading.value = false
  }
}

function baseGrid() {
  return { left: 40, right: 24, top: 20, bottom: 30 }
}

function hbarOption(data, color, labelWidth = 110) {
  const sorted = [...data].reverse()
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}: {c}' },
    grid: { left: labelWidth, right: 36, top: 10, bottom: 30 },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: AXIS_COLOR },
      splitLine: { lineStyle: { color: SPLIT_LINE } },
    },
    yAxis: {
      type: 'category',
      data: sorted.map((d) => d.name),
      axisLabel: { color: AXIS_COLOR, width: labelWidth - 15, overflow: 'truncate' },
      axisLine: { lineStyle: { color: SPLIT_LINE } },
    },
    series: [{
      type: 'bar',
      data: sorted.map((d) => d.count),
      barMaxWidth: 14,
      itemStyle: { borderRadius: [0, 4, 4, 0], color },
      label: { show: true, position: 'right', color },
    }],
  }
}

function renderCharts() {
  disposeCharts()
  if (!profile.value) return

  // 趋势
  const trend = echarts.init(trendRef.value)
  trend.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    grid: baseGrid(),
    xAxis: {
      type: 'category',
      data: profile.value.trend.map((d) => d.year),
      axisLine: { lineStyle: { color: SPLIT_LINE } },
      axisLabel: { color: AXIS_COLOR },
    },
    yAxis: { type: 'value', minInterval: 1, axisLabel: { color: AXIS_COLOR }, splitLine: { lineStyle: { color: SPLIT_LINE } } },
    series: [{
      type: 'line',
      data: profile.value.trend.map((d) => d.count),
      smooth: true,
      symbolSize: 8,
      itemStyle: { color: '#2d9df5' },
      lineStyle: { width: 3 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#2d9df566' },
          { offset: 1, color: '#2d9df505' },
        ]),
      },
    }],
  })
  charts.push(trend)

  // 研究方向
  if (profile.value.topics.length > 0) {
    const topic = echarts.init(topicRef.value)
    topic.setOption(hbarOption(profile.value.topics, '#a77af2'))
    charts.push(topic)
  }

  // 期刊分布
  if (profile.value.venues.length > 0) {
    const venue = echarts.init(venueRef.value)
    venue.setOption(hbarOption(profile.value.venues, '#2d9df5', 130))
    charts.push(venue)
  }

  // 合作者
  if (profile.value.coauthors.length > 0) {
    const co = echarts.init(coauthorRef.value)
    co.setOption(hbarOption(profile.value.coauthors, '#f5a04b', 90))
    charts.push(co)
  }
}

function disposeCharts() {
  charts.forEach((c) => c.dispose())
  charts = []
}

function handleResize() {
  charts.forEach((c) => c.resize())
}

onMounted(() => window.addEventListener('resize', handleResize))
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  disposeCharts()
})
</script>

<style scoped>
.scholar-profile {
  max-width: 1400px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--ink);
  margin-bottom: 4px;
}

.page-desc {
  font-size: 13px;
  color: var(--muted);
  margin-bottom: 14px;
}

.search-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.search-input {
  width: 420px;
}

.suggestion {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.suggestion-meta {
  color: var(--muted);
  font-size: 12px;
}

.profile-header {
  margin-bottom: 14px;
}

.scholar-name {
  font-size: 22px;
  font-weight: 700;
  color: var(--ink);
}

.scholar-meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: var(--muted);
  margin-top: 4px;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  background-color: var(--paper);
  border: 1px solid #1e3a5e;
  border-radius: 8px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-card .num {
  font-size: 26px;
  font-weight: 700;
  color: var(--accent);
}

.stat-card .label {
  font-size: 12px;
  color: var(--muted);
}

.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 14px;
}

.chart-card {
  background-color: var(--paper);
  border: 1px solid #1e3a5e;
  border-radius: 8px;
  padding: 16px;
}

.chart-card h3 {
  font-size: 15px;
  color: var(--ink);
  margin-bottom: 6px;
}

.chart {
  width: 100%;
  height: 260px;
}

.paper-list {
  margin-bottom: 20px;
}

.muted {
  color: var(--muted);
  font-size: 12px;
}
</style>
