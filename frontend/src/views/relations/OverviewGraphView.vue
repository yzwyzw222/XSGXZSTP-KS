<template>
  <!--
    综合情况页（左侧导航板块 1/5）——某位作者的「学术关系知识图谱」总览
    ────────────────────────────────────────────────────────────────
    定位：后四个板块（发行时间 / 合作者 / 研究趋向 / 引用影响）的关系总和。
    围绕一位中心作者，把 TA 的论文、合作者、机构、关键词一次性铺成自我网络（ego network）：
      节点：作者（绿，中心作者更大加亮）/ 论文（蓝）/ 关键词（橙）/ 机构（紫）
      边：  AUTHORED 作者→论文、AFFILIATED 作者→机构、HAS_KEYWORD 论文→关键词、
            COAUTHOR_WITH 作者↔合作者（线宽∝合著篇数）
      只收录与中心作者有直接关系的内容：机构节点仅保留中心作者的署名机构，
      合作者的机构等无直接关系的内容不进图（只作文字信息留在合作者弹窗里）；
      发表渠道（venue）按需求不进图谱，渠道名仅作文字信息留在论文弹窗里
    数据来源（全部复用现有接口，前端组装）：
      - papersApi.list({size:100})：论文 + 嵌套作者/关键词/渠道（44 篇全量可一次取回）
      - relationsApi.authorTopics(authorId)：关键词→研究领域映射（Paper.keywords 只有 id+name，领域要靠这个接口补）
      - authorsApi.list({size:100})：作者下拉选项
    交互：
      - 点击任意节点 → 在节点旁弹出详情卡（论文：发行时间/研究领域/渠道/被引；作者：合著篇数/机构…）
      - 同时图谱「聚焦过滤」为该节点直接相连的关系（点作者→只显示作者与论文；点论文→论文+作者+关键词；
        点关键词/机构同理），顶部聚焦条可一键返回完整图谱
      - 点击图例（论文/作者/关键词/机构）→「类型高亮」：始终点亮中心作者 + 该类型的
        全部节点及它们之间的线（点论文→中心作者+论文+署名线；点机构→中心作者+机构+隶属线；
        关键词经论文作桥），点亮的关系线加粗、颜色与该类型节点一致
        （论文蓝/关键词橙/机构紫，合作线保持绿色），
        其余节点与边留在原位但变灰；再点同一图例项恢复全彩。
        高亮与聚焦两种模式互斥（后操作的生效）
    检索与历史：工具栏检索框实时查数据库（作者/论文分组下拉，选论文自动定位其第一作者并弹详情）；
      右侧面板保存「最近搜索」（选过的作者）与「浏览记录」（点过的节点），存 localStorage 跨刷新保留
  -->
  <div class="overview-view">
    <div class="toolbar">
      <!-- 数据库检索框：远程搜索作者与论文（每敲一次都实时查 MySQL，不是本地过滤） -->
      <el-select
        v-model="searchValue"
        filterable
        remote
        clearable
        :remote-method="remoteSearch"
        :loading="searchLoading"
        placeholder="数据库检索：作者 / 论文"
        style="width: 260px"
        @change="onSearchSelect"
      >
        <el-option-group v-for="g in searchOptions" :key="g.label" :label="g.label">
          <el-option v-for="o in g.options" :key="o.key" :label="o.label" :value="o.key" />
        </el-option-group>
      </el-select>
      <el-select v-model="selectedAuthorId" filterable placeholder="选择作者" style="width: 200px"
        @change="onAuthorPick">
        <el-option v-for="a in authorOptions" :key="a.id" :label="a.displayName" :value="a.id" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="reloadAll">刷新</el-button>
      <!-- 图例（可点击）：既是配色一览，又是「类型高亮」开关——点某类型点亮中心作者与它的
           关系（如论文→中心+论文+署名线；机构→中心+机构+隶属线），其余变灰；再点同一项恢复 -->
      <div class="legend">
        <button
          v-for="lg in LEGEND"
          :key="lg.type"
          class="legend-item"
          :class="{ active: highlightType === lg.type }"
          :title="`点亮中心作者与${lg.label}的关系，其余变灰（再点一次恢复）`"
          @click="onLegendClick(lg.type)"
        >
          <i class="legend-dot" :style="{ background: lg.color }" />{{ lg.label }}
        </button>
      </div>
    </div>

    <!-- 综合统计条：一眼看清该作者的学术画像规模 -->
    <div v-if="stats" class="stats-strip">
      <div class="stat"><b>{{ stats.paperCount }}</b><span>论文</span></div>
      <div class="stat"><b>{{ stats.coauthorCount }}</b><span>合作者</span></div>
      <div class="stat"><b>{{ stats.fieldCount }}</b><span>研究领域</span></div>
      <div class="stat"><b>{{ stats.citationSum }}</b><span>总被引</span></div>
      <div class="stat"><b>{{ stats.institutionCount }}</b><span>涉及机构</span></div>
    </div>

    <!-- 聚焦条：点击节点后进入「只看该节点直接关系」模式，可一键返回完整图谱 -->
    <div v-if="focusInfo" class="focus-bar">
      <span class="focus-chip" :style="{ borderColor: TYPE_COLORS[focusInfo.type] ?? '#2d9df5' }">
        {{ typeLabel(focusInfo.type) }} · {{ focusInfo.label }}
      </span>
      <span class="focus-desc">{{ focusDesc(focusInfo.type) }}</span>
      <el-button link type="primary" class="focus-back" @click="clearFocus">← 返回完整图谱</el-button>
    </div>

    <div class="graph-row" :class="{ 'graph-fullscreen': graphFullscreen }">
      <!-- 图谱容器（position:relative 让弹窗卡能按节点渲染坐标定位） -->
      <div ref="cyContainer" class="cy-container" v-loading="loading">
        <!-- 放大按钮：图谱铺满整个视口细看（再点一次或按 ESC 退出） -->
        <el-button class="graph-zoom-btn" size="small" @click.stop="toggleFullscreen">
          {{ graphFullscreen ? '✕ 退出放大' : '⛶ 放大' }}
        </el-button>
        <transition name="pop">
          <div v-if="popup" class="node-popup" :style="{ left: popup.x + 'px', top: popup.y + 'px' }">
            <div class="popup-head">
              <span class="popup-type">{{ typeLabel(popup.info.type) }}</span>
              <el-button link class="popup-close" @click="popup = null">✕</el-button>
            </div>
            <p class="popup-title">{{ popup.info.title }}</p>
            <p v-for="line in popup.info.lines" :key="line.k" class="popup-line">
              <span class="popup-k">{{ line.k }}：</span>{{ line.v }}
            </p>
            <div v-if="popup.info.tags.length > 0" class="popup-tags">
              <el-tag v-for="t in popup.info.tags" :key="t" size="small">{{ t }}</el-tag>
            </div>
          </div>
        </transition>
      </div>

      <!-- 右侧历史面板：最近搜索记录 + 浏览记录（存 localStorage，跨刷新保留）。
           放大模式下隐藏，让图谱独占视口 -->
      <div v-show="!graphFullscreen" class="history-panel">
        <div class="history-block">
          <div class="history-head">
            <span class="history-title">最近搜索</span>
            <el-button v-if="recentAuthors.length > 0" link size="small" @click="clearRecent">清空</el-button>
          </div>
          <div v-if="recentAuthors.length === 0" class="history-empty">暂无搜索记录</div>
          <div v-for="r in recentAuthors" :key="r.id" class="history-item" :title="`查看 ${r.name} 的知识图谱`"
            @click="pickRecent(r)">
            ↻ {{ r.name }}
          </div>
        </div>
        <div class="history-block">
          <div class="history-head">
            <span class="history-title">浏览记录</span>
            <el-button v-if="browseHistory.length > 0" link size="small" @click="clearBrowse">清空</el-button>
          </div>
          <div v-if="browseHistory.length === 0" class="history-empty">点击图中节点后这里会出现记录</div>
          <div v-for="b in browseHistory" :key="b.nodeId" class="history-item" :title="`回到 ${b.title}`"
            @click="jumpToBrowse(b)">
            <i class="history-dot" :style="{ background: TYPE_COLORS[b.kind] ?? '#8296ae' }" />
            <span class="history-name">{{ b.title }}</span>
            <span class="history-time">{{ formatTime(b.time) }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="!loading && nodeCount === 0" class="empty-hint">
      该作者暂无论文数据，请先在顶栏「数据管理」中录入论文并完成图谱同步
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 综合情况脚本：自我网络图谱的构建与节点弹窗
 * ───────────────────────────────────────────
 * 流程：
 *   1. onMounted：初始化 Cytoscape → 拉作者选项/论文全量 → 默认选中「姚期智」→ 构建图谱
 *   2. buildGraph()：以选中作者为中心，从 TA 的论文出发推导合作者/机构/关键词，
 *      全部节点/边先收集再一次性 add（避免悬空边白屏的老坑）；渠道不进图谱，
 *      仅作文字信息留在论文弹窗里
 *   3. 点击节点：读 renderedPosition 定位弹窗卡，内容按节点类型组装
 * 说明：不依赖 Neo4j 的合作边接口——合作者与合著篇数直接从论文的作者列表推导，
 *       对投影同步的时效性零依赖，且天然与论文数据一致。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import cytoscape from 'cytoscape'
import { authorsApi, papersApi, relationsApi, type Author, type Paper } from '../../api'

// ====================================================================
// 常量：节点类型 → 中文名 / 颜色（沿用全站配色语言）
// ====================================================================
const TYPE_LABELS: Record<string, string> = {
  paper: '论文', author: '作者', keyword: '关键词', institution: '机构'
}
const TYPE_COLORS: Record<string, string> = {
  paper: '#2d9df5', author: '#35c98c', keyword: '#f5a04b', institution: '#a77af2'
}
const LEGEND = (Object.keys(TYPE_LABELS) as string[]).map(t => ({ type: t, label: TYPE_LABELS[t], color: TYPE_COLORS[t] }))

function typeLabel(t: string) {
  return TYPE_LABELS[t] ?? t
}

// ====================================================================
// 状态
// ====================================================================
const cyContainer = ref<HTMLElement | null>(null)
let cy: cytoscape.Core | null = null

const authorOptions = ref<Author[]>([])
const selectedAuthorId = ref<number | null>(null)
const loading = ref(false)
const nodeCount = ref(0)

const papers = ref<Paper[]>([])
/** 关键词 id → 研究领域名（来自 author-topics 接口，补 Paper.keywords 缺的 fieldName） */
const fieldMap = ref<Map<number, string>>(new Map())

/** 弹窗卡：内容 + 渲染坐标（相对图谱容器左上角） */
interface PopupInfo {
  type: string
  title: string
  lines: { k: string; v: string }[]
  tags: string[]
}
const popup = ref<{ info: PopupInfo; x: number; y: number } | null>(null)

/** 综合统计条数据 */
const stats = ref<{
  paperCount: number
  coauthorCount: number
  fieldCount: number
  citationSum: number
  institutionCount: number
} | null>(null)

/** 节点 id → 节点元信息（弹窗内容的数据源，与画布节点一一对应） */
type NodeInfo =
  | { type: 'paper'; title: string; date: string | null; year: number | null; citations: number; venue: string | null; authors: string[]; keywordIds: number[] }
  | { type: 'author'; name: string; isCenter: boolean; sharedCount: number; institutions: string[]; paperCount: number; citationSum: number }
  | { type: 'keyword'; name: string; field: string | null }
  | { type: 'institution'; name: string; members: string[] }
const nodeInfoMap = new Map<string, NodeInfo>()

/** 全量图谱元素缓存：聚焦过滤模式从它里面挑子图，返回完整图谱时直接复用 */
let fullNodeMap = new Map<string, cytoscape.ElementDefinition>()
let fullEdgeList: cytoscape.ElementDefinition[] = []

/** 聚焦过滤状态：当前聚焦的节点（null = 完整图谱） */
const focusNodeId = ref<string | null>(null)
const focusInfo = ref<{ id: string; type: string; label: string } | null>(null)

/** 类型高亮状态：点击图例后只点亮该类型节点及相连边（null = 全彩，不高亮） */
const highlightType = ref<string | null>(null)

/** 图谱放大（全屏）状态：true 时图谱铺满整个视口，历史面板隐藏 */
const graphFullscreen = ref(false)

/**
 * 切换放大模式：容器尺寸变化后必须让 Cytoscape 重新测量画布（resize），
 * 再 fit 一次让整张图在更大的画布里居中可见
 */
function toggleFullscreen() {
  graphFullscreen.value = !graphFullscreen.value
  nextTick(() => {
    cy?.resize()
    cy?.fit(undefined, 48)
  })
}

/** ESC 键退出放大模式（与按钮双通道，操作更顺手） */
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && graphFullscreen.value) toggleFullscreen()
}

