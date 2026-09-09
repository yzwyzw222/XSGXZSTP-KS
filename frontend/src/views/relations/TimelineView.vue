<template>
  <!--
    发行论文及时间页（左侧导航板块 2/5）——按发行时间整理某位作者的所有论文
    ────────────────────────────────────────────────────────────────
    呈现：时间轴散点图，横轴 = 发行时间，纵轴 = 同年论文的错行排布（避免重叠）。
          每个点 = 一篇论文，颜色 = 该论文所属研究领域（领域→颜色按出现顺序自动分配）；
          年份区间用交替底色分区、散点带同色辉光，悬停出现竖向虚线指示时间位置，
          点击的点白描边放大并联动右侧详情卡。
    交互：点击任意点 → 右侧弹出该论文详情卡（论文名称 / 发行时间 / 研究领域 / 渠道 / 被引 / 作者）。
    数据来源：papersApi.list({size:100}) 全量论文中筛出选中作者署名的，
              relationsApi.authorTopics 提供关键词→研究领域的映射（用于给点上色）。
  -->
  <div class="timeline-view">
    <div class="toolbar">
      <el-select v-model="selectedAuthorId" filterable placeholder="选择作者" style="width: 200px"
        @change="loadAll">
        <el-option v-for="a in authorOptions" :key="a.id" :label="a.displayName" :value="a.id" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="loadAll">刷新</el-button>
      <!-- 领域配色图例（动态生成，胶囊徽章样式 + 同色辉光圆点） -->
      <div class="legend">
        <span v-for="f in fieldColors" :key="f.field" class="legend-item">
          <i class="legend-dot" :style="{ background: f.color, boxShadow: `0 0 6px ${f.color}` }" />{{ f.field }}
        </span>
      </div>
    </div>

    <!-- 左：时间轴散点图；右：点击后弹出的论文详情卡 -->
    <div class="split">
      <div class="chart-card" v-loading="loading">
        <h3>论文发表时间分布</h3>
        <p class="chart-desc">
          {{ authorName }} 共 {{ papers.length }} 篇论文 · 跨越 {{ yearSpan }} 个年份 · 点击任意数据点查看详情
        </p>
        <div ref="chartEl" class="chart-body" />
      </div>

      <div class="detail-card">
        <template v-if="selectedPaper">
          <div class="detail-head">
            <span class="detail-type">论文详情</span>
            <el-button link class="detail-close" @click="selectedPaper = null">✕</el-button>
          </div>
          <h4 class="detail-title">{{ selectedPaper.title }}</h4>
          <p class="detail-line"><span class="detail-k">发行时间：</span>{{ selectedPaper.publicationDate ?? (selectedPaper.publicationYear ? selectedPaper.publicationYear + ' 年' : '—') }}</p>
          <p class="detail-line"><span class="detail-k">渠道：</span>{{ selectedPaper.venue?.displayName ?? '—' }}</p>
          <p class="detail-line"><span class="detail-k">被引：</span>{{ selectedPaper.citationCount }} 次</p>
          <p class="detail-line"><span class="detail-k">作者：</span>{{ (selectedPaper.authors ?? []).map(a => a.displayName).join('、') }}</p>
          <div class="detail-fields">
            <span class="detail-k">研究领域：</span>
            <el-tag v-for="f in paperFields(selectedPaper)" :key="f" size="small" effect="plain"
              :style="{ borderColor: fieldColor(f), color: fieldColor(f) }">{{ f }}</el-tag>
            <span v-if="paperFields(selectedPaper).length === 0" class="detail-none">—</span>
          </div>
          <p v-if="selectedPaper.abstractText" class="detail-abstract">{{ selectedPaper.abstractText }}</p>
        </template>
        <div v-else class="empty-hint">
          点击左侧散点图中的任意一个点，<br />这里会展示该论文的发行时间、研究领域等详情
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 发行论文及时间脚本：ECharts 时间轴散点图
 * ─────────────────────────────────────────
 * 流程：
 *   1. 加载作者下拉 → 默认选中「姚期智」→ 拉全量论文筛出 TA 的 + 关键词领域映射
 *   2. render()：论文按发行日期排序；同一年份的论文依次错行（y = 行号），
 *      颜色按论文所属领域分配（领域→颜色 按首次出现顺序从调色板轮转）
 *   3. 点击散点 → chart.on('click') 取 dataIndex 反查论文 → 右侧详情卡
 * 无发行日期的论文：若有年份，按"该年 6 月底"占位（图上仍可点、详情卡如实标注）；两者皆无放最后。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { authorsApi, papersApi, relationsApi, type Author, type Paper } from '../../api'

