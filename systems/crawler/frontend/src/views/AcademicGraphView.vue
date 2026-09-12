<script setup lang="ts">
import { ElAlert, ElButton, ElDrawer, ElInput, ElOption, ElSelect } from 'element-plus'
import { ArrowRight, Building2, CalendarDays, Maximize, Network, RefreshCw, UserRound } from 'lucide-vue-next'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import GraphCanvas from '@/components/business/GraphCanvas.vue'
import AcademicNodeDetail from '@/components/business/AcademicNodeDetail.vue'
import EntitySuggestInput from '@/components/business/EntitySuggestInput.vue'
import { useAuthorGraph } from '@/composables/useAuthorGraph'
import type { AcademicGraphMode, AuthorGraphQuery, WorkCategory } from '@/services/academic-graph'
import { catalogApi } from '@/services/business'
import { toErrorMessage } from '@/services/api'
import { useSessionStore } from '@/stores/session'
import type { CatalogEntity, GraphNode } from '@/types/api'
import { academicElements, cooperationPositions, timelineGroups, workCategories, workDate, workDefinition, workInstitutionNames } from '@/utils/academic-graph'
import { cooperationEvidence, type CooperationEvidence } from '@/utils/graph-cooperation'

const props = defineProps<{ mode: AcademicGraphMode }>()
const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const descriptions = {
  relations: { title: '学术关系图谱', text: '从共同创作的作品，了解一位作者的学术合作。' },
  achievements: { title: '学术成果图谱', text: '查看本人论文、专利，以及指导的硕士和博士学位论文。' },
  background: { title: '学术背景图谱', text: '按发表时间浏览成果，查看每项成果对应的机构。' },
}
const heading = computed(() => descriptions[props.mode])
const authorId = computed(() => typeof route.query.authorId === 'string' ? route.query.authorId : '')
const manualAuthor = ref('')
const category = computed(() => typeof route.query.category === 'string' ? route.query.category : '')
const categories = computed(() => props.mode === 'relations' ? workCategories.filter(item => ['PAPER', 'PATENT'].includes(item.value)) : workCategories)
const page = computed(() => Number(route.query.page ?? 0))
const size = computed(() => Number(route.query.size ?? 20))
const inputError = computed(() => {
  if (route.query.authorId !== undefined && (!/^[1-9]\d*$/.test(authorId.value) || !Number.isSafeInteger(Number(authorId.value)))) return '作者编号无效，请重新选择作者。'
  if (route.query.category !== undefined && !workCategories.some(item => item.value === category.value)) return '成果类型无效，请重新选择类型。'
  if ((route.query.page !== undefined && (typeof route.query.page !== 'string' || !/^\d+$/.test(route.query.page)))
    || !Number.isInteger(page.value) || page.value < 0 || page.value > 1000000
    || (route.query.size !== undefined && (typeof route.query.size !== 'string' || !/^\d+$/.test(route.query.size)))
    || !Number.isInteger(size.value) || size.value < 1 || size.value > 50) return '分页参数无效，请重新选择作者。'
  return ''
})
const query = computed<AuthorGraphQuery | null>(() => !authorId.value || inputError.value ? null : {
  authorId: Number(authorId.value), category: (category.value || undefined) as WorkCategory | undefined,
  collaborationsOnly: props.mode === 'relations', chronological: props.mode === 'background', page: page.value, size: size.value,
})
const { result, loading, error, load } = useAuthorGraph(() => query.value)
const graph = computed(() => result.value?.graph)
const root = computed(() => graph.value?.nodes.find(node => node.id === graph.value?.rootNodeId))
const works = computed(() => graph.value?.nodes.filter(node => node.type === 'ACHIEVEMENT') ?? [])
const cooperations = computed(() => graph.value ? cooperationEvidence(graph.value) : [])
const elements = computed(() => graph.value ? academicElements(graph.value) : [])
const positions = computed(() => props.mode === 'relations' && graph.value ? cooperationPositions(graph.value) : undefined)
const timeline = computed(() => timelineGroups(works.value))
const totalPages = computed(() => Math.max(1, Math.ceil((result.value?.totalWorks ?? 0) / size.value)))
const canvas = ref<InstanceType<typeof GraphCanvas> | null>(null)
const listMode = ref<'works' | 'authors'>('works')
const manifestOpen = ref(false)
const detailOpen = ref(false)
const selectedNode = ref<GraphNode | null>(null)
const selectedCooperation = ref<CooperationEvidence | null>(null)
const suggestions = ref<CatalogEntity[]>([])
const suggestionsLoading = ref(false)
const suggestionsError = ref('')
let suggestionSequence = 0