// ====================================================================
// 数据库检索（远程搜索：每次输入都实时查 MySQL 的作者表与论文表）
// ====================================================================
const searchValue = ref('')
const searchLoading = ref(false)
/** 分组下拉选项：作者一组、论文一组 */
interface SearchOption { key: string; label: string; kind: 'author' | 'paper' }
const searchOptions = ref<{ label: string; options: SearchOption[] }[]>([])
/** 最近一次搜索结果里的论文（选中论文项时要反查它的第一作者来定图谱中心） */
const searchPapers = ref<Paper[]>([])
/** 选完论文后待弹出的论文 id（等图谱重建完成后再定位弹窗） */
let pendingPaperPopup: number | null = null

/**
 * 远程搜索：输入即查库（authors + papers 两个接口并行），下拉分组展示。
 * key 约定：`author:123` / `paper:456`，选中后按前缀解析动作。
 */
async function remoteSearch(q: string) {
  if (!q || !q.trim()) {
    searchOptions.value = []
    return
  }
  searchLoading.value = true
  try {
    const [aRes, pRes] = await Promise.all([
      authorsApi.list({ keyword: q.trim(), size: 20 }),
      papersApi.list({ keyword: q.trim(), size: 20 })
    ])
    searchPapers.value = pRes.items
    searchOptions.value = [
      { label: '作者', options: aRes.items.map(a => ({ key: `author:${a.id}`, label: a.displayName, kind: 'author' as const })) },
      { label: '论文', options: pRes.items.map(p => ({ key: `paper:${p.id}`, label: p.title, kind: 'paper' as const })) }
    ]
  } catch {
    ElMessage.error('数据库检索失败')
  } finally {
    searchLoading.value = false
  }
}

