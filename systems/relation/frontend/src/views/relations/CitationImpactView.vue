<template>
  <!--
    引用影响页（左侧导航板块 5/5）
    ───────────────────────────────
    回答"哪些论文被学术界认可"：施引数（引用了别人多少篇）vs 被引数（被别人引了多少次）横向双柱图。
    默认按作者「姚期智」过滤，看他哪篇论文被同行引用最多；下拉清空 = 全库论文排行。
    数据来源：analyticsApi.citations(100) 全库引用排行 → 前端按作者论文集合过滤、截取 TOP N。
  -->
  <div class="citation-view">
    <div class="toolbar">
      <el-select v-model="selectedAuthorId" filterable clearable placeholder="全部论文" style="width: 200px"
        @change="onAuthorChange">
        <el-option v-for="a in authorOptions" :key="a.id" :label="a.displayName" :value="a.id" />
      </el-select>
      <el-select v-model="limit" style="width: 140px" @change="render">
        <el-option :value="10" label="TOP 10" />
        <el-option :value="20" label="TOP 20" />
        <el-option :value="50" label="TOP 50" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="load">刷新数据</el-button>
    </div>

    <div class="chart-card" v-loading="loading">
      <h3>论文引用影响排行</h3>
      <p class="chart-desc">
        {{ selectedAuthorName ? `${selectedAuthorName} 的论文：` : '' }}施引数（引用了别人多少篇）与被引数（被别人引了多少次）对比，被引越多越受学术界认可
      </p>
      <div ref="chartEl" class="chart-body" />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 引用影响脚本：ECharts 横向双柱状图
 * ──────────────────────────────────
 * 数据来自 analyticsApi.citations(100)：{ paperId, title, citesOut, citesIn }[]
 * 一次性取全库排行（100 条），前端按选中作者的论文集合过滤后截取 TOP N；
 * 横向布局让论文标题（Y 轴类目）有充足空间，不会被斜排挤压。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { analyticsApi, authorsApi, papersApi, type Author, type CitationItem } from '../../api'

const limit = ref(20)
const loading = ref(false)
const chartEl = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null

const authorOptions = ref<Author[]>([])
const selectedAuthorId = ref<number | null>(null)
/** 选中作者的论文 id 集合（引用排行过滤用） */
const authorPaperIds = ref<Set<number>>(new Set())
/** 全库引用排行缓存（切作者时本地过滤，不重复请求） */
const allData = ref<CitationItem[]>([])

const selectedAuthorName = computed(() =>
  authorOptions.value.find(a => a.id === selectedAuthorId.value)?.displayName ?? '')

// 配色常量（与 style.css 设计令牌一致）
const COLORS = {
  amber: '#f5a04b', green: '#35c98c', ink: '#edf6ff', muted: '#8296ae', border: '#1d3657'
}

async function loadAuthorOptions() {
  try {
    const res = await authorsApi.list({ size: 100 })
    authorOptions.value = res.items
  } catch {
    ElMessage.error('加载作者列表失败')
  }
}

/** 默认选中「姚期智」（与其余四个板块口径一致），找不到则保持全库视图 */
function resolveDefaultAuthor() {
  const hero = authorOptions.value.find(a => a.displayName === '姚期智')
  if (hero) selectedAuthorId.value = hero.id
}

/** 拉全量论文，筛出选中作者的论文 id 集合 */
async function loadAuthorPapers() {
  try {
    const res = await papersApi.list({ size: 100 })
    if (selectedAuthorId.value === null) {
      authorPaperIds.value = new Set()
      return
    }
    authorPaperIds.value = new Set(
      res.items.filter(p => (p.authors ?? []).some(a => a.authorId === selectedAuthorId.value)).map(p => p.id))
  } catch {
    ElMessage.error('加载论文数据失败')
  }
}

async function load() {
  loading.value = true
  try {
    allData.value = await analyticsApi.citations(100)
    render()
  } catch {
    ElMessage.error('加载引用分析数据失败')
  } finally {
    loading.value = false
  }
}

function render() {
  if (!chartEl.value) return
  if (!chart) {
    chart = echarts.init(chartEl.value, undefined, { renderer: 'canvas' })
  }

  // 有作者时按 TA 的论文集合过滤，再截取 TOP N；标题超过 24 字截断防止 Y 轴被撑爆
  const data = (selectedAuthorId.value === null
    ? allData.value
    : allData.value.filter(d => authorPaperIds.value.has(d.paperId))).slice(0, limit.value)
  const titles = data.map(d => d.title.length > 24 ? d.title.slice(0, 24) + '…' : d.title)

  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['施引数', '被引数'], textStyle: { color: COLORS.muted } },
    grid: { left: 240, right: 30, bottom: 30, top: 40 },
    xAxis: {
      type: 'value',
      axisLabel: { color: COLORS.muted },
      splitLine: { lineStyle: { color: COLORS.border } }
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: titles,
      axisLabel: { color: COLORS.ink, fontSize: 11 }
    },
    series: [
      { name: '施引数', type: 'bar', data: data.map(d => d.citesOut), itemStyle: { color: COLORS.amber }, barWidth: 12 },
      { name: '被引数', type: 'bar', data: data.map(d => d.citesIn), itemStyle: { color: COLORS.green }, barWidth: 12 }
    ]
  }, true)
}

/** 切换作者：更新论文集合后本地重绘（引用排行已缓存） */
async function onAuthorChange() {
  await loadAuthorPapers()
  render()
}

function handleResize() {
  chart?.resize()
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await loadAuthorOptions()
  resolveDefaultAuthor()
  await loadAuthorPapers()
  await load()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.citation-view {
  padding: var(--space-4);
}

.toolbar {
  display: flex;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  align-items: center;
}

.chart-card {
  background: var(--paper);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-4);
}

.chart-card h3 {
  margin: 0 0 var(--space-1) 0;
  font-size: 16px;
}

.chart-desc {
  color: var(--muted);
  font-size: 12px;
  margin: 0 0 var(--space-3) 0;
}

.chart-body {
  width: 100%;
  height: 460px;
}
</style>