async function loadAuthors(): Promise<void> {
  if (!session.hasPermission('CATALOG_READ')) return
  const sequence = ++suggestionSequence
  suggestionsLoading.value = true
  suggestionsError.value = ''
  try {
    const data = await catalogApi.entities('authors', '', 0, 8)
    if (sequence === suggestionSequence) suggestions.value = data.items
  } catch (cause) { if (sequence === suggestionSequence) suggestionsError.value = toErrorMessage(cause) }
  finally { if (sequence === suggestionSequence) suggestionsLoading.value = false }
}

function chooseAuthor(id: string): void {
  detailOpen.value = false
  void router.push({ path: route.path, query: { authorId: id } })
}
function changeCategory(value: string): void {
  void router.push({ path: route.path, query: { ...route.query, category: value || undefined, page: undefined } })
}
function changePage(value: number): void {
  void router.push({ path: route.path, query: { ...route.query, page: value ? String(value) : undefined } })
}
function selectNode(id: string): void {
  selectedNode.value = graph.value?.nodes.find(node => node.id === id) ?? null
  detailOpen.value = Boolean(selectedNode.value)
}
function selectCooperation(item: CooperationEvidence): void {
  selectedNode.value = null
  selectedCooperation.value = item
  detailOpen.value = true
}
function selectEdge(id: string): void {
  const cooperation = cooperations.value.find(item => item.edge.id === id)
  if (cooperation) selectCooperation(cooperation)
  else {
    const edge = graph.value?.edges.find(item => item.id === id)
    if (edge) selectNode(edge.target)
  }
}
function partner(item: CooperationEvidence): GraphNode {
  return item.source.id === root.value?.id ? item.target : item.source
}
watch(query, () => { detailOpen.value = false; selectedNode.value = null; selectedCooperation.value = null })
watch(() => props.mode, () => { listMode.value = 'works' })
watch(authorId, value => { if (!value) void loadAuthors() }, { immediate: true })
onBeforeUnmount(() => { suggestionSequence++ })
</script>