/** 选中搜索结果：作者 → 切换图谱中心；论文 → 切到其第一作者并弹出该论文详情 */
function onSearchSelect(key: string | null) {
  if (!key) return
  searchValue.value = ''
  searchOptions.value = []
  const [kind, idStr] = key.split(':')
  const id = Number(idStr)
  if (kind === 'author') {
    if (selectedAuthorId.value !== id) {
      selectedAuthorId.value = id
      reloadAll()
    }
    pushRecentAuthor(id)
  } else if (kind === 'paper') {
    const p = searchPapers.value.find(x => x.id === id)
    const firstAuthorId = p?.authors?.[0]?.authorId
    if (firstAuthorId == null) {
      ElMessage.warning('该论文没有署名作者，无法定位图谱')
      return
    }
    selectedAuthorId.value = firstAuthorId
    pushRecentAuthor(firstAuthorId)
    pendingPaperPopup = id
    reloadAll()
  }
}

// ====================================================================
// 历史记录（最近搜索 + 浏览记录，存 localStorage，刷新页面后仍在）
// ====================================================================
const RECENT_KEY = 'overview.recentAuthors'
const BROWSE_KEY = 'overview.browseHistory'

interface RecentAuthor { id: number; name: string }
interface BrowseItem { nodeId: string; kind: string; title: string; time: number }

/** 从 localStorage 安全读 JSON（数据损坏时退回默认值，不让历史面板拖垮页面） */
function loadJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) as T : fallback
  } catch {
    return fallback
  }
}

function saveJson(key: string, value: unknown) {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // 隐私模式等场景写不进 localStorage：静默忽略，仅本次会话内有效
  }
}

const recentAuthors = ref<RecentAuthor[]>(loadJson(RECENT_KEY, []))
const browseHistory = ref<BrowseItem[]>(loadJson(BROWSE_KEY, []))

/** 记录一次作者搜索：去重后插到最前，最多留 8 条 */
function pushRecentAuthor(id: number) {
  const name = authorOptions.value.find(a => a.id === id)?.displayName
    ?? papers.value.find(p => (p.authors ?? []).some(a => a.authorId === id))?.authors.find(a => a.authorId === id)?.displayName
    ?? `作者#${id}`
  recentAuthors.value = [{ id, name }, ...recentAuthors.value.filter(r => r.id !== id)].slice(0, 8)
  saveJson(RECENT_KEY, recentAuthors.value)
}

