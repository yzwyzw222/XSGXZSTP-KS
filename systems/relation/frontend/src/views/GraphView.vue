<template>
  <!--
    图谱可视化页（GraphView）：Cytoscape 力导向图
    ─────────────────────────────────────────────────
    技术方案：
      1. 从后端拉取论文数据（含作者/关键词/渠道/引用）+ 抽取图谱数据（研究实体/LLM 关系），
         在前端构建 nodes + edges
      2. Cytoscape 渲染力导向布局（cose），节点按类型着色
      3. 工具栏：关键词搜索（200ms 防抖）、实体类型过滤、节点上限 300（性能保护）、保存查询
      4. 交互：单击节点弹出详情抽屉；双击节点两跳展开（聚焦模式，≤300 节点截断）；
              鼠标滚轮缩放；拖拽平移
    节点类型与颜色：
      - paper（论文）：主蓝色 --accent
      - author（作者）：绿色 --green
      - keyword（关键词）：橙色 --amber
      - institution（机构）：紫色 --violet
      - research（研究实体：方法/数据集/工具）：亮蓝 #57bcff
    边类型：
      - AUTHORED（作者→论文）
      - AFFILIATED（作者→机构）
      - HAS_KEYWORD（论文→关键词）
      - CITES（论文→论文）
      - PUBLISHED_IN（论文→渠道）
      - COAUTHOR_WITH（作者↔作者，派生边）：由 Neo4j 物化的合作关系边，
        weight=共同署名论文数，画成直连线且线宽随 weight 变化，打开开关才加载
      - EXTRACTED_FROM（抽取实体→来源论文，LLM 抽取证据边）：亮蓝虚线
      - LLM_RELATION（六类 LLM 关系边）：粉红虚线 + 关系类型标签
  -->
  <div class="graph-view">
    <!-- 工具栏：搜索 + 过滤 + 聚焦 + 保存查询 + 重建按钮 -->
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索论文标题/作者…" clearable style="width: 260px"
        @input="onKeywordInput" @keyup.enter="rebuildGraph" @clear="rebuildGraph" />
      <el-select v-model="nodeLimit" placeholder="节点上限" style="width: 130px" @change="rebuildGraph">
        <el-option :value="50" label="50 节点" />
        <el-option :value="100" label="100 节点" />
        <el-option :value="200" label="200 节点" />
        <el-option :value="300" label="300 节点" />
      </el-select>
      <el-checkbox-group v-model="visibleTypes" @change="applyFilter">
        <el-checkbox label="paper">论文</el-checkbox>
        <el-checkbox label="author">作者</el-checkbox>
        <el-checkbox label="keyword">关键词</el-checkbox>
        <el-checkbox label="institution">机构</el-checkbox>
        <el-checkbox label="research">研究实体</el-checkbox>
      </el-checkbox-group>
      <!-- 合作关系开关：打开后作者之间直接连线（不必再经过论文节点中转），线宽=合著篇数 -->
      <el-switch v-model="showCoauthor" active-text="合作关系" @change="onCoauthorToggle" />
      <!-- 聚焦模式（双击节点进入）：只显示该节点两跳内的子图 -->
      <el-button v-if="focusMode" @click="exitFocus()">退出聚焦</el-button>
      <el-button type="primary" @click="rebuildGraph">刷新图谱</el-button>
      <!-- 保存查询：把当前关键词存进 localStorage，之后一键回填重查 -->
      <el-button @click="saveCurrentQuery">保存查询</el-button>
      <el-dropdown v-if="savedQueries.length > 0" trigger="click">
        <el-button>已存查询（{{ savedQueries.length }}）</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="q in savedQueries" :key="q.keyword" @click="applySavedQuery(q)">
              <span class="query-item">{{ q.label }}</span>
              <el-button link type="danger" size="small" @click.stop="removeSavedQuery(q.keyword)">删除</el-button>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 图谱容器：Cytoscape 挂载到此 DOM 节点 -->
    <div ref="cyContainer" class="cy-container" v-loading="loading" />

    <!-- 节点详情抽屉（点击节点弹出）：按节点类型渲染不同内容 -->
    <el-drawer v-model="drawerVisible" size="380px" append-to-body :with-header="false">
      <div v-if="selectedNode" class="drawer-body">
        <div class="detail-header">
          <span class="detail-type">{{ typeLabel(selectedNode.data('type')) }}</span>
          <el-button link @click="clearSelection">✕</el-button>
        </div>
        <h3 class="drawer-title">{{ selectedNode.data('label') }}</h3>

        <!-- 论文节点：元数据 + 邻居分组（作者/关键词/引用/抽取实体，可点聚焦） -->
        <template v-if="selectedNode.data('type') === 'paper'">
          <p v-if="selectedNode.data('paperType')">
            <strong>类型：</strong>{{ paperTypeLabel(selectedNode.data('paperType')) }}
          </p>
          <p v-if="selectedNode.data('year')"><strong>年份：</strong>{{ selectedNode.data('year') }}</p>
          <p v-if="selectedNode.data('citations') !== undefined">
            <strong>被引：</strong>{{ selectedNode.data('citations') }}
          </p>
          <p v-if="selectedNode.data('doi')"><strong>DOI：</strong>{{ selectedNode.data('doi') }}</p>
          <p v-if="selectedNode.data('venue')"><strong>渠道：</strong>{{ selectedNode.data('venue') }}</p>
          <div v-for="group in paperNeighborGroups(selectedNode.id())" :key="group.label" class="neighbor-section">
            <p class="neighbor-title">{{ group.label }}（{{ group.nodes.length }}）</p>
            <div class="neighbor-tags">
              <el-tag v-for="n in group.nodes" :key="n.id" size="small" class="neighbor-tag"
                @click="focusNode(n.id)">
                {{ n.name }}
              </el-tag>
            </div>
          </div>
        </template>

        <!-- 作者节点：所属机构 + 合著者（按论文分组） -->
        <template v-else-if="selectedNode.data('type') === 'author'">
          <div v-if="authorInstitutions(selectedNode.id()).length > 0" class="neighbor-section">
            <p class="neighbor-title">所属机构（{{ authorInstitutions(selectedNode.id()).length }}）</p>
            <div class="neighbor-tags">
              <el-tag v-for="inst in authorInstitutions(selectedNode.id())" :key="inst.id" size="small"
                type="warning" class="neighbor-tag" @click="focusNode(inst.id)">
                {{ inst.name }}
              </el-tag>
            </div>
          </div>
          <div v-if="coAuthorGroups.length > 0" class="coauthor-section">
            <p class="coauthor-title">合著者（{{ coAuthorCount }} 人）</p>
            <div v-for="group in coAuthorGroups" :key="group.paperId" class="coauthor-group">
              <p class="coauthor-paper">{{ group.paperTitle }}</p>
              <div class="coauthor-tags">
                <el-tag v-for="ca in group.coAuthors" :key="ca.id" size="small" type="success"
                  class="coauthor-tag" @click="focusNode(ca.id)">
                  {{ ca.name }}
                </el-tag>
              </div>
            </div>
          </div>
          <p v-else class="coauthor-empty">暂无合著数据</p>
        </template>

        <!-- 研究实体节点（方法/数据集/工具）：类别 + 来源论文 -->
        <template v-else-if="selectedNode.data('type') === 'research'">
          <p v-if="selectedNode.data('entityType')">
            <strong>类别：</strong>{{ entityTypeLabel(selectedNode.data('entityType')) }}
          </p>
          <div class="neighbor-section">
            <p class="neighbor-title">来源论文（{{ researchSourcePapers(selectedNode.id()).length }}）</p>
            <div class="neighbor-tags">
              <el-tag v-for="p in researchSourcePapers(selectedNode.id())" :key="p.id" size="small"
                type="primary" class="neighbor-tag" @click="focusNode(p.id)">
                {{ p.name }}
              </el-tag>
            </div>
          </div>
        </template>

        <!-- 其余节点：通用字段 -->
        <template v-else>
          <p v-if="selectedNode.data('field')"><strong>领域：</strong>{{ selectedNode.data('field') }}</p>
        </template>
      </div>
    </el-drawer>

    <!-- 空状态提示 -->
    <div v-if="!loading && nodeCount === 0" class="empty-hint">
      暂无数据，请先在「数据管理」页添加论文及关联实体
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 图谱可视化脚本：Cytoscape 力导向图
 * ─────────────────────────────────────
 * 核心流程：
 *   1. rebuildGraph() 并行拉取论文列表与抽取图谱数据，去重生成 nodes + edges
 *   2. Cytoscape 初始化并渲染，绑定 tap（详情抽屉）/ dbltap（两跳聚焦）事件
 * 性能保护：
 *   - 节点总数上限 nodeLimit（默认 100，最大 300），超出截断
 *   - 过滤时只隐藏节点（remove/restore），不重新请求数据
 *   - 关键词输入 200ms 防抖 + AbortController：新请求发出前取消旧请求
 *   - 两跳聚焦子图超过 300 节点时截断并提示
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import cytoscape from 'cytoscape'
import { analyticsApi, extractionApi, papersApi, type Paper } from '../api'

