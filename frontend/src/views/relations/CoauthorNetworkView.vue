<template>
  <!--
    合作者页（左侧导航板块 3/5）
    ────────────────────────────
    以「某位作者」为中心，回答"谁和 TA 合作过、合作多紧密、是否长期核心伙伴"：
      左：Cytoscape 作者自我网络（中心作者 + 其合作者，边宽∝合著篇数；只画与选中作者有关的子网）
      右：合作者明细表（合著篇数 / 合作跨度 / 同机构 / 核心伙伴标签 / 共同论文 / 设为中心）
    「核心长期合作伙伴」判定：合著 ≥ 2 篇 且 合作跨越 ≥ 2 个不同年份（论文数据前端推导，无后端依赖）。
    数据源：
      - analyticsApi.collaborations(200)：全局作者对 + 合著篇数 → 取与选中作者有关的边画图
      - relationsApi.coauthors(authorId)：合作者明细（共同论文/机构）→ 填表
      - papersApi.list({size:100})：算合作年份跨度（识别"长期"）
    交互：点击图中节点 = 把该作者设为中心；最小合著篇数阈值同步过滤图与表。
  -->
  <div class="coauthor-view">
    <div class="toolbar">
      <el-select v-model="selectedAuthorId" filterable placeholder="选择作者" style="width: 220px"
        @change="onAuthorChange">
        <el-option v-for="a in authorOptions" :key="a.id" :label="a.displayName" :value="a.id" />
      </el-select>
      <!-- 「最小合著篇数」标签与数字输入框是一组控件，视觉上收拢在一起，与其它控件保持正常间距 -->
      <span class="filter-group">
        <span class="filter-label">最小合著篇数</span>
        <el-input-number v-model="minCoauthorCount" :min="1" :max="50" @change="applyGraph" />
      </span>
      <el-button type="primary" @click="refresh">刷新</el-button>
    </div>

    <div class="main-split">
      <!-- 左：自我网络合作图 -->
      <div class="graph-panel">
        <div ref="cyContainer" class="cy-container" v-loading="loadingGraph" />
        <div v-if="!loadingGraph && nodeCount === 0" class="empty-hint">
          暂无可展示的合作关系：需要论文有两位以上作者，且合著篇数不低于当前阈值
        </div>
      </div>

      <!-- 右：合作者明细（表格限高内部滚动，高度与左侧图协调，不把页面撑长） -->
      <div class="table-panel">
        <h3 class="panel-title">合作者明细（{{ tableRows.length }} 人）</h3>
        <el-table :data="tableRows" v-loading="loadingTable" size="small" max-height="400"
          empty-text="暂无合作者数据">
          <el-table-column prop="authorName" label="姓名" width="100" />
          <el-table-column prop="paperCount" label="合著篇数" width="86" sortable />
          <el-table-column prop="yearSpan" label="合作跨度" width="110">
            <template #default="{ row }">{{ row.yearSpan }}<span v-if="row.yearSpan !== '—'"> 年</span></template>
          </el-table-column>
          <el-table-column label="同机构" width="84">
            <template #default="{ row }">
              <el-tag v-if="row.sameInstitution" type="success" size="small">同机构</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="核心伙伴" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.core" type="warning" size="small">核心长期伙伴</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="共同论文" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ (row.paperTitles ?? []).join('；') }}</template>
          </el-table-column>
          <el-table-column label="操作" width="96">
            <template #default="{ row }">
              <el-button link type="primary" @click="setCenter(row.authorId)">设为中心</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 合作者页脚本：自我网络图 + 合作者明细表
 * ───────────────────────────────────────
 * 流程：
 *   1. onMounted：拉作者选项 → 默认选中「姚期智」→ 拉论文全量/合作边表/合作者明细 → 画图
 *   2. applyGraph()：把全局合作边表按"自我网络"过滤（两端都必须 ∈ {中心作者 ∪ 其合作者}），
 *      再按最小合著阈值筛一道，线宽∝合著篇数
 *   3. 点节点/设为中心 → 换中心作者，重新拉明细并重画
 * 高亮：中心作者 + 其邻居加 highlighted 类，其余淡化（与全站同一套视觉语言）。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import cytoscape from 'cytoscape'
import { analyticsApi, authorsApi, papersApi, relationsApi, type Author, type CollaborationItem, type CoauthorItem, type Paper } from '../../api'

// ====================================================================
// 状态
// ====================================================================
const cyContainer = ref<HTMLElement | null>(null)
let cy: cytoscape.Core | null = null // Cytoscape 实例（非响应式，避免性能问题）