/** 点击最近搜索条目：直接切回该作者的图谱 */
function pickRecent(r: RecentAuthor) {
  if (selectedAuthorId.value !== r.id) {
    selectedAuthorId.value = r.id
    reloadAll()
  }
  pushRecentAuthor(r.id)
}

function clearRecent() {
  recentAuthors.value = []
  localStorage.removeItem(RECENT_KEY)
}

function clearBrowse() {
  browseHistory.value = []
  localStorage.removeItem(BROWSE_KEY)
}

/** 点击浏览记录：进入该节点的聚焦视图，居中并重新弹出详情卡 */
function jumpToBrowse(b: BrowseItem) {
  if (!cy) return
  if (cy.getElementById(b.nodeId).length === 0) {
    ElMessage.info('该节点已不在当前图谱中（切换作者后图谱会重建）')
    return
  }
  setFocus(b.nodeId)
  const node = cy.getElementById(b.nodeId)
  cy.animate({ center: { eles: node }, zoom: 1.6 }, { duration: 300 })
  openPopup(node)
}

/** 时间显示成「MM-DD HH:mm」的短格式 */
function formatTime(t: number): string {
  const d = new Date(t)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
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

/** 默认中心作者：优先按姓名找「姚期智」（知网真实数据的主角作者），找不到再取列表第一位 */
function resolveDefaultAuthor() {
  if (selectedAuthorId.value !== null) return
  const hero = authorOptions.value.find(a => a.displayName === '姚期智')
  selectedAuthorId.value = hero ? hero.id : (authorOptions.value[0]?.id ?? null)
}

/** 全量加载：论文 + 当前作者的关键词领域映射，然后重建图谱 */
async function reloadAll() {
  if (selectedAuthorId.value === null) return
  loading.value = true
  try {
    const res = await papersApi.list({ size: 100 })
    papers.value = res.items
    const topics = await relationsApi.authorTopics(selectedAuthorId.value, 500)
    fieldMap.value = new Map(topics.map(t => [t.keywordId, t.fieldName ?? '未分类']))
    buildGraph()
  } catch {
    ElMessage.error('加载综合图谱数据失败')
  } finally {
    loading.value = false
  }
}

// ====================================================================
// 图谱构建
// ====================================================================
function buildGraph() {
  if (!cy) return
  nodeInfoMap.clear()
  cy.elements().remove()
  const centerId = selectedAuthorId.value
  if (centerId === null) {
    nodeCount.value = 0
    stats.value = null
    return
  }

  // 中心作者自己的论文（authors 里有 centerId 才算）
  const authorPapers = papers.value.filter(p => (p.authors ?? []).some(a => a.authorId === centerId))
  const centerName = authorOptions.value.find(a => a.id === centerId)?.displayName
    ?? authorPapers[0]?.authors.find(a => a.authorId === centerId)?.displayName
    ?? `作者#${centerId}`

  const nodeMap = new Map<string, cytoscape.ElementDefinition>()
  const edgeList: cytoscape.ElementDefinition[] = []
  let edgeId = 0
  const addNode = (def: cytoscape.ElementDefinition) => {
    const id = String(def.data.id)
    if (!nodeMap.has(id)) nodeMap.set(id, def)
  }
  const addEdge = (source: string, target: string, type: string, extra: Record<string, unknown> = {}) => {
    edgeList.push({ data: { id: `e${edgeId++}`, source, target, type, ...extra } })
  }

  // 中心作者节点：isCenter 标记让样式表把它画得更大
  const cId = `author_${centerId}`
  addNode({ data: { id: cId, type: 'author', label: centerName, isCenter: true } })

  // 合作者统计：合著篇数 + 署名机构（从论文作者列表推导，与论文数据天然一致）
  const coauthorStats = new Map<number, { name: string; shared: number; institutions: Set<string> }>()
  const institutionMembers = new Map<number, { name: string; members: Set<string> }>()

  for (const p of authorPapers) {
    const pId = `paper_${p.id}`
    addNode({ data: { id: pId, type: 'paper', label: p.title } })

    // 关键词节点 + HAS_KEYWORD 边
    for (const k of p.keywords ?? []) {
      const kId = `keyword_${k.id}`
      addNode({ data: { id: kId, type: 'keyword', label: k.name } })
      addEdge(pId, kId, 'HAS_KEYWORD')
    }

    // 作者/机构节点 + AUTHORED / AFFILIATED 边；非中心作者计入合作者统计
    for (const a of p.authors ?? []) {
      const aId = `author_${a.authorId}`
      addEdge(aId, pId, 'AUTHORED')
      if (a.authorId !== centerId) {
        const st = coauthorStats.get(a.authorId) ?? { name: a.displayName, shared: 0, institutions: new Set<string>() }
        st.shared += 1
        coauthorStats.set(a.authorId, st)
      }
      // 图谱只收录与中心作者有直接关系的内容：机构节点只建中心作者的署名机构，
      // 合作者的机构与中心作者无直接关系，不进图（仅作为文字信息留在合作者弹窗里）
      if (a.institutionId && a.authorId === centerId) {
        const iId = `institution_${a.institutionId}`
        addNode({ data: { id: iId, type: 'institution', label: a.institutionName ?? `机构#${a.institutionId}` } })
        addEdge(aId, iId, 'AFFILIATED')
        const im = institutionMembers.get(a.institutionId) ?? { name: a.institutionName ?? `机构#${a.institutionId}`, members: new Set<string>() }
        im.members.add(a.displayName)
        institutionMembers.set(a.institutionId, im)
      }
      if (a.institutionName && a.authorId !== centerId) {
        coauthorStats.get(a.authorId)?.institutions.add(a.institutionName)
      }
    }
  }

  // 合作者节点 + COAUTHOR_WITH 边（线宽∝合著篇数，图上直连省得绕论文中转）
  let maxShared = 1
  for (const st of coauthorStats.values()) maxShared = Math.max(maxShared, st.shared)
  for (const [coId, st] of coauthorStats) {
    const aId = `author_${coId}`
    addNode({ data: { id: aId, type: 'author', label: st.name } })
    addEdge(cId, aId, 'COAUTHOR_WITH', {
      label: String(st.shared),
      widthPx: 1 + ((st.shared - 1) / (maxShared - 1 || 1)) * 5
    })
  }

  // 缓存全量元素（聚焦模式按需取子图）+ 重置聚焦/高亮状态，然后一次性渲染
  fullNodeMap = nodeMap
  fullEdgeList = edgeList
  focusNodeId.value = null
  focusInfo.value = null
  highlightType.value = null
  renderGraph()
  nodeCount.value = nodeMap.size

  // ============ 弹窗用的节点元信息（与画布一一对应） ============
  for (const p of authorPapers) {
    nodeInfoMap.set(`paper_${p.id}`, {
      type: 'paper', title: p.title, date: p.publicationDate, year: p.publicationYear,
      citations: p.citationCount, venue: p.venue?.displayName ?? null,
      authors: (p.authors ?? []).map(a => a.displayName),
      keywordIds: (p.keywords ?? []).map(k => k.id)
    })
  }
  for (const [coId, st] of coauthorStats) {
    nodeInfoMap.set(`author_${coId}`, {
      type: 'author', name: st.name, isCenter: false, sharedCount: st.shared,
      institutions: Array.from(st.institutions),
      paperCount: 0, citationSum: 0
    })
  }
  for (const [instId, im] of institutionMembers) {
    nodeInfoMap.set(`institution_${instId}`, {
      type: 'institution', name: im.name, members: Array.from(im.members)
    })
  }
  for (const p of authorPapers) {
    for (const k of p.keywords ?? []) {
      nodeInfoMap.set(`keyword_${k.id}`, {
        type: 'keyword', name: k.name, field: fieldMap.value.get(k.id) ?? null
      })
    }
  }

  // ============ 中心作者元信息（统计条 + 弹窗共用） ============
  const citationSum = authorPapers.reduce((s, p) => s + (p.citationCount ?? 0), 0)
  nodeInfoMap.set(cId, {
    type: 'author', name: centerName, isCenter: true, sharedCount: 0,
    institutions: Array.from(new Set(authorPapers.flatMap(p => (p.authors ?? [])
      .filter(a => a.authorId === centerId && a.institutionName).map(a => a.institutionName as string)))),
    paperCount: authorPapers.length, citationSum
  })

  // ============ 统计条 ============
  stats.value = {
    paperCount: authorPapers.length,
    coauthorCount: coauthorStats.size,
    fieldCount: new Set(authorPapers.flatMap(p => (p.keywords ?? []).map(k => fieldMap.value.get(k.id) ?? k.name))).size,
    citationSum,
    institutionCount: institutionMembers.size
  }

  // 重建后关掉上一次的弹窗；搜索选中论文则居中该论文并弹出详情
  popup.value = null
  if (pendingPaperPopup !== null) {
    const node = cy.getElementById(`paper_${pendingPaperPopup}`)
    if (node.length > 0) {
      cy.animate({ center: { eles: node }, zoom: 1.6 }, { duration: 300 })
      openPopup(node)
    }
    pendingPaperPopup = null
  }
}

/**
 * 把给定元素渲染进画布（先节点后边，边只连存在的节点，杜绝悬空边白屏）。
 * 不传参时渲染全量图谱（聚焦模式返回完整视图时复用）。
 */
function renderGraph(nodeDefs?: cytoscape.ElementDefinition[], edgeDefs?: cytoscape.ElementDefinition[]) {
  if (!cy) return
  cy.elements().remove()
  cy.add(nodeDefs ?? Array.from(fullNodeMap.values()))
  cy.add(edgeDefs ?? fullEdgeList)
  // 同心圆布局：度数最高的节点自然落在圆心（聚焦模式下即聚焦节点居中）
  cy.layout({ name: 'concentric', animate: false, minNodeSpacing: 26, padding: 36 } as any).run()
}

/** 作者下拉切换：记录最近搜索后重建图谱 */
function onAuthorPick(authorId: number | null) {
  if (authorId === null) return
  pushRecentAuthor(authorId)
  reloadAll()
}

// ====================================================================
// 聚焦过滤：点击节点后，图谱只保留该节点直接相连的关系
// ====================================================================
/** 每种节点类型的聚焦范围说明（显示在顶部聚焦条上） */
const FOCUS_DESC: Record<string, string> = {
  author: '只显示 TA 署名发表的论文',
  paper: '只显示该论文的署名作者与关键词',
  keyword: '只显示该关键词涉及的论文及作者',
  institution: '只显示该机构的作者及其论文'
}

function focusDesc(t: string): string {
  return FOCUS_DESC[t] ?? '只显示直接相连的关系'
}

/**
 * 按聚焦节点类型从全量元素里挑子图：
 *  - 直接边：与聚焦节点相连的指定类型边（作者→AUTHORED、论文→AUTHORED+HAS_KEYWORD…）
 *  - 二级边：关键词/机构聚焦时，把关联论文/作者的 AUTHORED 边也带上，
 *    让"关键词→论文→作者"这类链式关系在图上一眼可见
 */
function collectFocusElements(focusId: string, focusType: string) {
  const directTypes: Record<string, string[]> = {
    author: ['AUTHORED'],
    paper: ['AUTHORED', 'HAS_KEYWORD'],
    keyword: ['HAS_KEYWORD'],
    institution: ['AFFILIATED']
  }
  const secondTypes: Record<string, string[]> = {
    keyword: ['AUTHORED'],
    institution: ['AUTHORED']
  }
  const included: cytoscape.ElementDefinition[] = []
  for (const e of fullEdgeList) {
    const s = String(e.data.source)
    const t = String(e.data.target)
    const type = String(e.data.type)
    if ((directTypes[focusType] ?? []).includes(type) && (s === focusId || t === focusId)) {
      included.push(e)
    }
  }
  if (secondTypes[focusType]) {
    const seeds = new Set<string>([focusId])
    for (const e of included) {
      seeds.add(String(e.data.source))
      seeds.add(String(e.data.target))
    }
    for (const e of fullEdgeList) {
      const s = String(e.data.source)
      const t = String(e.data.target)
      const type = String(e.data.type)
      if ((secondTypes[focusType] ?? []).includes(type) && (seeds.has(s) || seeds.has(t))) {
        included.push(e)
      }
    }
  }
  // 收集子图涉及的节点；聚焦节点克隆一份并加 isFocus 标记（样式表据此高亮描边）
  const nodeMap = new Map<string, cytoscape.ElementDefinition>()
  const focusDef = fullNodeMap.get(focusId)
  if (focusDef) {
    nodeMap.set(focusId, { ...focusDef, data: { ...focusDef.data, isFocus: true } })
  }
  for (const e of included) {
    for (const id of [String(e.data.source), String(e.data.target)]) {
      if (id === focusId || nodeMap.has(id)) continue
      const d = fullNodeMap.get(id)
      if (d) nodeMap.set(id, d)
    }
  }
  return { nodeDefs: Array.from(nodeMap.values()), edgeDefs: included }
}

/** 点击节点 → 进入聚焦模式（同一节点重复点击不重建；聚焦与类型高亮互斥，进入即取消高亮） */
function setFocus(id: string) {
  if (focusNodeId.value === id) return
  const def = fullNodeMap.get(id)
  if (!def) return
  focusNodeId.value = id
  focusInfo.value = { id, type: String(def.data.type), label: String(def.data.label) }
  highlightType.value = null
  const { nodeDefs, edgeDefs } = collectFocusElements(id, String(def.data.type))
  renderGraph(nodeDefs, edgeDefs)
}

/** 返回完整图谱 */
function clearFocus() {
  if (focusNodeId.value === null) return
  focusNodeId.value = null
  focusInfo.value = null
  renderGraph()
  popup.value = null
}

// ====================================================================
// 类型高亮：始终以「中心作者」为主角，点击图例点亮他与该类型的关系
// ====================================================================
/** 需要论文当「桥」的类型：关键词与中心作者不直接相连，中间隔着一层论文 */
const HIGHLIGHT_BRIDGE_EDGES: Record<string, string[]> = {
  keyword: ['HAS_KEYWORD']
}

/**
 * 点击图例项：同一项再点一次取消高亮；换类型直接切换。
 * 与聚焦模式互斥——聚焦是把图谱收成子图，高亮需要基于完整图谱，
 * 所以先在聚焦状态下点图例会退回全图再高亮。
 */
function onLegendClick(type: string) {
  if (highlightType.value === type) {
    highlightType.value = null
  } else {
    if (focusNodeId.value !== null) {
      focusNodeId.value = null
      focusInfo.value = null
      renderGraph()
    }
    highlightType.value = type
  }
  applyTypeHighlight()
}

/**
 * 把高亮状态落到画布上（以中心作者为核心）：
 *   1. 点亮区 = 中心作者 ∪ 该类型的全部节点（点论文→中心+论文；点机构→中心+机构）；
 *   2. 关键词与中心作者隔着论文，把与它们相连的论文一起点亮当桥，
 *      让「中心作者—论文—关键词」这条链完整可见；
 *   3. 线只点亮「两端都在点亮区内」的（中心作者的署名线/隶属线/合作线），
 *      点亮线加粗（hl-lit 类）且颜色跟随该类型节点色，其余点线留在原位
 *      打 hl-dim 类变灰（样式表按类压透明度）。
 */
function applyTypeHighlight() {
  if (!cy) return
  cy.elements().removeClass('hl-dim hl-lit')
  // 清掉上一次的线色覆盖（bypass 样式），避免切换类型时残留旧颜色
  cy.edges().removeStyle('line-color target-arrow-color')
  const t = highlightType.value
  if (!t) return

  const center = cy.nodes('[isCenter]')
  const targetNodes = cy.nodes(`[type = "${t}"]`)
  let litNodes: cytoscape.CollectionReturnValue = cy.collection().union(center).union(targetNodes)

  // 桥接节点：与目标类型相连的论文（仅关键词需要）
  for (const bridgeType of HIGHLIGHT_BRIDGE_EDGES[t] ?? []) {
    const bridgeEdges = cy.edges(`[type = "${bridgeType}"]`).filter(e =>
      e.connectedNodes().intersection(targetNodes).nonempty())
    litNodes = litNodes.union(bridgeEdges.connectedNodes())
  }

  // 两端都在点亮区内的边一起点亮；非合作边打 hl-lit 类（样式表里加粗），
  // 线色跟随该类型节点的颜色（论文蓝/关键词橙/机构紫），合作边保持原绿色
  const litEdges = cy.edges().filter(e =>
    e.connectedNodes().every(n => litNodes.contains(n)))
  const litColor = TYPE_COLORS[t] ?? '#6fd3ff'
  const styledLit = litEdges.filter(e => e.data('type') !== 'COAUTHOR_WITH')
  styledLit.addClass('hl-lit')
  styledLit.style({ 'line-color': litColor, 'target-arrow-color': litColor })
  const lit = litNodes.union(litEdges)
  cy.elements().difference(lit).addClass('hl-dim')
}

// ====================================================================
// 节点弹窗
// ====================================================================
function toPopupInfo(info: NodeInfo): PopupInfo {
  switch (info.type) {
    case 'paper': {
      const fields = Array.from(new Set(info.keywordIds.map(id => fieldMap.value.get(id) ?? '未分类')))
      return {
        type: 'paper',
        title: info.title,
        lines: [
          { k: '发行时间', v: info.date ?? (info.year !== null ? `${info.year} 年` : '—') },
          { k: '渠道', v: info.venue ?? '—' },
          { k: '被引', v: String(info.citations) },
          { k: '作者', v: info.authors.join('、') }
        ],
        tags: fields
      }
    }
    case 'author':
      if (info.isCenter) {
        return {
          type: 'author',
          title: info.name,
          lines: [
            { k: '论文', v: `${info.paperCount} 篇` },
            { k: '总被引', v: String(info.citationSum) },
            { k: '署名机构', v: info.institutions.length > 0 ? info.institutions.join('、') : '—' }
          ],
          tags: ['中心作者']
        }
      }
      return {
        type: 'author',
        title: info.name,
        lines: [
          { k: '与中心作者合著', v: `${info.sharedCount} 篇` },
          { k: '署名机构', v: info.institutions.length > 0 ? info.institutions.join('、') : '—' }
        ],
        tags: info.sharedCount >= 2 ? ['核心合作伙伴'] : []
      }
    case 'keyword':
      return {
        type: 'keyword', title: info.name,
        lines: [{ k: '研究领域', v: info.field ?? '未分类' }],
        tags: []
      }
    case 'institution':
      return {
        type: 'institution', title: info.name,
        lines: [{ k: '成员作者', v: info.members.length > 0 ? info.members.join('、') : '—' }],
        tags: []
      }
  }
}

/** 打开节点弹窗：按节点渲染坐标定位，并夹在容器范围内防止溢出；同时记入浏览记录 */
function openPopup(node: cytoscape.NodeSingular) {
  const info = nodeInfoMap.get(node.id())
  if (!info || !cyContainer.value) return
  const pos = node.renderedPosition()
  const rect = cyContainer.value.getBoundingClientRect()
  const x = Math.min(Math.max(pos.x + 18, 8), Math.max(8, rect.width - 296))
  const y = Math.min(Math.max(pos.y - 70, 8), Math.max(8, rect.height - 260))
  const popupInfo = toPopupInfo(info)
  popup.value = { info: popupInfo, x, y }

  // 浏览记录：同节点去重后插到最前，最多留 10 条
  browseHistory.value = [
    { nodeId: node.id(), kind: info.type, title: popupInfo.title, time: Date.now() },
    ...browseHistory.value.filter(b => b.nodeId !== node.id())
  ].slice(0, 10)
  saveJson(BROWSE_KEY, browseHistory.value)
}

// ====================================================================
// Cytoscape 初始化
// ====================================================================
onMounted(async () => {
  if (!cyContainer.value) return
  cy = cytoscape({
    container: cyContainer.value,
    style: [
      // 节点基础样式：圆形 + 下方标签
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
          'background-color': '#2d9df5',
          width: 'data(size)',
          height: 'data(size)'
        }
      },
      // 按类型着色 + 尺寸（中心作者更大更醒目）
      ...(Object.entries(TYPE_COLORS).map(([t, c]) => ({
        selector: `node[type="${t}"]`,
        style: { 'background-color': c }
      })) as cytoscape.StylesheetStyle[]),
      {
        selector: 'node[type="paper"]',
        style: { width: 24, height: 24 }
      },
      {
        selector: 'node[type="author"]',
        style: { width: 26, height: 26 }
      },
      {
        selector: 'node[type="keyword"]',
        style: { width: 18, height: 18 }
      },
      {
        selector: 'node[type="institution"]',
        style: { width: 30, height: 30 }
      },
      // 中心作者：更大 + 蓝色描边
      {
        selector: 'node[isCenter]',
        style: { width: 38, height: 38, 'border-width': 3, 'border-color': '#57bcff' }
      },
      // 聚焦节点：点击后橙色描边高亮（放在 isCenter 之后，聚焦中心作者时橙色优先）
      {
        selector: 'node[isFocus]',
        style: { 'border-width': 3, 'border-color': '#f5a04b' }
      },
      // 事实边：灰色细线带箭头
      {
        selector: 'edge',
        style: {
          'line-color': '#3a5070',
          'target-arrow-color': '#3a5070',
          'target-arrow-shape': 'triangle',
          width: 1,
          'curve-style': 'unbundled-bezier'
        }
      },
      // 合作边：绿线无箭头，线宽∝合著篇数，边上标篇数
      {
        selector: 'edge[type="COAUTHOR_WITH"]',
        style: {
          'line-color': '#35c98c',
          width: 'data(widthPx)',
          'target-arrow-shape': 'none',
          'curve-style': 'bezier',
          opacity: 0.9,
          label: 'data(label)',
          'font-size': '8px',
          color: '#9fe3c4',
          'text-rotation': 'autorotate',
          'text-background-color': '#0d1b2a',
          'text-background-opacity': 0.75,
          'text-background-padding': '1px'
        }
      },
      // 选中/悬停反馈
      {
        selector: 'node:selected',
        style: { 'border-width': 3, 'border-color': '#57bcff' }
      },
      {
        selector: 'node.hovered',
        style: { opacity: 0.8 }
      },
      // 类型高亮：非相关元素打 hl-dim 类 → 压到很低的透明度（节点连描边一起去掉），
      // 视觉上"留在原位但变灰"；必须放在 COAUTHOR_WITH 规则之后，才能盖过它的 opacity 0.9
      {
        selector: 'node.hl-dim',
        style: { opacity: 0.15, 'border-width': 0 }
      },
      {
        selector: 'edge.hl-dim',
        style: { opacity: 0.07 }
      },
      // 类型高亮：点亮的关系线（合作边除外）加粗；颜色在运行时按类型动态覆盖
      // （bypass 样式，与该类型节点色一致），此处冰蓝仅作兜底
      {
        selector: 'edge.hl-lit',
        style: {
          'line-color': '#6fd3ff',
          'target-arrow-color': '#6fd3ff',
          width: 3,
          opacity: 1
        }
      }
    ],
    layout: { name: 'concentric', animate: false },
    wheelSensitivity: 0.3
  })

  // 点击节点 → 弹出详情卡 + 图谱聚焦为该节点的直接关系；点击空白 → 关闭弹窗
  cy.on('tap', 'node', (evt) => {
    const node = evt.target as cytoscape.NodeSingular
    openPopup(node)
    setFocus(node.id())
  })
  cy.on('tap', (evt) => {
    if (evt.target === cy) popup.value = null
  })

  await loadAuthorOptions()
  resolveDefaultAuthor()
  await reloadAll()
  window.addEventListener('keydown', onKeydown)

  // 开发模式下暴露实例，便于控制台调试与自动化测试
  if (import.meta.env.DEV) {
    (window as any).__cy = cy
    ;(window as any).__focusNode = setFocus
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  cy?.destroy()
  cy = null
})
</script>