// ====================================================================
// 状态
// ====================================================================
const cyContainer = ref<HTMLElement | null>(null)
let cy: cytoscape.Core | null = null // Cytoscape 实例（非响应式，避免性能问题）

const loading = ref(false)
const keyword = ref('')
const nodeLimit = ref(100)
const nodeCount = ref(0)
const selectedNode = ref<cytoscape.NodeSingular | null>(null)

/** 合著者分组：按论文列出与该作者共同发表的其他作者 */
interface CoAuthorGroup {
  paperId: string
  paperTitle: string
  coAuthors: { id: string; name: string }[]
}
const coAuthorGroups = ref<CoAuthorGroup[]>([])
const coAuthorCount = ref(0)

/** 当前可见的节点类型（默认全选，含研究实体） */
const visibleTypes = ref(['paper', 'author', 'keyword', 'institution', 'research'])

/** 全量节点/边数据（过滤时从中筛选，不重新请求） */
let allNodes: cytoscape.ElementDefinition[] = []
let allEdges: cytoscape.ElementDefinition[] = []

/**
 * 合作关系（COAUTHOR_WITH）派生边的开关与缓存。
 * 与 allEdges 分开存放：这批边来自另一个接口（Neo4j 物化边），
 * 开关切换时只要把它们并入/移出画布即可，不必重新请求论文数据；
 * coauthorLoaded 保证只在第一次打开开关时请求一次（懒加载）。
 */