const authorOptions = ref<Author[]>([])
const selectedAuthorId = ref<number | null>(null)
const minCoauthorCount = ref(1)
const loadingGraph = ref(false)
const loadingTable = ref(false)
const nodeCount = ref(0)

/** 全局合作边表（缓存，切中心作者/改阈值时直接重画，不重新请求） */
const collabData = ref<CollaborationItem[]>([])
/** 当前中心作者的合作者明细（来自 relations/coauthors） */
const coauthors = ref<CoauthorItem[]>([])
/** 论文全量（用于算合作年份跨度，识别"长期"伙伴） */
const papers = ref<Paper[]>([])

/** 表格按最小合著篇数过滤（与图同步） */
const filteredCoauthors = computed(() =>
  coauthors.value.filter(c => c.paperCount >= minCoauthorCount.value))

/**
 * 合作者 → 合作年份集合（从论文作者列表推导）。
 * 只统计中心作者也署名的论文，保证跨度和合著篇数口径一致。
 */
const coauthorYears = computed(() => {
  const map = new Map<number, number[]>()
  if (selectedAuthorId.value === null) return map
  for (const p of papers.value) {
    const authors = p.authors ?? []
    if (!authors.some(a => a.authorId === selectedAuthorId.value)) continue
    for (const a of authors) {
      if (a.authorId === selectedAuthorId.value || p.publicationYear === null) continue
      const years = map.get(a.authorId) ?? []
      years.push(p.publicationYear)
      map.set(a.authorId, years)
    }
  }
  return map
})

/** 表格行：明细 + 合作跨度 + 核心伙伴判定（合著≥2 篇且跨越≥2 个年份 = 长期合作） */
interface CoauthorRow extends CoauthorItem {
  yearSpan: string
  core: boolean
}
const tableRows = computed<CoauthorRow[]>(() =>
  filteredCoauthors.value.map(c => {
    const years = Array.from(new Set(coauthorYears.value.get(c.authorId) ?? [])).sort((a, b) => a - b)
    const yearSpan = years.length === 0 ? '—'
      : years.length === 1 ? String(years[0])
      : `${years[0]}–${years[years.length - 1]}`
    return { ...c, yearSpan, core: c.paperCount >= 2 && years.length >= 2 }
  }))

// ====================================================================
// 数据加载
// ====================================================================

/** 作者下拉选项（filterable，数据量小一次性取 100 条） */
async function loadAuthorOptions() {
  try {
    const res = await authorsApi.list({ size: 100 })
    authorOptions.value = res.items
  } catch {
    ElMessage.error('加载作者列表失败')
  }
}

/** 默认中心作者：优先「姚期智」（知网真实数据的主角作者），找不到再取列表第一位 */
function resolveDefaultAuthor() {
  if (selectedAuthorId.value !== null) return
  const hero = authorOptions.value.find(a => a.displayName === '姚期智')
  selectedAuthorId.value = hero ? hero.id : (authorOptions.value[0]?.id ?? null)
}

/** 合作边表（供自我网络过滤） */
async function loadCollabData() {
  loadingGraph.value = true
  try {
    collabData.value = await analyticsApi.collaborations(200)
  } catch {
    ElMessage.error('加载合作网络失败')
  } finally {
    loadingGraph.value = false
  }
}

/** 某作者的合作者明细（含共同论文标题与同机构标记） */
async function loadCoauthors() {
  if (selectedAuthorId.value === null) {
    coauthors.value = []
    return
  }
  loadingTable.value = true
  try {
    coauthors.value = await relationsApi.coauthors(selectedAuthorId.value)
  } catch {
    ElMessage.error('加载合作者明细失败')
  } finally {
    loadingTable.value = false
  }
}

/** 论文全量（算合作跨度用；合作者页只需年份字段，与综合情况页共用同一次拉取成本） */
async function loadPapers() {
  try {
    const res = await papersApi.list({ size: 100 })
    papers.value = res.items
  } catch {
    ElMessage.error('加载论文数据失败')
  }
}

// ====================================================================
// 图谱构建（Cytoscape）
// ====================================================================

/**
 * 画自我网络：只保留"两端都在 {中心作者 ∪ 其合作者} 里"的合作边（自我网络过滤），
 * 再按最小合著阈值筛一道。
 * 关键点（沿袭图谱页踩过的坑）：先 remove 清空画布、只 add 两端都在节点集里的边，
 * 否则悬空边会让 Cytoscape 抛错、整图白屏。
 */