<template>
  <section class="academic-graph" :aria-label="heading.title">
    <header class="academic-toolbar">
      <div class="academic-heading"><h1>{{ heading.title }}</h1><p>{{ heading.text }}</p></div>
      <label class="author-picker"><span class="sr-only">研究作者</span>
        <EntitySuggestInput v-if="session.hasPermission('CATALOG_READ')" :key="authorId" collection="authors" label="研究作者" mode="id" :model-value="authorId" :placeholder="root ? `当前：${root.label}，输入姓名切换` : '输入作者姓名，选择一位作者'" @select="chooseAuthor(String($event.id))" />
        <span v-else class="manual-author"><ElInput v-model="manualAuthor" aria-label="作者编号" placeholder="输入作者编号" @keydown.enter="chooseAuthor(manualAuthor)" /><ElButton @click="chooseAuthor(manualAuthor)">查看</ElButton></span>
      </label>
      <label class="category-picker"><span class="sr-only">成果类型</span><ElSelect :model-value="category" aria-label="成果类型" placeholder="全部类型" @update:model-value="changeCategory"><ElOption label="全部类型" value="" /><ElOption v-for="item in categories" :key="item.value" :label="item.label" :value="item.value" /></ElSelect></label>
      <ElButton :loading="loading" :disabled="!query" @click="load"><RefreshCw v-if="!loading" :size="15" class="mr-1.5" />刷新</ElButton>
      <ElButton v-if="root && mode !== 'background'" :aria-pressed="manifestOpen" @click="manifestOpen = !manifestOpen">内容清单</ElButton>
      <details class="academic-tools"><summary>图谱工具</summary><nav aria-label="图谱辅助工具">
        <RouterLink to="/graph/overview">全局图谱</RouterLink><RouterLink to="/graph/explore">高级查询</RouterLink>
        <RouterLink to="/graph/path">路径分析</RouterLink><RouterLink to="/graph/queries">保存的查询</RouterLink>
        <RouterLink to="/graph/settings/nodes">节点样式</RouterLink><RouterLink to="/graph/settings/edges">关系样式</RouterLink>
      </nav></details>
    </header>
    <ElAlert v-if="inputError || error" :title="inputError || error" type="error" :closable="false" show-icon />
    <ElAlert v-if="graph?.truncated" :title="graph.narrowingSuggestion || '当前页部分合作证据未展示，请减少每页成果数量。'" type="warning" :closable="false" show-icon />

    <div v-if="!authorId && !inputError" class="author-start">
      <Network :size="36" aria-hidden="true" /><h2>从一位作者开始</h2><p>选择作者，查看合作网络、学术成果与时间线。</p>
      <p v-if="suggestionsLoading" role="status">正在读取作者…</p>
      <template v-if="suggestionsError"><ElAlert :title="suggestionsError" type="error" :closable="false" /><ElButton @click="loadAuthors">重新读取作者</ElButton></template>
      <ul v-if="suggestions.length" class="author-suggestions" aria-label="可选作者"><li v-for="author in suggestions" :key="author.id"><button @click="chooseAuthor(String(author.id))"><UserRound :size="19" aria-hidden="true" /><span><strong>{{ author.displayName }}</strong><small>#{{ author.id }} · {{ author.achievementCount }} 项收录成果</small></span><ArrowRight :size="15" aria-hidden="true" /></button></li></ul>
      <p v-else-if="!suggestionsLoading && !suggestionsError && session.hasPermission('CATALOG_READ')">暂无可选作者，请先导入作者资料。</p>
    </div>
    <div v-else-if="loading" class="academic-loading" role="status">正在读取作者图谱…</div>

    <template v-if="root && result">
      <div v-if="mode !== 'background'" class="academic-workspace" :class="{ 'has-manifest': manifestOpen }">
        <section class="academic-canvas-panel" aria-label="学术图谱画布">
          <div class="canvas-heading"><button class="canvas-author" :aria-label="`${root.label} 的作者详情`" @click="selectedCooperation = null; selectNode(root.id)"><UserRound :size="15" />{{ root.label }} <small>作者 #{{ root.businessId }} · {{ result.totalWorks }} 项{{ mode === 'relations' ? '共同作品' : '成果' }}</small></button><ElButton text size="small" @click="canvas?.fit()"><Maximize :size="14" class="mr-1" />适应画布</ElButton></div>
          <div class="academic-canvas-stage"><GraphCanvas ref="canvas" :elements="elements" :root-node-id="root.id" :positions="positions" :layout="mode === 'relations' ? 'network' : 'concentric'" :scope-key="JSON.stringify(query)" fill :label="`${root.label}的${heading.title}，${graph?.nodes.length}个节点`" :selected-node-id="selectedNode?.id" :selected-edge-id="selectedCooperation?.edge.id" @select-node="selectedCooperation = null; selectNode($event)" @select-edge="selectEdge" @clear-selection="selectedNode = null; selectedCooperation = null" />
            <p v-if="!works.length" class="canvas-empty">{{ mode === 'relations' ? '当前范围内暂无共同创作作品' : '当前范围内暂无成果' }}</p>
          </div>
          <ul class="academic-legend" aria-label="实体类型图例"><li><i style="background: #1677ef" />作者</li><li v-for="item in workCategories" :key="item.value"><i :style="{ background: item.color }" />{{ item.label }}</li></ul>
        </section>
        <aside v-if="manifestOpen" class="academic-manifest" aria-label="图谱内容清单">
          <div class="manifest-tabs"><button :aria-pressed="listMode === 'works'" @click="listMode = 'works'">{{ mode === 'relations' ? '共同作品' : '成果清单' }} <span>{{ works.length }}</span></button><button v-if="mode === 'relations'" :aria-pressed="listMode === 'authors'" @click="listMode = 'authors'">合作作者 <span>{{ cooperations.length }}</span></button></div>
          <ul v-if="listMode === 'works'" class="work-list" aria-label="当前页作品"><li v-for="work in works" :key="work.id"><button @click="selectedCooperation = null; selectNode(work.id)"><span class="work-meta"><span :style="{ color: workDefinition(work)?.color }">{{ workDefinition(work)?.label }}</span><time>{{ workDate(work) || '日期未知' }}</time></span><strong>{{ work.label }}</strong><span class="work-action">查看详情 <ArrowRight :size="12" /></span></button></li></ul>
          <ul v-else class="partner-list" aria-label="当前页合作作者"><li v-for="item in cooperations" :key="item.edge.id"><button class="partner-name" @click="selectedCooperation = null; selectNode(partner(item).id)"><UserRound :size="17" />{{ partner(item).label }}</button><button class="partner-evidence" :aria-label="`与${partner(item).label}共同创作的作品`" @click="selectCooperation(item)">共同创作 · {{ item.works.length }} 项 <ArrowRight :size="13" /></button></li></ul>
          <p v-if="!works.length" class="manifest-empty">{{ result.totalWorks ? '这一页没有成果，请返回第一页。' : '可尝试其他作者或成果类型。' }}</p>
        </aside>
      </div>

      <section v-else class="academic-timeline" aria-label="学术成果时间线">
        <div class="timeline-heading"><button class="canvas-author" :aria-label="`${root.label} 的作者详情`" @click="selectedCooperation = null; selectNode(root.id)"><UserRound :size="15" />{{ root.label }} <small>作者 #{{ root.businessId }} · {{ result.totalWorks }} 项成果</small></button><span class="order-note"><CalendarDays :size="14" />按日期由早到晚 · 日期未知置后</span></div>
        <div v-for="group in timeline" :key="group.year" class="timeline-year"><h2>{{ group.year }}</h2><ol>
          <li v-for="work in group.works" :key="work.id" :style="{ '--work-color': workDefinition(work)?.color }"><button @click="selectedCooperation = null; selectNode(work.id)">
            <span class="timeline-date"><span>发布时间</span><time v-if="workDate(work)" :datetime="workDate(work)">{{ workDate(work) }}</time><span v-else>未收录</span></span>
            <span class="timeline-content"><span class="timeline-type">{{ workDefinition(work)?.label }}</span><strong>{{ work.label }}</strong>
              <span class="timeline-institutions"><Building2 :size="14" aria-hidden="true" /><span>机构：{{ workInstitutionNames(work) }}</span></span>
              <span v-if="work.properties.institutionsTruncated" class="timeline-institution-note">仅显示该成果的前 100 家机构。</span>
            </span><ArrowRight :size="17" aria-hidden="true" />
          </button></li>
        </ol></div>
        <p v-if="!works.length" class="manifest-empty">{{ result.totalWorks ? '这一页没有成果，请返回第一页。' : '当前范围内暂无成果，可选择其他作者或类型。' }}</p>
      </section>

      <footer class="academic-footer"><p>{{ mode === 'relations' ? '合作依据仅覆盖当前页共同署名作品；指导关系不等同于共同署名。' : '论文与专利依据本人署名；硕论与博论依据已确认的导师关系。' }}</p><div class="academic-pagination">
        <ElSelect :model-value="size" aria-label="每页成果数量" @update:model-value="value => router.push({ path: route.path, query: { ...route.query, size: String(value), page: undefined } })"><ElOption v-for="count in [10, 20, 50]" :key="count" :value="count" :label="`${count} 项/页`" /></ElSelect>
        <ElButton :disabled="page <= 0" @click="changePage(Math.max(0, page - 1))">上一页</ElButton><span>{{ page + 1 }} / {{ totalPages }} 页</span><ElButton :disabled="page + 1 >= totalPages" @click="changePage(page + 1)">下一页</ElButton><ElButton v-if="page >= totalPages" link @click="changePage(0)">返回第一页</ElButton>
      </div></footer>
    </template>

    <ElDrawer v-model="detailOpen" :title="selectedNode ? selectedNode.type === 'AUTHOR' ? '作者详情' : '作品详情' : '共同创作的作品'" size="min(620px, 100vw)" destroy-on-close>
      <ElButton v-if="selectedNode && selectedCooperation" class="mb-4" link @click="selectedNode = null">返回共同作品</ElButton>
      <AcademicNodeDetail v-if="selectedNode" :key="selectedNode.id" :node="selectedNode" @choose-author="chooseAuthor" />
      <section v-else-if="selectedCooperation" aria-label="共同作品详情"><h3 class="mb-3 text-lg font-semibold">{{ selectedCooperation.source.label }} × {{ selectedCooperation.target.label }}</h3><p class="mb-4 text-sm text-muted-foreground">以下作品为当前页共同署名的依据，点击查看完整作品资料。</p><ul class="work-list"><li v-for="work in selectedCooperation.works" :key="work.id"><button @click="selectNode(work.id)"><span class="work-meta">{{ workDefinition(work)?.label }} · {{ workDate(work) || '日期未知' }}</span><strong>{{ work.label }}</strong><span class="work-action">查看作品详情 <ArrowRight :size="12" /></span></button></li></ul><p v-if="selectedCooperation.incomplete" class="text-sm text-muted-foreground">部分共同署名依据未返回。</p></section>
    </ElDrawer>
  </section>
