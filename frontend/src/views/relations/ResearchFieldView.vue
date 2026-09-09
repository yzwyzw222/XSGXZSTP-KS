<template>
  <!--
    研究领域分区页（关系分析板块 2/5）
    ─────────────────────────────────
    按「领域」看作者分布，并可下钻到单作者的「研究主题画像」：
      全局视图（不选作者）：横向柱状图（各领域论文量）+ 按领域折叠的作者面板
      作者视图（选中作者）：关键词柱状图 + 主题迁移时间条 + 领域归属汇总
    数据源：
      - relationsApi.fieldPartition()：全体作者的领域分区扁平行（前端按 field 分组）
      - relationsApi.authorTopics(authorId)：某作者的关键词频次与出现年份区间
    注意：领域来自关键词的 field_name（知网分类），没填的归入「未分类」
  -->
  <div class="field-view">
    <div class="toolbar">
      <el-select v-model="selectedAuthorId" filterable clearable placeholder="全部作者（领域总览）"
        style="width: 240px" @change="onAuthorChange">
        <el-option v-for="a in authorOptions" :key="a.id" :label="a.displayName" :value="a.id" />
      </el-select>
      <el-button type="primary" @click="refresh">刷新</el-button>
    </div>

    <!-- 全局视图：领域柱状图 + 折叠面板 -->
    <template v-if="selectedAuthorId === null">
      <div class="chart-card" v-loading="loadingField">
        <h3>领域论文量分布</h3>
        <p class="chart-desc">按关键词的领域归属聚合每位作者的论文数，反映各研究领域的人力投入</p>
        <div ref="fieldChart" class="chart-body" />
      </div>
      <div v-if="fieldRows.length === 0 && !loadingField" class="empty-hint">
        暂无领域分区数据：请先在「数据管理」页为论文添加带领域的关键词
      </div>
      <el-collapse v-else v-model="activeFields" class="field-collapse">
        <el-collapse-item v-for="g in fieldGroups" :key="g.field" :name="g.field">
          <template #title>
            <span class="collapse-title">{{ g.field }}</span>
            <span class="collapse-sub">共 {{ g.authors.length }} 位作者 · {{ g.total }} 篇</span>
          </template>
          <el-table :data="g.authors" size="small">
            <el-table-column prop="authorName" label="作者" min-width="160" />
            <el-table-column prop="paperCount" label="论文数" width="100" sortable />
            <el-table-column label="操作" width="130">
              <template #default="{ row }">
                <el-button link type="primary" @click="drillTo(row.authorId)">查看主题画像</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-collapse-item>
      </el-collapse>
    </template>

    <!-- 作者视图：主题画像三件套 -->
    <template v-else>
      <div class="chart-card" v-loading="loadingTopics">
        <h3>{{ selectedAuthorName }} 的研究主题</h3>
        <p class="chart-desc">关键词按出现论文数排序，柱高 = 该关键词涉及的论文数</p>
        <div ref="topicChart" class="chart-body" />
      </div>

      <div class="chart-card">
        <h3>主题迁移</h3>
        <p class="chart-desc">每个关键词最早/最晚出现年份构成的区间，观察研究方向的演变</p>
        <div v-if="topicRows.length === 0" class="empty-hint">该作者暂无关键词数据</div>
        <div v-else class="timeline">
          <div v-for="t in timelineRows" :key="t.keywordId" class="timeline-row">
            <span class="timeline-keyword" :title="t.keyword">{{ t.keyword }}</span>
            <div class="timeline-track">
              <div class="timeline-bar" :style="barStyle(t)" :title="`${t.keyword}：${t.yearLabel}`" />
            </div>
            <span class="timeline-year">{{ t.yearLabel }}</span>
          </div>
        </div>
      </div>

      <div class="chart-card">
        <h3>领域归属</h3>
        <p class="chart-desc">该作者论文所涉领域及其论文数汇总</p>
        <el-table :data="fieldSummary" size="small" empty-text="暂无领域数据">
          <el-table-column prop="fieldName" label="领域" min-width="140" />
          <el-table-column prop="paperCount" label="论文数" width="100" sortable />
          <el-table-column label="关键词" min-width="240">
            <template #default="{ row }">{{ row.keywords.join('、') }}</template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 研究领域分区脚本：ECharts 双图（领域柱状 / 主题柱状）+ 折叠面板 + CSS 时间条
 * ─────────────────────────────────────────────
 * 两个图表共用一个 chartInstances 数组懒初始化（沿用分析总览页的 ECharts 模式）：
 *   [0] 全局领域柱状图（横向，Y=领域 X=论文数）
 *   [1] 作者主题柱状图（X=关键词 Y=论文数）
 * 主题迁移用纯 CSS 定位的区间条实现（不用 ECharts custom 系列，简单可靠）：
 *   每个关键词一行，条的位置/宽度按 (最早年,最晚年) 在全图年份范围内归一化成百分比
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { authorsApi, relationsApi, type Author, type AuthorTopicItem, type FieldPartitionItem } from '../../api'