// ====================================================================
// 状态
// ====================================================================
const chartEl = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null

const authorOptions = ref<Author[]>([])
const selectedAuthorId = ref<number | null>(null)
const loading = ref(false)

/** 选中作者的论文（按发行时间升序排好） */
const papers = ref<Paper[]>([])
/** 关键词 id → 研究领域名 */
const fieldMap = ref<Map<number, string>>(new Map())
/** 选中的论文（右侧详情卡） */
const selectedPaper = ref<Paper | null>(null)

// 配色（与全站设计令牌一致，六色轮转足够覆盖样例的领域数）
const PALETTE = ['#2d9df5', '#35c98c', '#f5a04b', '#a77af2', '#e07bb0', '#57bcff']

/** 领域 → 颜色（按论文首次出现的顺序分配，保证图例稳定） */
const fieldColors = ref<{ field: string; color: string }[]>([])

const authorName = computed(() =>
  authorOptions.value.find(a => a.id === selectedAuthorId.value)?.displayName ?? '')

/** 论文跨越的年份数（标题栏副文案用，无年份的论文不计入） */
const yearSpan = computed(() =>
  new Set(papers.value.map(p => p.publicationYear ?? 0).filter(y => y > 0)).size)

/** 领域名 → 图例颜色（详情卡标签边框与文字跟图例同色，图表与详情视觉呼应） */
function fieldColor(f: string): string {
  return fieldColors.value.find(x => x.field === f)?.color ?? '#57bcff'
}

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

/** 默认选中「姚期智」（知网真实数据的主角作者），找不到再取第一位 */
function resolveDefaultAuthor() {
  if (selectedAuthorId.value !== null) return
  const hero = authorOptions.value.find(a => a.displayName === '姚期智')
  selectedAuthorId.value = hero ? hero.id : (authorOptions.value[0]?.id ?? null)
}

async function loadAll() {
  if (selectedAuthorId.value === null) return
  loading.value = true
  try {
    const res = await papersApi.list({ size: 100 })
    papers.value = res.items
      .filter(p => (p.authors ?? []).some(a => a.authorId === selectedAuthorId.value))
      .sort((a, b) => sortDate(a).localeCompare(sortDate(b)))
    const topics = await relationsApi.authorTopics(selectedAuthorId.value, 500)
    fieldMap.value = new Map(topics.map(t => [t.keywordId, t.fieldName ?? '未分类']))
    selectedPaper.value = null
    render()
  } catch {
    ElMessage.error('加载论文时间线失败')
  } finally {
    loading.value = false
  }
}

/** 排序用日期：有完整日期用它；只有年份用当年 6 月底占位；都没有排最后 */
function sortDate(p: Paper): string {
  if (p.publicationDate) return p.publicationDate
  if (p.publicationYear !== null) return `${p.publicationYear}-06-30`
  return '9999-12-31'
}