<style scoped>
.overview-view {
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
}

/* 图例项 = 可点击的「类型高亮」开关：悬停轻微提亮，激活态描边凸显 */
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--muted);
  font-size: 12px;
  background: none;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  padding: 2px 6px;
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease, background-color 0.15s ease;
}

.legend-item:hover {
  color: var(--ink);
  background: var(--accent-dark);
}

.legend-item.active {
  color: var(--ink);
  border-color: var(--accent);
  background: var(--accent-dark);
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}

/* 综合统计条：五格横排，≤680px 时换行 */
.stats-strip {
  display: flex;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  flex-wrap: wrap;
}

.stat {
  flex: 1;
  min-width: 90px;
  background: var(--paper);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-3);
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  justify-content: center;
}

.stat b {
  font-size: 20px;
  color: var(--accent-bright);
}

.stat span {
  color: var(--muted);
  font-size: 13px;
}

/* 聚焦条：进入「只看直接关系」模式时的提示与返回入口 */
.focus-bar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  background: var(--paper);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-2) var(--space-3);
  margin-bottom: var(--space-4);
}

.focus-chip {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
  border-left: 3px solid var(--accent);
  padding-left: var(--space-2);
  white-space: nowrap;
}

.focus-desc {
  color: var(--muted);
  font-size: 12px;
}