const showCoauthor = ref(false)
const coauthorLoaded = ref(false)
let coauthorEdgeDefs: cytoscape.ElementDefinition[] = []

/** 抽取图谱数据（研究实体/EXTRACTED_FROM/LLM 关系）：与主图谱并行拉取，失败降级为空 */
let extractionNodes: cytoscape.ElementDefinition[] = []
let extractionEdges: cytoscape.ElementDefinition[] = []

/** 关键词防抖定时器与请求取消控制器（新请求发出前取消旧请求，组件卸载时取消在途请求） */
let keywordTimer: number | null = null
let currentAbort: AbortController | null = null

// ====================================================================
// 聚焦模式（双击节点两跳展开）
// ====================================================================
/** 聚焦子图的节点 id 集合；null 表示未聚焦（显示全图）。必须是 ref：模板里 focusMode 依赖它做响应式更新 */
const focusVisibleIds = ref<Set<string> | null>(null)
const focusRootId = ref<string | null>(null)
const focusMode = computed(() => focusVisibleIds.value !== null)

// ====================================================================
// 中文标签
// ====================================================================
const TYPE_LABELS: Record<string, string> = {
  paper: '论文', author: '作者', keyword: '关键词', institution: '机构', research: '研究实体'
}
const PAPER_TYPE_LABELS: Record<string, string> = {
  JOURNAL_ARTICLE: '期刊论文', CONFERENCE_PAPER: '会议论文', PATENT: '专利', OTHER: '其他'
}
const RESEARCH_ENTITY_LABELS: Record<string, string> = {
  METHOD: '方法', DATASET: '数据集', TOOL: '工具'
}

function typeLabel(t: string) { return TYPE_LABELS[t] ?? t }
function paperTypeLabel(t: string) { return PAPER_TYPE_LABELS[t] ?? t }
function entityTypeLabel(t: string) { return RESEARCH_ENTITY_LABELS[t] ?? t }

// ====================================================================
// 抽取图谱数据
// ====================================================================

/**
 * 拉取抽取图谱数据并转成 Cytoscape 元素定义。
 * 后端只返回"已归并"的实体节点（research_/author_/institution_/keyword_ 前缀）
 * 与两类边：extracted=EXTRACTED_FROM 证据边、llm=六类 LLM 关系边。
 * 失败时降级为空（主图谱不受影响），由 rebuildGraph 静默处理。
 */
async function loadExtractionData() {
  try {
    const data = await extractionApi.graphData()
    extractionNodes = data.nodes.map((n) => ({
      data: {
        id: n.id,
        type: n.type,
        label: n.label,
        entityType: n.entityType ?? undefined
      }
    }))
    extractionEdges = data.edges.map((e) => ({
      data: {
        id: e.id,
        source: e.source,
        target: e.target,
        // 两类边换成图内边类型名，样式表据此画成亮蓝/粉红虚线
        type: e.kind === 'llm' ? 'LLM_RELATION' : 'EXTRACTED_FROM',
        label: e.kind === 'llm' ? e.label : undefined,
        evidence: e.evidence
      }
    }))
  } catch {
    extractionNodes = []
    extractionEdges = []
  }
}

/** 缺失端点占位节点：论文在候选列表之外时补一个「论文#id」节点，保证边接得上 */
function placeholderDef(id: string): cytoscape.ElementDefinition {
  const [prefix, num] = id.split('_')
  const labelPrefix: Record<string, string> = {
    paper: '论文', author: '作者', institution: '机构', keyword: '关键词', research: '研究实体'
  }
  return { data: { id, type: prefix, label: `${labelPrefix[prefix] ?? '实体'}#${num ?? '?'}` } }
}

// ====================================================================
// 图谱构建
// ====================================================================

/**
 * 拉取论文数据与抽取数据并构建图谱。
 * 策略：每篇论文产生 1 个 paper 节点；其 authors/keywords/venue/references 各产生对应节点和边；
 *       抽取的研究实体节点并入同一张图，EXTRACTED_FROM/LLM 边接在对应业务节点上。
 * 使用 Map 去重：同一作者出现在多篇论文中只生成 1 个节点。
 */