// ====================================================================
// 图表渲染
// ====================================================================
function render() {
  if (!chartEl.value) return
  if (!chart) {
    chart = echarts.init(chartEl.value, undefined, { renderer: 'canvas' })
    // 点击散点 → 反查论文 → 右侧详情卡
    chart.on('click', (params: any) => {
      const idx = params.dataIndex as number
      if (typeof idx === 'number' && papers.value[idx]) {
        selectedPaper.value = papers.value[idx]
      }
    })
    // 开发模式下暴露实例，便于控制台调试与自动化测试
    if (import.meta.env.DEV) {
      (window as any).__timelineChart = chart
      ;(window as any).__selectPaper = (idx: number) => {
        if (papers.value[idx]) selectedPaper.value = papers.value[idx]
      }
    }
  }
  if (papers.value.length === 0) {
    // 空数据时在画布中间放一句提示，避免只剩空白面板
    chart.setOption({
      graphic: [{
        type: 'text', left: 'center', top: 'middle',
        style: { text: '该作者暂无论文数据', fill: '#8296ae', fontSize: 13 }
      }]
    })
    return
  }

  // 领域 → 颜色：按论文首次出现的顺序分配（图例与散点同源，不会错位）
  const colorOf: Record<string, string> = {}
  const legend: { field: string; color: string }[] = []
  for (const p of papers.value) {
    const field = paperFields(p)[0] ?? '未分类'
    if (!(field in colorOf)) {
      colorOf[field] = PALETTE[legend.length % PALETTE.length]
      legend.push({ field, color: colorOf[field] })
    }
  }
  fieldColors.value = legend

  // 散点数据：同年论文错行排布（y = 年内行号），颜色按领域；阴影与点同色形成辉光
  const yearRow = new Map<number, number>()
  const data = papers.value.map(p => {
    const year = p.publicationYear ?? 0
    const row = yearRow.get(year) ?? 0
    yearRow.set(year, row + 1)
    const field = paperFields(p)[0] ?? '未分类'
    return {
      value: [sortDate(p), row],
      itemStyle: { color: colorOf[field], shadowBlur: 12, shadowColor: colorOf[field] }
    }
  })
  const maxRow = Math.max(1, ...yearRow.values())

  // 年份交替底色分区：相邻年份一深一浅，时间走向一眼可辨（markArea 画在散点下方）
  const years = Array.from(new Set(papers.value.map(p => p.publicationYear ?? 0).filter(y => y > 0)))
    .sort((a, b) => a - b)
  const yearAreas = years.map((y, i) => [
    {
      xAxis: `${y}-01-01`,
      itemStyle: { color: i % 2 === 0 ? 'rgba(87, 188, 255, 0.07)' : 'rgba(87, 188, 255, 0.025)' }
    },
    { xAxis: `${y + 1}-01-01` }
  ])

  chart.setOption({
    tooltip: {
      trigger: 'item',
      backgroundColor: '#10233d',
      borderColor: '#1d3657',
      borderWidth: 1,
      textStyle: { color: '#edf6ff' },
      extraCssText: 'border-radius: 8px; box-shadow: 0 6px 20px rgba(0, 0, 0, 0.45);',
      formatter: (params: any) => {
        const p = papers.value[params?.dataIndex]
        if (!p) return ''
        const field = paperFields(p).join(' / ') || '未分类'
        const dot = `<span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${params.color ?? '#57bcff'};margin-right:6px"></span>`
        return `${dot}<b>${p.title}</b><br/>发行时间：${p.publicationDate ?? (p.publicationYear ?? '—')}<br/>研究领域：${field}<br/>被引：${p.citationCount} 次`
      }
    },
    grid: { left: 48, right: 30, top: 40, bottom: 48 },
    xAxis: {
      type: 'time',
      boundaryGap: ['3%', '3%'],
      axisLabel: { color: '#8296ae', formatter: '{yyyy}' },
      axisLine: { lineStyle: { color: '#1d3657' } },
      splitLine: { show: false },
      // 悬停散点时出现竖向虚线，指示该论文在时间轴上的位置
      axisPointer: {
        show: true,
        type: 'line',
        snap: true,
        lineStyle: { color: 'rgba(87, 188, 255, 0.45)', type: 'dashed', width: 1 },
        label: { show: false }
      }
    },
    // 纵轴只是"同年错行"的占位行号，隐藏刻度
    yAxis: {
      type: 'value',
      min: -0.6,
      max: maxRow - 0.4,
      interval: 1,
      axisLabel: { show: false },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { show: false }
    },
    series: [{
      name: '论文',
      type: 'scatter',
      symbolSize: 14,
      data,
      // 年份底色分区画在散点层之下（markArea 属于 series，天然在下层）
      markArea: { silent: true, data: yearAreas },
      // 白描边让深色画布上的彩点更立体；点按顺序逐个浮现更有时间流动感
      itemStyle: { borderColor: 'rgba(255, 255, 255, 0.55)', borderWidth: 1 },
      emphasis: { scale: 1.55, itemStyle: { borderColor: '#ffffff', borderWidth: 2, shadowBlur: 18 } },
      select: { itemStyle: { borderColor: '#ffffff', borderWidth: 2, shadowBlur: 20 } },
      // 单选模式：点击的点白描边放大并保持选中，与右侧详情卡联动
      selectedMode: 'single',
      animationDuration: 700,
      animationEasing: 'cubicOut',
      animationDelay: (idx: number) => idx * 50
    }]
  }, true)
}