// ====================================================================
// 状态
// ====================================================================
const authorOptions = ref<Author[]>([])
const selectedAuthorId = ref<number | null>(null)
const loadingField = ref(false)
const loadingTopics = ref(false)

const fieldChart = ref<HTMLElement | null>(null)
const topicChart = ref<HTMLElement | null>(null)
let chartInstances: echarts.ECharts[] = []

/** 领域分区扁平行（全局视图数据源） */
const fieldRows = ref<FieldPartitionItem[]>([])
/** 某作者的主题行（作者视图数据源） */
const topicRows = ref<AuthorTopicItem[]>([])
const activeFields = ref<string[]>([])

const selectedAuthorName = computed(() =>
  authorOptions.value.find(a => a.id === selectedAuthorId.value)?.displayName ?? '')

// 配色常量（与 style.css 设计令牌一致）
const COLORS = {
  accent: '#2d9df5', green: '#35c98c', amber: '#f5a04b',
  violet: '#a77af2', ink: '#edf6ff', muted: '#8296ae', border: '#1d3657'
}

// ====================================================================
// 派生数据
// ====================================================================

/** 领域分组：{ field, total, authors[] }，作者按论文数倒序 */
const fieldGroups = computed(() => {
  const map = new Map<string, { field: string; total: number; authors: { authorId: number; authorName: string; paperCount: number }[] }>()
  for (const row of fieldRows.value) {
    const g = map.get(row.field) ?? { field: row.field, total: 0, authors: [] }
    g.total += row.paperCount
    g.authors.push({ authorId: row.authorId, authorName: row.authorName, paperCount: row.paperCount })
    map.set(row.field, g)
  }
  return Array.from(map.values())
    .map(g => ({ ...g, authors: g.authors.sort((a, b) => b.paperCount - a.paperCount) }))
    .sort((a, b) => b.total - a.total)
})

/** 领域归属汇总：按 fieldName 聚合主题行 */
const fieldSummary = computed(() => {
  const map = new Map<string, { fieldName: string; paperCount: number; keywords: string[] }>()
  for (const t of topicRows.value) {
    const field = t.fieldName ?? '未分类'
    const s = map.get(field) ?? { fieldName: field, paperCount: 0, keywords: [] }
    s.paperCount += t.paperCount
    s.keywords.push(t.keyword)
    map.set(field, s)
  }
  return Array.from(map.values()).sort((a, b) => b.paperCount - a.paperCount)
})

// ====================================================================
// 数据加载
// ====================================================================

async function loadAuthorOptions() {
  try {
    const res = await authorsApi.list({ size: 100 })
    authorOptions.value = res.items
  } catch {
    ElMessage.error('加载作者列表失败')
  }
}

/** 全局领域分区（默认 500 行，后端已封顶 2000） */
async function loadFieldPartition() {
  loadingField.value = true
  try {
    fieldRows.value = await relationsApi.fieldPartition()
    await nextTick()
    renderFieldChart()
  } catch {
    ElMessage.error('加载领域分区失败')
  } finally {
    loadingField.value = false
  }
}

/** 某作者的主题画像（含年份区间） */
async function loadAuthorTopics() {
  if (selectedAuthorId.value === null) return
  loadingTopics.value = true
  try {
    topicRows.value = await relationsApi.authorTopics(selectedAuthorId.value)
    await nextTick()
    renderTopicChart()
  } catch {
    ElMessage.error('加载主题画像失败')
  } finally {
    loadingTopics.value = false
  }
}

// ====================================================================
// 图表渲染（ECharts，沿用分析总览页的懒初始化模式）
// ====================================================================

function getChart(index: number, el: HTMLElement | null): echarts.ECharts | null {
  if (!el) return null
  if (!chartInstances[index]) {
    chartInstances[index] = echarts.init(el, undefined, { renderer: 'canvas' })
  }
  return chartInstances[index]
}

/** [0] 全局领域柱状图：横向，Y 轴领域（按论文量倒序，inverse 让最大者在上） */
function renderFieldChart() {
  const chart = getChart(0, fieldChart.value)
  if (!chart) return
  const groups = fieldGroups.value
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 110, right: 40, bottom: 30, top: 20 },
    xAxis: {
      type: 'value',
      axisLabel: { color: COLORS.muted },
      splitLine: { lineStyle: { color: COLORS.border } }
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: groups.map(g => g.field),
      axisLabel: { color: COLORS.ink }
    },
    series: [{
      type: 'bar',
      data: groups.map(g => g.total),
      itemStyle: { color: COLORS.violet },
      barWidth: 14
    }]
  }, true)
}