async function rebuildGraph() {
  loading.value = true
  selectedNode.value = null
  coAuthorGroups.value = []
  coAuthorCount.value = 0
  // 合作关系边是派生数据：论文变了权重就可能过时，重建时一律作废缓存
  coauthorLoaded.value = false
  coauthorEdgeDefs = []
  // 重建即退出聚焦（不重绘旧图，稍后 applyFilter 会统一画新图）
  focusVisibleIds.value = null
  focusRootId.value = null
  clearHighlight()
  // 取消上一次未完成的搜索请求与待触发的防抖（手动刷新与防抖共用一套取消逻辑）
  if (keywordTimer !== null) {
    window.clearTimeout(keywordTimer)
    keywordTimer = null
  }
  currentAbort?.abort()
  const controller = new AbortController()
  currentAbort = controller
  const aborted = () => controller.signal.aborted

  try {
    // 论文主数据与抽取数据并行拉取（抽取失败时 loadExtractionData 内部降级为空）
    const [res] = await Promise.all([
      papersApi.list({ keyword: keyword.value.trim() || undefined, size: 100 }, controller.signal),
      loadExtractionData()
    ])
    if (aborted()) return
    const papers: Paper[] = res.items

    const nodeMap = new Map<string, cytoscape.ElementDefinition>()
    const edgeList: cytoscape.ElementDefinition[] = []
    let edgeId = 0

    for (const p of papers) {
      if (nodeMap.size >= nodeLimit.value) break

      // 论文节点（data 里多带 doi/渠道/类型，详情抽屉直接读）
      const pId = `paper_${p.id}`
      if (!nodeMap.has(pId)) {
        nodeMap.set(pId, {
          data: {
            id: pId, type: 'paper', label: p.title, year: p.publicationYear,
            citations: p.citationCount, doi: p.doi ?? undefined,
            venue: p.venue?.displayName ?? undefined, paperType: p.paperType
          }
        })
      }

      // 作者节点 + AUTHORED 边
      for (const a of p.authors ?? []) {
        const aId = `author_${a.authorId}`
        if (!nodeMap.has(aId)) {
          nodeMap.set(aId, { data: { id: aId, type: 'author', label: a.displayName } })
        }
        edgeList.push({ data: { id: `e${edgeId++}`, source: aId, target: pId, type: 'AUTHORED' } })

        // 机构节点 + AFFILIATED 边
        if (a.institutionId) {
          const iId = `institution_${a.institutionId}`
          if (!nodeMap.has(iId)) {
            nodeMap.set(iId, { data: { id: iId, type: 'institution', label: a.institutionName ?? `机构#${a.institutionId}` } })
          }
          edgeList.push({ data: { id: `e${edgeId++}`, source: aId, target: iId, type: 'AFFILIATED' } })
        }
      }

      // 关键词节点 + HAS_KEYWORD 边
      for (const k of p.keywords ?? []) {
        const kId = `keyword_${k.id}`
        if (!nodeMap.has(kId)) {
          nodeMap.set(kId, { data: { id: kId, type: 'keyword', label: k.name } })
        }
        edgeList.push({ data: { id: `e${edgeId++}`, source: pId, target: kId, type: 'HAS_KEYWORD' } })
      }

      // 引用边 CITES（论文→论文）
      for (const r of p.references ?? []) {
        if (r.citedPaperId) {
          const cId = `paper_${r.citedPaperId}`
          // 被引论文可能不在当前列表中，补充一个占位节点
          if (!nodeMap.has(cId)) {
            nodeMap.set(cId, { data: { id: cId, type: 'paper', label: r.citedTitle ?? `论文#${r.citedPaperId}` } })
          }
          edgeList.push({ data: { id: `e${edgeId++}`, source: pId, target: cId, type: 'CITES' } })
        }
      }
    }

    // 抽取的研究实体节点并入同一张图（id 与主图重复时跳过，label 相同无需覆盖）
    for (const en of extractionNodes) {
      const id = en.data.id as string
      if (nodeMap.has(id)) continue
      if (nodeMap.size >= nodeLimit.value) break
      nodeMap.set(id, en)
    }
    // 抽取边：端点缺失时在节点上限内补占位节点，仍接不上就丢弃（防悬空边）
    for (const ee of extractionEdges) {
      const s = ee.data.source as string
      const t = ee.data.target as string
      if (!nodeMap.has(s) && nodeMap.size < nodeLimit.value) nodeMap.set(s, placeholderDef(s))
      if (!nodeMap.has(t) && nodeMap.size < nodeLimit.value) nodeMap.set(t, placeholderDef(t))
      if (nodeMap.has(s) && nodeMap.has(t)) edgeList.push(ee)
    }

    allNodes = Array.from(nodeMap.values())
    allEdges = edgeList
    nodeCount.value = allNodes.length
    // 开关是开着的就把合作边重新拉回来（上面刚作废了缓存），保证与最新论文数据一致
    if (showCoauthor.value) {
      await loadCoauthorEdges()
    }
    applyFilter()
  } catch (err) {
    // 被取消的请求（防抖替换/组件卸载）不算失败，不弹错误提示
    if (!aborted()) {
      ElMessage.error('加载图谱数据失败')
    }
  } finally {
    loading.value = false
    if (currentAbort === controller) currentAbort = null
  }
}

/**
 * 按 visibleTypes 过滤节点：只保留勾选类型的节点，以及两端都在保留集合内的边
 * 做法是 cy.elements().remove() 清空画布后，从原始 allNodes/allEdges 重新 add
 * 关键点：必须先过滤掉"端点已被隐藏"的边，否则 Cytoscape 添加悬空边时直接抛错，
 *        整个图谱会构建失败（这正是之前取消勾选某一类型后白屏的原因）
 * 聚焦模式下：可见节点 = 类型过滤 ∩ 两跳聚焦集合，其余一律隐藏
 * 合作关系边也走同一条过滤：作者类型被取消勾选、或某位作者不在当前节点窗口内时，
 *        对应的合作边会被自动剔除，不会重现悬空边问题
 */