</template>

<style scoped>
.academic-graph { height: 100%; min-height: 0; overflow: hidden; padding: 10px 20px 6px; display: flex; flex-direction: column; gap: 8px; color: hsl(var(--foreground)); }
.academic-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; flex-shrink: 0; }
.academic-heading { margin-right: auto; }
.academic-toolbar h1 { font-size: 24px; line-height: 1.35; font-weight: 650; white-space: nowrap; }
.academic-heading p { margin-top: 4px; font-size: 13px; line-height: 1.6; color: hsl(var(--muted-foreground)); }
.academic-toolbar .el-button + .el-button { margin-left: 0; }
.academic-tools { position: relative; font-size: 13px; flex-shrink: 0; }
.academic-tools summary { cursor: pointer; padding: 8px 0; color: hsl(var(--muted-foreground)); }
.academic-tools nav { position: absolute; right: 0; top: 100%; z-index: 40; display: grid; min-width: 140px; padding: 6px; border: 1px solid hsl(var(--border)); border-radius: 8px; background: hsl(var(--popover)); box-shadow: var(--shadow-md); }
.academic-tools a { padding: 9px 12px; border-radius: 3px; }
.academic-tools a:hover { background: hsl(var(--muted)); }
.author-picker { width: clamp(200px, 25vw, 340px); min-width: 0; }
.category-picker { width: 148px; }
.manual-author { display: flex; gap: 6px; }
.author-start { max-width: 780px; width: 100%; align-self: center; padding: 32px 24px; margin-top: 16px; text-align: center; background: hsl(var(--card)); border: 1px solid hsl(var(--border)); border-radius: 10px; overflow: auto; }
.author-start > svg { margin: 0 auto 14px; color: hsl(var(--primary)); }
.author-start h2 { font-size: 21px; margin-bottom: 10px; }
.author-start > p { color: hsl(var(--muted-foreground)); font-size: 13px; }
.author-suggestions { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 28px; }
.author-suggestions button { width: 100%; display: flex; align-items: center; gap: 12px; padding: 18px; text-align: left; background: hsl(var(--muted) / .55); border: 1px solid hsl(var(--border)); border-radius: 8px; }
.author-suggestions button > svg:first-child { color: hsl(var(--primary)); flex-shrink: 0; }
.author-suggestions button > span { flex: 1; min-width: 0; }
.author-suggestions small { display: block; color: hsl(var(--muted-foreground)); font-size: 12px; margin-top: 4px; }
.academic-loading { padding: 12px 0; text-align: center; color: hsl(var(--muted-foreground)); }
.canvas-author { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; font-weight: 550; text-align: left; }
.canvas-author > svg { color: hsl(var(--primary)); }
.canvas-author small { font-size: 12px; color: hsl(var(--muted-foreground)); font-weight: 400; }
.order-note { display: flex; gap: 5px; align-items: center; font-size: 12px; color: hsl(var(--muted-foreground)); }
.academic-workspace { display: grid; grid-template-columns: minmax(0, 1fr); gap: 10px; flex: 1; min-height: 0; }
.academic-workspace.has-manifest { grid-template-columns: minmax(0, 1fr) 310px; }
.academic-canvas-panel { border: 1px solid hsl(var(--border)); border-radius: 10px; overflow: hidden; display: flex; flex-direction: column; background: hsl(var(--card)); min-width: 0; }
.canvas-heading { display: flex; justify-content: space-between; align-items: center; padding: 4px 16px; border-bottom: 1px solid hsl(var(--border)); font-size: 14px; gap: 12px; }
.academic-canvas-stage { position: relative; flex: 1; min-height: 0; }
.canvas-empty { position: absolute; top: 20px; left: 0; right: 0; font-size: 12px; text-align: center; pointer-events: none; color: hsl(var(--muted-foreground)); }
.academic-legend { display: flex; flex-wrap: wrap; gap: 16px; padding: 6px 16px; border-top: 1px solid hsl(var(--border)); font-size: 12px; color: hsl(var(--muted-foreground)); }
.academic-legend li { display: flex; gap: 6px; align-items: center; }
.academic-legend i { width: 9px; height: 9px; border-radius: 50%; }
.academic-manifest { border: 1px solid hsl(var(--border)); background: hsl(var(--card)); border-radius: 10px; min-height: 0; overflow: auto; }
.manifest-tabs { display: flex; position: sticky; top: 0; background: hsl(var(--card)); border-bottom: 1px solid hsl(var(--border)); padding: 0 12px; z-index: 1; }
.manifest-tabs button { padding: 16px 7px 12px; font-size: 13px; border-bottom: 2px solid transparent; }
.manifest-tabs button[aria-pressed=true] { color: hsl(var(--primary)); border-bottom-color: hsl(var(--primary)); }
.manifest-tabs span { margin-left: 4px; font-size: 12px; color: hsl(var(--muted-foreground)); }
.work-list > li + li { border-top: 1px solid hsl(var(--border) / .7); }
.work-list button { display: block; width: 100%; padding: 15px 16px; text-align: left; }
.work-list button:hover, .author-suggestions button:hover { background: hsl(var(--primary) / .06); }
.work-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; font-size: 12px; color: hsl(var(--muted-foreground)); }
.work-list strong { display: block; margin: 7px 0; font-weight: 500; font-size: 13px; line-height: 1.65; overflow-wrap: anywhere; }
.work-action { display: flex; gap: 5px; align-items: center; color: hsl(var(--primary)); font-size: 12px; }
.partner-list li { padding: 17px; border-bottom: 1px solid hsl(var(--border)); }
.partner-name { display: flex; align-items: center; gap: 9px; font-size: 14px; }
.partner-evidence { margin-top: 12px; display: flex; align-items: center; gap: 6px; color: hsl(var(--primary)); font-size: 12px; }
.manifest-empty { padding: 28px 18px; font-size: 13px; color: hsl(var(--muted-foreground)); }
.academic-footer { display: flex; justify-content: space-between; gap: 20px; align-items: center; padding: 3px 0; }
.academic-footer > p { font-size: 12px; line-height: 1.6; color: hsl(var(--muted-foreground)); max-width: 560px; }
.academic-pagination { display: flex; gap: 10px; align-items: center; white-space: nowrap; font-size: 12px; }
.academic-pagination .el-select { width: 102px; }
.academic-pagination .el-button + .el-button { margin-left: 0; }
.academic-timeline { width: 100%; flex: 1; min-height: 0; overflow: auto; padding: 18px 22px; background: hsl(var(--card)); border: 1px solid hsl(var(--border)); border-radius: 10px; }
.timeline-heading { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px; margin-bottom: 22px; padding-bottom: 14px; border-bottom: 1px solid hsl(var(--border)); }
.timeline-year { display: grid; grid-template-columns: 100px minmax(0, 1fr); }
.timeline-year > h2 { font-size: 22px; letter-spacing: -.03em; font-variant-numeric: tabular-nums; padding-top: 14px; font-weight: 600; color: hsl(var(--primary)); }
.timeline-year ol { border-left: 1px solid hsl(var(--border)); padding-left: 27px; }
.timeline-year li { padding-bottom: 17px; position: relative; }
.timeline-year li::before { content: ''; position: absolute; top: 25px; left: -32px; width: 9px; height: 9px; border-radius: 50%; background: var(--work-color); box-shadow: 0 0 0 4px hsl(var(--background)); }
.timeline-year button { display: flex; gap: 18px; text-align: left; align-items: center; width: 100%; padding: 18px 22px; border: 1px solid hsl(var(--border)); border-radius: 8px; background: hsl(var(--muted) / .45); }
.timeline-year button:hover { border-color: var(--work-color); }
.timeline-date { display: flex; flex-direction: column; gap: 6px; color: hsl(var(--muted-foreground)); font-size: 12px; width: 86px; flex-shrink: 0; font-variant-numeric: tabular-nums; }
.timeline-content { display: flex; flex-direction: column; gap: 7px; flex: 1; min-width: 0; }
.timeline-type { color: var(--work-color); font-size: 12px; }
.timeline-content strong { font-size: 14px; font-weight: 500; line-height: 1.65; overflow-wrap: anywhere; }
.timeline-institutions { display: flex; align-items: flex-start; gap: 5px; color: hsl(var(--muted-foreground)); font-size: 12px; line-height: 1.6; overflow-wrap: anywhere; }
.timeline-institutions > svg { flex-shrink: 0; margin-top: 2px; }
.timeline-institution-note { color: hsl(var(--muted-foreground)); font-size: 12px; }
.timeline-year button > svg { flex-shrink: 0; color: hsl(var(--muted-foreground)); }
button:focus-visible, summary:focus-visible, a:focus-visible { outline: 2px solid hsl(var(--primary)); outline-offset: 3px; }
@media (max-width: 1050px) { .academic-footer { flex-wrap: wrap; gap: 5px; } }
@media (max-width: 700px) {
  .academic-graph { padding: 8px; overflow: auto; }
  .academic-heading { width: calc(100% - 80px); order: -2; }
  .academic-toolbar h1 { font-size: 22px; }
  .academic-tools { order: -1; margin-left: auto; }
  .author-picker { flex: 1 1 100%; width: 100%; }
  .category-picker { flex: 1; }
  .academic-workspace, .academic-workspace.has-manifest { display: flex; flex-direction: column; min-height: 350px; flex: 1 0 350px; }
  .academic-canvas-panel { min-height: 350px; flex: 1; }
  .academic-manifest { max-height: 330px; flex-shrink: 0; }
  .academic-legend { gap: 8px; } .academic-pagination { gap: 6px; flex-wrap: wrap; }
  .academic-pagination .el-select { width: 95px; } .academic-pagination .el-button { padding: 8px 10px; }
  .author-suggestions { grid-template-columns: 1fr; } .timeline-year { grid-template-columns: 55px minmax(0, 1fr); }
  .timeline-year > h2 { font-size: 16px; } .timeline-year ol { padding-left: 16px; } .timeline-year li::before { left: -21px; }
  .timeline-year button { padding: 14px; gap: 7px; flex-wrap: wrap; } .timeline-date { width: 100%; flex-direction: row; gap: 8px; font-size: 12px; }
  .academic-timeline { flex: 1 0 auto; overflow: visible; padding: 14px 10px; }
}
</style>