/** 论文的研究领域列表（关键词 → 领域映射去重；接口缺映射时退回关键词名） */
function paperFields(p: Paper): string[] {
  const fields = (p.keywords ?? []).map(k => fieldMap.value.get(k.id) ?? k.name)
  return Array.from(new Set(fields))
}

// ====================================================================
// 生命周期
// ====================================================================
function handleResize() {
  chart?.resize()
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await loadAuthorOptions()
  resolveDefaultAuthor()
  await loadAll()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.timeline-view {
  padding: var(--space-4);
}

.toolbar {
  display: flex;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  align-items: center;
  flex-wrap: wrap;
}

.legend {
  display: flex;
  gap: var(--space-3);
  margin-left: auto;
  flex-wrap: wrap;
}

/* 领域图例 = 胶囊徽章：圆点带同色辉光（inline style），与散点配色一一对应 */
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--muted);
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--accent-dark);
  border: 1px solid var(--border);
  white-space: nowrap;
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
}

/* 左右分栏：左图右详情卡；≤900px 上下堆叠 */
.split {
  display: flex;
  gap: var(--space-4);
  align-items: stretch;
}

.chart-card {
  flex: 1 1 62%;
  min-width: 0;
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
  height: 440px;
}

/* 详情卡：初始显示引导文案，点击散点后替换成论文信息 */
.detail-card {
  flex: 1 1 38%;
  min-width: 0;
  background: var(--raised);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-4);
}

.detail-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-3);
}

.detail-type {
  font-size: 12px;
  color: var(--accent-bright);
  background: rgba(45, 157, 245, 0.15);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
}

.detail-close {
  color: var(--muted);
}

.detail-title {
  margin: 0 0 var(--space-3) 0;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
  color: var(--ink);
}

.detail-line {
  margin: var(--space-1) 0;
  font-size: 13px;
  color: var(--muted);
}

.detail-k {
  color: var(--ink);
}

.detail-fields {
  margin-top: var(--space-2);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1);
  font-size: 13px;
}

/* 领域标签：边框/文字颜色由 inline style 跟图例同色，底改透明贴合深色卡片 */
.detail-fields :deep(.el-tag) {
  background: transparent;
  border-radius: 999px;
}

.detail-none {
  color: var(--muted);
}

.detail-abstract {
  margin-top: var(--space-3);
  padding-top: var(--space-3);
  border-top: 1px solid var(--border);
  font-size: 12px;
  color: var(--muted);
  line-height: 1.7;
  max-height: 180px;
  overflow: auto;
}

.empty-hint {
  text-align: center;
  color: var(--muted);
  padding: var(--space-6) var(--space-2);
  line-height: 1.8;
}

@media (max-width: 900px) {
  .split {
    flex-direction: column;
  }
}
</style>