function applyFilter() {
  if (!cy) return
  const types = new Set(visibleTypes.value)
  let visibleNodes = allNodes.filter(n => types.has(n.data.type as string))
  if (focusVisibleIds.value) {
    visibleNodes = visibleNodes.filter(n => focusVisibleIds.value!.has(n.data.id as string))
  }
  // 可见节点 id 集合，用于筛边
  const visibleIds = new Set(visibleNodes.map(n => n.data.id))
  // 开关打开时把派生的合作边并进候选集，关闭时只用事实边
  const candidateEdges = showCoauthor.value ? allEdges.concat(coauthorEdgeDefs) : allEdges
  // 只保留两端都可见的边：端点被过滤掉的边若强行加入，Cytoscape 会抛错导致整图构建失败
  const visibleEdges = candidateEdges.filter(e => visibleIds.has(e.data.source as string) && visibleIds.has(e.data.target as string))
  // 先全部移除，再恢复可见节点与合法边
  cy.elements().remove()
  cy.add(visibleNodes)
  cy.add(visibleEdges)
  nodeCount.value = visibleNodes.length
  // 重新运行布局让节点重新排列
  cy.layout({ name: 'cose', animate: false, nodeRepulsion: () => 8000, idealEdgeLength: () => 100 } as any).run()
}

// ====================================================================
// 双击两跳聚焦
// ====================================================================

/** 用当前全量边（含合作边）建无向邻接表，聚焦 BFS 用 */
function buildAdjacency(): Map<string, Set<string>> {
  const adj = new Map<string, Set<string>>()
  const edges = showCoauthor.value ? allEdges.concat(coauthorEdgeDefs) : allEdges
  for (const e of edges) {
    const s = e.data.source as string
    const t = e.data.target as string
    if (!adj.has(s)) adj.set(s, new Set())
    if (!adj.has(t)) adj.set(t, new Set())
    adj.get(s)!.add(t)
    adj.get(t)!.add(s)
  }
  return adj
}

/**
 * 进入聚焦模式：BFS 展开根节点两跳以内的子图。
 * 超过 300 节点截断（取前 300 个）并提示，防止大图把画布拖垮。
 */
function enterFocus(nodeId: string) {
  if (!cy) return
  const adj = buildAdjacency()
  const visited = new Set<string>([nodeId])
  let frontier = [nodeId]
  for (let depth = 0; depth < 2; depth++) {
    const next: string[] = []
    for (const id of frontier) {
      for (const nb of adj.get(id) ?? []) {
        if (!visited.has(nb)) {
          visited.add(nb)
          next.push(nb)
        }
      }
    }
    frontier = next
    if (frontier.length === 0) break
  }
  if (visited.size > 300) {
    focusVisibleIds.value = new Set([...visited].slice(0, 300))
    ElMessage.warning('两跳展开超过 300 节点，已截断展示前 300 个')
  } else {
    focusVisibleIds.value = visited
  }
  focusRootId.value = nodeId
  applyFilter()
}

/** 退出聚焦模式；repaint=false 供 rebuildGraph 内部使用（避免用旧数据重绘一遍） */
function exitFocus(repaint = true) {
  focusVisibleIds.value = null
  focusRootId.value = null
  if (repaint) applyFilter()
}

// ====================================================================
// 合作关系派生边（Neo4j 物化的 COAUTHOR_WITH）
// ====================================================================

/** 合著篇数 → 线宽：1 篇最细，篇数越多越粗，封顶 9px 免得糊成一团 */
function weightToWidth(weight: number) {
  return Math.min(1.5 + (Math.max(weight, 1) - 1) * 1.2, 9)
}

/**
 * 拉取合作关系边并转成 Cytoscape 边定义。
 * 后端返回的是 { author1Id, author1, author2Id, author2, paperCount }，
 * paperCount 就是物化边上的 weight（共同署名论文数）。
 * 作者节点 id 的拼法必须与 rebuildGraph 里一致（author_ 前缀 + MySQL 主键），否则接不上。
 */
async function loadCoauthorEdges() {
  try {
    const edges = await analyticsApi.coauthorEdges(500)
    coauthorEdgeDefs = edges.map(e => {
      const weight = e.paperCount ?? 1
      return {
        data: {
          id: `co_${e.author1Id}_${e.author2Id}`,
          source: `author_${e.author1Id}`,
          target: `author_${e.author2Id}`,
          type: 'COAUTHOR_WITH',
          label: String(weight),   // 边上直接标合著篇数
          weight,
          widthPx: weightToWidth(weight)
        }
      }
    })
    coauthorLoaded.value = true
    // 派生边是投影产物：图里一篇论文都没有、或从没跑过同步时会是空集，
    // 给一句可操作的提示，免得用户以为开关坏了
    if (coauthorEdgeDefs.length === 0) {
      ElMessage.info('暂无合作关系边：需要论文有两位以上作者，若刚导入数据请到「权限管理」页点「同步图谱」重算')
    }
  } catch (err) {
    ElMessage.error('加载合作关系失败')
    showCoauthor.value = false
  }
}

/** 开关切换：首次打开时懒加载，之后直接用缓存（点刷新图谱会重新拉取） */
async function onCoauthorToggle(value: boolean | string | number) {
  if (value && !coauthorLoaded.value) {
    await loadCoauthorEdges()
  }
  applyFilter()
}

// ====================================================================
// 合著者遍历与高亮
// ====================================================================

/**
 * 给定作者节点 ID，遍历 Cytoscape 图找出所有合著者：
 *   作者 → AUTHORED 边 → 论文 → AUTHORED 边 → 其他作者
 * 返回按论文分组的合著者列表，同时在图上高亮合作子网络
 */