/** [1] 作者主题柱状图：X 轴关键词（斜排防重叠），Y 轴论文数 */
function renderTopicChart() {
  const chart = getChart(1, topicChart.value)
  if (!chart) return
  const rows = topicRows.value
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, bottom: 70, top: 30 },
    xAxis: {
      type: 'category',
      data: rows.map(t => t.keyword),
      axisLabel: { color: COLORS.muted, rotate: 30, fontSize: 10 },
      axisLine: { lineStyle: { color: COLORS.border } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: COLORS.muted },
      splitLine: { lineStyle: { color: COLORS.border } }
    },
    series: [{
      type: 'bar',
      data: rows.map(t => t.paperCount),
      itemStyle: { color: COLORS.amber },
      barWidth: 18
    }]
  }, true)
}

// ====================================================================
// 主题迁移时间条（纯 CSS 区间条）
// ====================================================================

/** 年份区间的全局范围：所有关键词的最早年 ~ 最晚年，用于归一化条的位置 */
const yearRange = computed(() => {
  const all = topicRows.value.flatMap(t => t.years ?? [])
  if (all.length === 0) return { min: 0, max: 1 }
  return { min: Math.min(...all), max: Math.max(...all) }
})

interface TopicRowExt extends AuthorTopicItem {
  minYear: number
  maxYear: number
  yearLabel: string
}

/** 为每个关键词补上 min/max 年份与展示文案 */
const timelineRows = computed<TopicRowExt[]>(() => {
  const { min: globalMin } = yearRange.value
  return topicRows.value.map(t => {
    const years = (t.years ?? []).filter(y => y !== null)
    const minYear = years.length ? Math.min(...years) : globalMin
    const maxYear = years.length ? Math.max(...years) : globalMin
    return {
      ...t,
      minYear,
      maxYear,
      yearLabel: years.length ? `${minYear}–${maxYear}` : '未知'
    }
  })
})

/** 区间条样式：left/width 按年份在全局范围内归一化成百分比 */
function barStyle(t: TopicRowExt) {
  const { min: allMin, max: allMax } = yearRange.value
  const span = Math.max(allMax - allMin, 1)
  const left = ((t.minYear - allMin) / span) * 100
  const width = t.yearLabel === '未知' ? 2 : Math.max(((t.maxYear - t.minYear + 1) / span) * 100, 2)
  return { left: `${left}%`, width: `${width}%` }
}

// ====================================================================
// 交互
// ====================================================================

/** 从折叠面板钻取到某位作者的主题画像 */
function drillTo(authorId: number) {
  selectedAuthorId.value = authorId
  loadAuthorTopics()
}

/** 下拉切换：null（清空）= 全局视图，否则作者视图 */
function onAuthorChange(authorId: number | null) {
  if (authorId === null) return
  loadAuthorTopics()
}

/** 刷新：按当前视图重新拉数据 */
async function refresh() {
  if (selectedAuthorId.value === null) {
    await loadFieldPartition()
  } else {
    await loadAuthorTopics()
  }
}

// ====================================================================
// 生命周期
// ====================================================================
function handleResize() {
  chartInstances.forEach(c => c?.resize())
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await loadAuthorOptions()
  // 默认选中「姚期智」：五个板块都以作者为中心，进来直接看到主题画像；
  // 下拉仍可清空回到全局领域分区视图
  const hero = authorOptions.value.find(a => a.displayName === '姚期智')
  if (hero) {
    selectedAuthorId.value = hero.id
    await loadAuthorTopics()
  }
  await loadFieldPartition()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chartInstances.forEach(c => c?.dispose())
  chartInstances = []
})
</script>

<style scoped>
.field-view {
  padding: var(--space-4);
}

.toolbar {
  display: flex;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  align-items: center;
}

/* 图表卡片（沿用分析总览页样式） */
.chart-card {
  background: var(--paper);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-4);
  margin-bottom: var(--space-4);
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
  height: 320px;
}

.empty-hint {
  text-align: center;
  color: var(--muted);
  padding: var(--space-6);
}

/* 领域折叠面板 */
.field-collapse {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.collapse-title {
  font-weight: 600;
  margin-right: var(--space-3);
}

.collapse-sub {
  color: var(--muted);
  font-size: 12px;
}

/* 主题迁移时间条：关键词 | 区间条 | 年份标签 三列布局 */
.timeline {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.timeline-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.timeline-keyword {
  width: 150px;
  flex-shrink: 0;
  text-align: right;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.timeline-track {
  position: relative;
  flex: 1;
  height: 14px;
  background: var(--paper-deep);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
}

.timeline-bar {
  position: absolute;
  top: 2px;
  bottom: 2px;
  background: var(--amber);
  border-radius: 2px;
}

.timeline-year {
  width: 90px;
  flex-shrink: 0;
  color: var(--muted);
  font-size: 12px;
}
</style>