.focus-back {
  margin-left: auto;
}

/* 图谱 + 历史面板的左右分栏：≤900px 上下堆叠 */
.graph-row {
  display: flex;
  gap: var(--space-4);
  align-items: stretch;
}

/* 放大模式：图谱脱离文档流铺满整个视口（历史面板此时隐藏） */
.graph-row.graph-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 60;
  background: var(--background);
  padding: var(--space-4);
  gap: 0;
}

.graph-fullscreen .cy-container {
  flex: 1;
  height: auto;
}

/* 放大按钮：浮在图谱右上角，不遮挡图内交互 */
.graph-zoom-btn {
  position: absolute;
  right: var(--space-2);
  top: var(--space-2);
  z-index: 11;
}

.cy-container {
  position: relative;
  flex: 1 1 0%;
  min-width: 0;
  height: 520px;
  background: var(--paper-deep);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

/* 历史面板：最近搜索 + 浏览记录两个小块，超出滚动 */
.history-panel {
  width: 250px;
  flex-shrink: 0;
  background: var(--paper);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-3);
  height: 520px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.history-block {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.history-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-1);
}

.history-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
}

.history-empty {
  color: var(--muted);
  font-size: 12px;
  padding: var(--space-2) 0;
}

.history-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--muted);
  font-size: 12px;
  padding: var(--space-2);
  border-radius: var(--radius-sm);
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
}