function findCoAuthors(authorNodeId: string) {
  if (!cy) return
  const graph = cy // 局部引用，让 TypeScript 在闭包内也能推断非空
  const authorNode = graph.getElementById(authorNodeId)
  if (!authorNode || authorNode.data('type') !== 'author') return

  const groups: CoAuthorGroup[] = []
  const coAuthorIds = new Set<string>()
  const paperIds = new Set<string>()

  // 遍历该作者的所有 AUTHORED 边，找到关联的论文
  const authoredEdges = authorNode.connectedEdges('edge[type="AUTHORED"]')
  authoredEdges.forEach(edge => {
    // AUTHORED 边的 source 是作者，target 是论文
    const paperNode = edge.data('source') === authorNodeId
      ? graph.getElementById(edge.data('target'))
      : graph.getElementById(edge.data('source'))
    if (!paperNode) return

    const paperId = paperNode.id()
    paperIds.add(paperId)

    // 找出该论文的所有其他作者
    const coAuthors: { id: string; name: string }[] = []
    const paperAuthorEdges = paperNode.connectedEdges('edge[type="AUTHORED"]')
    paperAuthorEdges.forEach(ae => {
      const otherEnd = ae.data('source') === paperId
        ? graph.getElementById(ae.data('target'))
        : graph.getElementById(ae.data('source'))
      if (otherEnd && otherEnd.id() !== authorNodeId && otherEnd.data('type') === 'author') {
        coAuthors.push({ id: otherEnd.id(), name: otherEnd.data('label') })
        coAuthorIds.add(otherEnd.id())
      }
    })

    if (coAuthors.length > 0) {
      groups.push({
        paperId,
        paperTitle: paperNode.data('label'),
        coAuthors
      })
    }
  })

  coAuthorGroups.value = groups
  coAuthorCount.value = coAuthorIds.size

  // 高亮合作子网络：该作者 + 关联论文 + 合著者
  highlightCollaboration(authorNodeId, paperIds, coAuthorIds)
}

/**
 * 高亮合作子网络：
 * - 相关节点（作者 + 论文 + 合著者）：正常显示 + 加亮边框
 * - 其他节点：透明度降低（淡化）
 */
function highlightCollaboration(authorId: string, paperIds: Set<string>, coAuthorIds: Set<string>) {
  if (!cy) return
  const relatedIds = new Set([authorId, ...paperIds, ...coAuthorIds])

  // 先重置所有节点样式
  cy.elements().removeClass('dimmed highlighted')

  // 淡化不相关的节点
  cy.nodes().forEach(n => {
    if (!relatedIds.has(n.id())) {
      n.addClass('dimmed')
    }
  })

  // 高亮相关节点
  cy.nodes().forEach(n => {
    if (relatedIds.has(n.id())) {
      n.addClass('highlighted')
    }
  })
}

/** 清除所有高亮效果 */
function clearHighlight() {
  if (!cy) return
  cy.elements().removeClass('dimmed highlighted')
}

/** 点击邻居标签：聚焦到该节点并更新选中状态 */
function focusNode(nodeId: string) {
  if (!cy) return
  const node = cy.getElementById(nodeId)
  if (node && node.length) {
    cy.animate({ center: { eles: node }, zoom: 1.5 }, { duration: 400 })
    selectedNode.value = node
    if (node.data('type') === 'author') {
      findCoAuthors(node.id())
    } else {
      clearHighlight()
      coAuthorGroups.value = []
      coAuthorCount.value = 0
    }
  }
}

/** 清除选中状态并重置高亮（抽屉关闭时调用） */
function clearSelection() {
  selectedNode.value = null
  coAuthorGroups.value = []
  coAuthorCount.value = 0
  clearHighlight()
}

/** 抽屉可见性：选中节点即打开，关闭即清除选中 */
const drawerVisible = computed({
  get: () => selectedNode.value !== null,
  set: (v: boolean) => {
    if (!v) clearSelection()
  }
})

// ====================================================================
// 抽屉内容的邻居查询（读当前画布，选中节点变化时随渲染重新计算）
// ====================================================================

interface NeighborNode { id: string; name: string }

interface NeighborGroup { label: string; nodes: NeighborNode[] }

/** 取边另一端节点并去重收集 */
function collectNeighbors(nodeId: string, edgeSelector: string, keepType?: string): NeighborNode[] {
  if (!cy) return []
  const node = cy.getElementById(nodeId)
  const map = new Map<string, string>()
  node.connectedEdges(edgeSelector).forEach(e => {
    const otherId = e.data('source') === nodeId ? e.data('target') : e.data('source')
    const other = cy!.getElementById(otherId as string)
    if (other && other.id() !== nodeId && (!keepType || other.data('type') === keepType)) {
      map.set(other.id(), other.data('label') as string)
    }
  })
  return [...map.entries()].map(([id, name]) => ({ id, name }))
}

/** 论文节点邻居：作者 / 关键词 / 引用论文 / 抽取实体四组 */
function paperNeighborGroups(paperId: string): NeighborGroup[] {
  const groups: NeighborGroup[] = []
  const authors = collectNeighbors(paperId, 'edge[type="AUTHORED"]', 'author')
  const keywords = collectNeighbors(paperId, 'edge[type="HAS_KEYWORD"]', 'keyword')
  const cited = collectNeighbors(paperId, 'edge[type="CITES"]', 'paper')
  // EXTRACTED_FROM 方向是 实体→论文，所以另一端就是抽取出来的实体
  const extracted = collectNeighbors(paperId, 'edge[type="EXTRACTED_FROM"]')
  if (authors.length) groups.push({ label: '作者', nodes: authors })
  if (keywords.length) groups.push({ label: '关键词', nodes: keywords })
  if (cited.length) groups.push({ label: '引用论文', nodes: cited })
  if (extracted.length) groups.push({ label: '抽取实体', nodes: extracted })
  return groups
}