function applyGraph() {
  if (!cy) return
  const egoSet = new Set<number>([selectedAuthorId.value as number])
  coauthors.value.forEach(c => egoSet.add(c.authorId))
  const filtered = collabData.value.filter(e =>
    e.paperCount >= minCoauthorCount.value
    && egoSet.has(e.author1Id) && egoSet.has(e.author2Id))

  // 节点：中心作者 + 过滤后边表里出现过的合作者（顺带统计度数定节点大小）
  const nodeMap = new Map<string, { id: string; label: string; degree: number; center: boolean }>()
  if (selectedAuthorId.value !== null) {
    const name = authorOptions.value.find(a => a.id === selectedAuthorId.value)?.displayName ?? '中心作者'
    nodeMap.set(`a${selectedAuthorId.value}`, { id: `a${selectedAuthorId.value}`, label: name, degree: 0, center: true })
  }
  for (const e of filtered) {
    for (const [id, label] of [[e.author1Id, e.author1], [e.author2Id, e.author2]] as const) {
      const key = `a${id}`
      const node = nodeMap.get(key)
      if (node) {
        node.degree += 1
      } else {
        nodeMap.set(key, { id: key, label, degree: 1, center: false })
      }
    }
  }

  const maxCount = filtered.reduce((m, e) => Math.max(m, e.paperCount), 1)
  const edges: cytoscape.ElementDefinition[] = filtered.map(e => ({
    data: {
      id: `col_${e.author1Id}_${e.author2Id}`,
      source: `a${e.author1Id}`,
      target: `a${e.author2Id}`,
      type: 'COAUTHOR_WITH',
      label: String(e.paperCount),
      widthPx: 1 + ((e.paperCount - 1) / (maxCount - 1 || 1)) * 5
    }
  }))
  // 节点大小随度数增长；中心作者稍大（整体比早期版本缩小一圈，图面更清爽）
  const nodes: cytoscape.ElementDefinition[] = Array.from(nodeMap.values()).map(n => ({
    data: {
      id: n.id, type: 'author', label: n.label,
      size: (n.center ? 26 : 12) + Math.min(n.degree, 6)
    }
  }))

  cy.elements().remove()
  cy.add(nodes)
  const edgeEles = cy.add(edges)
  nodeCount.value = nodes.length
  // 稳定布局（手动等分圆周）：合作者均匀落在圆环上（n 个点构成正 n 边形，
  // 6 个合作者时恰好是正六边形），中心作者钉在圆心——形成"圆心 + 圆环"骨架。
  // 位置纯数学计算，同一数据每次渲染完全一致，不像力导向布局那样乱晃。
  if (selectedAuthorId.value !== null) {
    const center = cy.getElementById(`a${selectedAuthorId.value}`)
    center.position({ x: 0, y: 0 })
    const ringNodes = cy.nodes().filter(n => n.id() !== center.id())
    const n = ringNodes.length
    ringNodes.forEach((node, i) => {
      // 从正上方开始顺时针等分，保证对称；半径留出节点与标签的空间
      const angle = (2 * Math.PI * i) / n - Math.PI / 2
      node.position({ x: Math.cos(angle) * 150, y: Math.sin(angle) * 150 })
    })
  }
  cy.fit(undefined, 44)
  highlightSelected()
  // 边逐条渐入：先全部透明，再按顺序延迟依次淡入，形成"关系逐渐建立"的动画效果
  edgeEles.style('opacity', 0)
  edgeEles.forEach((e, i) => {
    e.delay(60 * i).animate({ style: { opacity: 0.85 } }, { duration: 250 })
  })
}

/**
 * 高亮合作子网络：中心作者 + 其邻居节点加亮（highlighted），
 * 其余节点与边淡化（dimmed），让"围绕谁看合作"一目了然
 */
function highlightSelected() {
  if (!cy) return
  cy.elements().removeClass('highlighted dimmed')
  if (selectedAuthorId.value === null) return

  const center = cy.getElementById(`a${selectedAuthorId.value}`)
  if (center.length === 0) return
  const related = new Set<string>([center.id()])
  center.connectedEdges().forEach(edge => {
    related.add(edge.source().id())
    related.add(edge.target().id())
    edge.addClass('highlighted')
  })
  cy.nodes().forEach(n => {
    if (related.has(n.id())) n.addClass('highlighted')
    else n.addClass('dimmed')
  })
  cy.edges().forEach(e => {
    if (!related.has(e.source().id()) || !related.has(e.target().id())) e.addClass('dimmed')
  })
}

// ====================================================================
// 交互
// ====================================================================