.history-item:hover {
  background: var(--accent-dark);
  color: var(--ink);
}

.history-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.history-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}

.history-time {
  color: var(--muted);
  opacity: 0.7;
  flex-shrink: 0;
}

@media (max-width: 900px) {
  .graph-row {
    flex-direction: column;
  }

  /* 堆叠布局下必须关掉 flex 收缩（flex-basis 0% 会把图谱压成一条线），改回固定高度 */
  .cy-container {
    flex: none;
    width: 100%;
    height: 400px;
  }

  .graph-fullscreen .cy-container {
    flex: 1;
    height: auto;
  }

  .history-panel {
    width: 100%;
    height: auto;
    max-height: 260px;
  }
}

.empty-hint {
  text-align: center;
  color: var(--muted);
  padding: var(--space-6);
}

/* 节点详情弹窗卡：绝对定位在点击节点旁，带淡入动画 */
.node-popup {
  position: absolute;
  width: 280px;
  background: var(--raised);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-3);
  z-index: 10;
  box-shadow: 0 8px 24px rgb(0 0 0 / 40%);
}

.popup-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-2);
}

.popup-type {
  font-size: 12px;
  color: var(--accent-bright);
  background: rgba(45, 157, 245, 0.15);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
}

.popup-close {
  color: var(--muted);
  font-size: 14px;
}

.popup-title {
  margin: 0 0 var(--space-2) 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--ink);
  line-height: 1.4;
}

.popup-line {
  margin: var(--space-1) 0;
  font-size: 12px;
  color: var(--muted);
  line-height: 1.5;
}

.popup-k {
  color: var(--ink);
}

.popup-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  margin-top: var(--space-2);
}

.pop-enter-active,
.pop-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.pop-enter-from,
.pop-leave-to {
  opacity: 0;
  transform: scale(0.95);
}
</style>