/** 作者节点：所属机构（AFFILIATED 边的另一端） */
function authorInstitutions(authorId: string): NeighborNode[] {
  return collectNeighbors(authorId, 'edge[type="AFFILIATED"]', 'institution')
}

/** 研究实体节点：来源论文（EXTRACTED_FROM 边指向的论文） */
function researchSourcePapers(researchId: string): NeighborNode[] {
  return collectNeighbors(researchId, 'edge[type="EXTRACTED_FROM"]', 'paper')
}

// ====================================================================
// 保存查询（localStorage，无第三方库：手写结构校验）
// ====================================================================

interface SavedQuery { keyword: string; label: string }

const SAVED_QUERIES_KEY = 'ag.graph.savedQueries'
const savedQueries = ref<SavedQuery[]>([])

/** 读取已存查询：损坏/非法数据直接丢弃（localStorage 是用户可改的，不能信任） */
function loadSavedQueries() {
  try {
    const raw = localStorage.getItem(SAVED_QUERIES_KEY)
    if (!raw) return
    const parsed: unknown = JSON.parse(raw)
    if (
      Array.isArray(parsed) &&
      parsed.every(
        (q) =>
          q !== null && typeof q === 'object' &&
          typeof (q as SavedQuery).keyword === 'string' && (q as SavedQuery).keyword.trim() !== '' &&
          typeof (q as SavedQuery).label === 'string' && (q as SavedQuery).label.trim() !== ''
      )
    ) {
      savedQueries.value = parsed.map((q) => ({ keyword: q.keyword, label: q.label }))
    }
  } catch {
    savedQueries.value = []
  }
}

/** 把当前关键词存进列表（按关键词去重） */
function saveCurrentQuery() {
  const kw = keyword.value.trim()
  if (!kw) {
    ElMessage.warning('请输入关键词后再保存')
    return
  }
  if (!savedQueries.value.some((q) => q.keyword === kw)) {
    savedQueries.value.push({ keyword: kw, label: kw.length > 20 ? kw.slice(0, 20) + '…' : kw })
  }
  localStorage.setItem(SAVED_QUERIES_KEY, JSON.stringify(savedQueries.value))
  ElMessage.success('查询已保存')
}

function removeSavedQuery(queryKeyword: string) {
  savedQueries.value = savedQueries.value.filter((q) => q.keyword !== queryKeyword)
  localStorage.setItem(SAVED_QUERIES_KEY, JSON.stringify(savedQueries.value))
}

/** 点击已存查询：回填关键词并重建图谱 */
function applySavedQuery(q: SavedQuery) {
  keyword.value = q.keyword
  void rebuildGraph()
}

// ====================================================================
// 关键词防抖
// ====================================================================
/** 输入停顿 200ms 后才发起搜索；连续输入时旧定时器不断被替换，只发最后一次 */
function onKeywordInput() {
  if (keywordTimer !== null) {
    window.clearTimeout(keywordTimer)
  }
  keywordTimer = window.setTimeout(() => void rebuildGraph(), 200)
}