/** 把某位作者设为中心：拉明细 + 重画高亮（选中自己时不必重复请求） */
function setCenter(authorId: number) {
  if (selectedAuthorId.value === authorId) return
  selectedAuthorId.value = authorId
  loadCoauthors().then(() => applyGraph())
}

/** 下拉切换作者 */
function onAuthorChange(authorId: number | null) {
  if (authorId !== null) setCenter(authorId)
}

/** 刷新：重新拉边表与明细，再按当前阈值重画 */
async function refresh() {
  await loadCollabData()
  await loadCoauthors()
  applyGraph()
}

// ====================================================================
// Cytoscape 初始化
// ====================================================================
onMounted(async () => {
  if (!cyContainer.value) return
  cy = cytoscape({
    container: cyContainer.value,
    style: [
      // 作者节点：绿色圆形 + 下方标签，大小随度数（data(size)）
      {
        selector: 'node',
        style: {
          label: 'data(label)',
          'text-valign': 'bottom',
          'text-halign': 'center',
          'font-size': '10px',
          color: '#edf6ff',
          'text-max-width': '90px',
          'text-wrap': 'ellipsis',
          'background-color': '#35c98c',
          width: 'data(size)' as any,
          height: 'data(size)' as any
        }
      },
      // 合著边：绿线无箭头（合作是无向关系），线宽 = data(widthPx)，边上标合著篇数
      {
        selector: 'edge',
        style: {
          'line-color': '#35c98c',
          width: 'data(widthPx)' as any,
          'target-arrow-shape': 'none',
          'curve-style': 'bezier',
          opacity: 0.85,
          label: 'data(label)',
          'font-size': '8px',
          color: '#9fe3c4',
          'text-rotation': 'autorotate',
          'text-background-color': '#0d1b2a',
          'text-background-opacity': 0.75,
          'text-background-padding': '1px'
        }
      },
      // 选中态：中心作者蓝色描边
      {
        selector: 'node:selected',
        style: { 'border-width': 3, 'border-color': '#57bcff' }
      },
      // 合作子网络高亮：相关节点亮边框，其余淡化
      {
        selector: 'node.highlighted',
        style: { 'border-width': 3, 'border-color': '#57bcff', opacity: 1 }
      },
      {
        selector: 'node.dimmed',
        style: { opacity: 0.15 }
      },
      {
        selector: 'edge.highlighted',
        style: { 'line-color': '#57bcff', opacity: 1 }
      },
      {
        selector: 'edge.dimmed',
        style: { opacity: 0.1 }
      }
    ],
    layout: { name: 'cose', animate: false },
    wheelSensitivity: 0.3
  })

  // 点击节点 = 把该作者设为中心
  cy.on('tap', 'node', (evt) => {
    const node = evt.target as cytoscape.NodeSingular
    setCenter(Number(node.id().replace('a', '')))
  })

  await loadAuthorOptions()
  resolveDefaultAuthor()
  await loadPapers()
  await loadCollabData()
  await loadCoauthors()
  applyGraph()

  // 开发模式下暴露实例，便于控制台调试与自动化测试
  if (import.meta.env.DEV) {
    (window as any).__cy = cy
  }
})

onBeforeUnmount(() => {
  cy?.destroy()
  cy = null
})
</script>

<style scoped>
.coauthor-view {
  padding: var(--space-4);
}

.toolbar {
  display: flex;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  align-items: center;
  flex-wrap: wrap;
}

.filter-label {
  color: var(--muted);
  font-size: 13px;
}

/* 「标签 + 输入框」组合控件：内部收拢（8px），与工具栏其它控件保持 12px 间距 */
.filter-group {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

/* 左右分栏：左图右表；≤900px 上下堆叠 */
.main-split {
  display: flex;
  gap: var(--space-4);
  align-items: flex-start;
}

.graph-panel {
  flex: 1 1 55%;
  min-width: 0;
}

.table-panel {
  flex: 1 1 45%;
  min-width: 0;
  background: var(--paper);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-4);
}

.panel-title {
  margin: 0 0 var(--space-3) 0;
  font-size: 15px;
}

@media (max-width: 900px) {
  .main-split {
    flex-direction: column;
  }

  .graph-panel,
  .table-panel {
    width: 100%;
  }

  /* 堆叠布局：图高度收敛（宽屏分栏时 460px），表格内部滚动，图与表一屏内大致可见 */
  .graph-panel .cy-container {
    height: 340px;
  }
}

.cy-container {
  height: 460px;
  background: var(--paper-deep);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
}

.empty-hint {
  text-align: center;
  color: var(--muted);
  padding: var(--space-6);
}
</style>