// ====================================================================
// Cytoscape 初始化
// ====================================================================
onMounted(() => {
  if (!cyContainer.value) return
  cy = cytoscape({
    container: cyContainer.value,
    style: [
      // 节点基础样式：圆形 + 标签在下方
      {
        selector: 'node',
        style: {
          label: 'data(label)',
          'text-valign': 'bottom',
          'text-halign': 'center',
          'font-size': '10px',
          color: '#edf6ff',
          'text-max-width': '80px',
          'text-wrap': 'ellipsis',
          'background-color': '#2d9df5',
          width: 28,
          height: 28
        }
      },
      // 按类型着色：paper=蓝 author=绿 keyword=橙 institution=紫 research=亮蓝
      { selector: 'node[type="paper"]', style: { 'background-color': '#2d9df5' } },
      { selector: 'node[type="author"]', style: { 'background-color': '#35c98c' } },
      { selector: 'node[type="keyword"]', style: { 'background-color': '#f5a04b' } },
      { selector: 'node[type="institution"]', style: { 'background-color': '#a77af2' } },
      { selector: 'node[type="research"]', style: { 'background-color': '#57bcff' } },
      // 边样式：灰色细线 + 箭头
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
      // 合作关系派生边（作者↔作者）：与作者节点同色系的绿线，无箭头（合作是无向关系），
      // 线宽取自 data.widthPx（由合著篇数换算），边上标出篇数并沿线方向旋转
      {
        selector: 'edge[type="COAUTHOR_WITH"]',
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
          // 深色文字底衬：否则绿色数字压在深色画布和交叉线上很难认
          'text-background-color': '#0d1b2a',
          'text-background-opacity': 0.75,
          'text-background-padding': '1px'
        }
      },
      // LLM 关系边（六类关系）：粉红虚线 + 关系类型标签
      {
        selector: 'edge[type="LLM_RELATION"]',
        style: {
          'line-color': '#e07bb0',
          'target-arrow-color': '#e07bb0',
          'target-arrow-shape': 'triangle',
          'line-style': 'dashed',
          width: 1.5,
          'curve-style': 'unbundled-bezier',
          label: 'data(label)',
          'font-size': '8px',
          color: '#f0b8d2',
          'text-rotation': 'autorotate',
          'text-background-color': '#0d1b2a',
          'text-background-opacity': 0.75,
          'text-background-padding': '1px'
        }
      },
      // EXTRACTED_FROM 证据边（抽取实体→来源论文）：亮蓝虚线
      {
        selector: 'edge[type="EXTRACTED_FROM"]',
        style: {
          'line-color': '#57bcff',
          'target-arrow-color': '#57bcff',
          'target-arrow-shape': 'triangle',
          'line-style': 'dashed',
          width: 1.2,
          'curve-style': 'unbundled-bezier',
          opacity: 0.8
        }
      },
      // 选中态高亮
      {
        selector: 'node:selected',
        style: {
          'border-width': 3,
          'border-color': '#57bcff'
        }
      },
      // 合作子网络高亮：相关节点加亮边框
      {
        selector: 'node.highlighted',
        style: {
          'border-width': 3,
          'border-color': '#57bcff',
          'opacity': 1
        }
      },
      // 淡化非相关节点
      {
        selector: 'node.dimmed',
        style: {
          'opacity': 0.15
        }
      }
    ],
    layout: { name: 'cose', animate: false },
    wheelSensitivity: 0.3
  })

  // 点击节点弹出详情抽屉；作者节点额外触发合著者遍历
  cy.on('tap', 'node', (evt) => {
    const node = evt.target as cytoscape.NodeSingular
    selectedNode.value = node
    if (node.data('type') === 'author') {
      findCoAuthors(node.id())
    } else {
      // 非作者节点：清除合著者数据和高亮
      coAuthorGroups.value = []
      coAuthorCount.value = 0
      clearHighlight()
    }
  })
  // 点击空白处关闭详情并清除高亮
  cy.on('tap', (evt) => {
    if (evt.target === cy) {
      clearSelection()
    }
  })

  // 双击节点：进入两跳聚焦模式（先触发两次 tap 选中节点，再触发 dbltap 聚焦，体验上连贯）
  cy.on('dbltap', 'node', (evt) => {
    enterFocus((evt.target as cytoscape.NodeSingular).id())
  })
  // 双击空白处退出聚焦
  cy.on('dbltap', (evt) => {
    if (evt.target === cy) {
      exitFocus()
    }
  })

  loadSavedQueries()
  rebuildGraph()

  // 开发模式下暴露实例，便于控制台调试与自动化测试
  if (import.meta.env.DEV) {
    (window as any).__cy = cy
  }
})

onBeforeUnmount(() => {
  // 组件卸载：停掉防抖定时器、取消在途请求、销毁画布
  if (keywordTimer !== null) {
    window.clearTimeout(keywordTimer)
    keywordTimer = null
  }
  currentAbort?.abort()
  cy?.destroy()
  cy = null
})
</script>

<style scoped>
.graph-view {
  padding: var(--space-4);
  height: 100%;
  display: flex;
  flex-direction: column;
}

.toolbar {
  display: flex;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  align-items: center;
  flex-wrap: wrap;
}

/* Cytoscape 容器：占据剩余空间 */
.cy-container {
  flex: 1;
  min-height: 400px;
  background: var(--paper-deep);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
}

/* 空状态提示 */
.empty-hint {
  text-align: center;
  color: var(--muted);
  padding: var(--space-6);
}

/* 已存查询下拉里的行：查询名 + 删除按钮 */
.query-item {
  display: inline-block;
  min-width: 160px;
  margin-right: var(--space-2);
}

/* 节点详情抽屉 */
.drawer-body {
  padding: 0 var(--space-1);
}

.detail-header {
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

.drawer-title {
  color: var(--ink);
  font-size: 16px;
  margin: 0 0 var(--space-3) 0;
  word-break: break-all;
}

.drawer-body p {
  margin: var(--space-1) 0;
  font-size: 13px;
}

/* 邻居分组（论文的作者/关键词/引用/抽取实体、作者的机构、研究实体的来源论文） */
.neighbor-section {
  margin-top: var(--space-4);
  padding-top: var(--space-3);
  border-top: 1px solid var(--border);
}

.neighbor-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--accent-bright);
  margin-bottom: var(--space-2) !important;
}

.neighbor-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
}

.neighbor-tag {
  cursor: pointer;
}

/* 合著者区域 */
.coauthor-section {
  margin-top: var(--space-4);
  padding-top: var(--space-3);
  border-top: 1px solid var(--border);
}

.coauthor-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--accent-bright);
  margin-bottom: var(--space-2) !important;
}

.coauthor-group {
  margin-bottom: var(--space-3);
}

.coauthor-paper {
  font-size: 12px;
  color: var(--muted);
  margin-bottom: var(--space-1) !important;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 300px;
}

.coauthor-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
}

.coauthor-tag {
  cursor: pointer;
}

.coauthor-empty {
  margin-top: var(--space-3);
  color: var(--muted);
  font-size: 12px;
}
</style>
