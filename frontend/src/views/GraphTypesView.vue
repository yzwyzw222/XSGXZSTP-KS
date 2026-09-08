<script setup lang="ts">
import { ElAlert, ElButton, ElDialog, ElInput, ElInputNumber, ElOption, ElSelect, ElTag, ElTable, ElTableColumn, type TableInstance } from 'element-plus'
import { storeToRefs } from 'pinia'
import { Circle, Search } from 'lucide-vue-next'
import { RouterLink } from 'vue-router'
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import { FilterField } from '@/components/business'
import { useGraphTypesStore } from '@/stores/graph-types'
import { useSessionStore } from '@/stores/session'
import type { GraphTypeDefinition } from '@/types/api'
import { reviewStatusLabel } from '@/utils/graph'

const props = defineProps<{ kind: GraphTypeDefinition['kind'] }>()
const store = useGraphTypesStore()
const { definitions, loading, saving, error } = storeToRefs(store)
const session = useSessionStore()
const keyword = ref('')
const table = ref<TableInstance | null>(null)
const selected = ref<GraphTypeDefinition[]>([])
const details = ref<GraphTypeDefinition | null>(null)
const batchStatus = ref('')
const batchRunning = ref(false)
const batchMessage = ref('')
const editor = ref<GraphTypeDefinition | null>(null)
const formError = ref('')
const title = computed(() => props.kind === 'NODE' ? '实体管理' : '关系管理')
const canEdit = computed(() => session.hasPermission('GRAPH_SYNC_MANAGE'))
const rows = computed(() => definitions.value.filter(item => item.kind === props.kind
  && (props.kind === 'NODE' ? ['AUTHOR', 'ACHIEVEMENT'] : ['AUTHORED', 'COAUTHORED']).includes(item.code)
  && `${item.displayName} ${item.code}`.toLowerCase().includes(keyword.value.trim().toLowerCase())))
const statusOptions = (['PENDING', 'APPROVED', 'REJECTED'] as const).map(value => ({ text: reviewStatusLabel(value), value }))
let pageVersion = 0

/** 批量更新沿用单项版本校验，首个失败立即停止并明确已保存数量。 */
async function saveBatch(value: GraphTypeDefinition['reviewStatus']): Promise<void> {
  if (!canEdit.value || !selected.value.length || batchRunning.value || saving.value) return
  const items = [...selected.value]
  const version = pageVersion
  batchRunning.value = true
  batchMessage.value = ''
  let completed = 0
  try {
    for (const item of items) {
      const saved = await store.save({ ...item, reviewStatus: value })
      if (version !== pageVersion) return
      if (!saved) break
      completed++
    }
    batchMessage.value = `已更新 ${completed} / ${items.length} 项类型状态。${completed < items.length ? '其余未更新，请刷新配置后重试。' : ''}`
  } finally {
    if (version === pageVersion) {
      batchRunning.value = false
      batchStatus.value = ''
      selected.value = []
      table.value?.clearSelection()
    }
  }
}

watch(() => props.kind, () => {
  pageVersion++
  batchRunning.value = false
  batchStatus.value = ''
  editor.value = null
  keyword.value = ''
  selected.value = []
  details.value = null
  batchMessage.value = ''
  store.reset()
  void store.load()
}, { immediate: true })
onBeforeUnmount(() => { pageVersion++; store.reset() })

function edit(value: GraphTypeDefinition): void {
  editor.value = { ...value }
  formError.value = ''
  error.value = ''
}

/** 客户端即时提示；服务端再次校验字段和版本。 */
async function save(): Promise<void> {
  const value = editor.value
  if (!value || !canEdit.value || saving.value) return
  if (!value.displayName.trim() || value.displayName.trim().length > 64
    || !/^#[0-9a-fA-F]{6}$/.test(value.color) || !Number.isInteger(value.size)
    || value.size < (value.kind === 'NODE' ? 16 : 1) || value.size > (value.kind === 'NODE' ? 96 : 8)) {
    formError.value = '请填写 1–64 字的名称、六位十六进制颜色及范围内的整数尺寸。'
    return
  }
  formError.value = ''
  if (await store.save({ ...value, displayName: value.displayName.trim() })) editor.value = null
}
</script>

<template>
  <section class="graph-types-page">
    <header class="types-heading"><h1><Circle :size="17" aria-hidden="true" />{{ title }}</h1></header>
    <div class="types-content">
      <div class="types-toolbar">
        <ElInput v-model="keyword" class="types-search" aria-label="搜索类型" placeholder="搜索类型名称或标识" clearable><template #suffix><Search :size="16" /></template></ElInput>
        <div class="types-actions">
          <ElSelect v-if="canEdit" v-model="batchStatus" aria-label="批量修改状态" placeholder="批量修改状态" :disabled="!selected.length || saving || batchRunning" @change="saveBatch"><ElOption v-for="state in statusOptions" :key="state.value" :label="state.text" :value="state.value" /></ElSelect>
          <ElButton :loading="loading" :disabled="saving || batchRunning" @click="store.load()">刷新配置</ElButton>
          <RouterLink to="/graph"><ElButton type="primary">查看图谱</ElButton></RouterLink>
        </div>
      </div>
      <ElAlert v-if="error && !editor" type="error" :closable="false" :title="error" show-icon class="mb-3" />
      <p v-if="batchMessage" class="mb-3 text-sm" role="status">{{ batchMessage }}</p>
      <div class="types-table" :aria-busy="loading">
        <ElTable :key="kind" ref="table" :data="rows" row-key="code" class="aacv-table" @selection-change="value => { selected = value }">
          <ElTableColumn v-if="canEdit" type="selection" width="48" :selectable="() => !saving && !batchRunning" />
          <ElTableColumn type="index" label="序号" width="72" />
          <ElTableColumn prop="displayName" :label="kind === 'NODE' ? '节点类型名称' : '关系类型名称'" min-width="210"><template #default="{ row }"><span>{{ row.displayName }}</span><span v-if="kind === 'RELATIONSHIP'" class="relation-direction">{{ row.code === 'AUTHORED' ? '作者 → 作品' : '作者 ↔ 作者 · 共同作品' }}</span></template></ElTableColumn>
          <ElTableColumn prop="code" label="类型标识" min-width="160" />
          <ElTableColumn v-if="kind === 'NODE'" prop="size" label="大小" min-width="90"><template #default="{ row }">{{ row.size }} px</template></ElTableColumn>
          <ElTableColumn v-if="kind === 'NODE'" label="颜色" min-width="90"><template #default="{ row }"><span class="type-color" :style="{ backgroundColor: row.color }" :aria-label="row.color" :title="row.color" /></template></ElTableColumn>
          <ElTableColumn prop="reviewStatus" label="状态" min-width="120" :filters="statusOptions" :filter-method="(value, row) => row.reviewStatus === value"><template #default="{ row }"><ElTag :type="row.reviewStatus === 'APPROVED' ? 'success' : row.reviewStatus === 'REJECTED' ? 'danger' : 'warning'" size="small">{{ reviewStatusLabel(row.reviewStatus) }}</ElTag></template></ElTableColumn>
          <ElTableColumn label="操作" width="130"><template #default="{ row }"><ElButton link type="primary" @click="details = row as GraphTypeDefinition">详情</ElButton><ElButton v-if="canEdit" link type="primary" :disabled="loading || saving || batchRunning" @click="edit(row as GraphTypeDefinition)">编辑</ElButton><span v-else class="ml-3 text-xs text-muted-foreground">只读</span></template></ElTableColumn>
          <template #empty>{{ loading ? '正在读取类型配置…' : '没有匹配的类型' }}</template>
        </ElTable>
      </div>
      <footer class="types-footer"><span>共 {{ rows.length }} 类</span><span>类型审核用于展示配置，修改后重新加载图谱生效。</span></footer>
    </div>
    <ElDialog :model-value="Boolean(details)" title="类型详情" width="min(520px, calc(100vw - 32px))" @update:model-value="value => { if (!value) details = null }">
      <dl v-if="details" class="grid gap-3 text-sm"><div>名称：{{ details.displayName }}</div><div>标识：{{ details.code }}</div><div>颜色：<span class="type-color" :style="{ backgroundColor: details.color }" /> {{ details.color }}</div><div>{{ kind === 'NODE' ? '节点直径' : '关系线宽' }}：{{ details.size }} px</div><div>类型审核：{{ reviewStatusLabel(details.reviewStatus) }}</div><div v-if="details.code === 'COAUTHORED'">由作者共同作品推导合作，详情证据仅覆盖当前返回的图谱。</div><div v-else-if="details.code === 'AUTHORED'">方向：作者 → 作品，展示作品中记录的署名关系。</div></dl>
    </ElDialog>
    <ElDialog :model-value="Boolean(editor)" title="编辑类型配置" width="min(520px, calc(100vw - 32px))" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" @update:model-value="value => { if (!value && !saving) editor = null }">
      <form v-if="editor" class="grid gap-4" @submit.prevent="save">
        <p class="text-sm text-muted-foreground">{{ editor.code }} · 版本 {{ editor.version }}</p>
        <FilterField label="类型名称"><ElInput v-model="editor.displayName" aria-label="类型名称" maxlength="64" :disabled="saving" /></FilterField>
        <FilterField label="颜色"><div class="flex items-center gap-3"><input v-model="editor.color" aria-label="选择颜色" type="color" class="h-9 w-12" :disabled="saving" /><ElInput v-model="editor.color" aria-label="颜色值" maxlength="7" :disabled="saving" /></div></FilterField>
        <FilterField :label="kind === 'NODE' ? '节点直径（px）' : '关系线宽（px）'"><ElInputNumber v-model="editor.size" aria-label="尺寸" :min="kind === 'NODE' ? 16 : 1" :max="kind === 'NODE' ? 96 : 8" :precision="0" :disabled="saving" /></FilterField>
        <FilterField label="类型审核状态"><ElSelect v-model="editor.reviewStatus" aria-label="编辑类型审核状态" :disabled="saving"><ElOption v-for="state in (['PENDING', 'APPROVED', 'REJECTED'] as const)" :key="state" :value="state" :label="reviewStatusLabel(state)" /></ElSelect></FilterField>
        <ElAlert v-if="formError || error" type="error" :closable="false" :title="formError || error" show-icon />
        <div class="flex justify-end gap-2"><ElButton :disabled="saving" @click="editor = null">取消</ElButton><ElButton native-type="submit" type="primary" :loading="saving">保存配置</ElButton></div>
      </form>
    </ElDialog>
  </section>
</template>

<style scoped>
.graph-types-page { display: flex; flex-direction: column; height: calc(100% - 24px); min-height: 0; margin: 12px; overflow: hidden; background: hsl(var(--card)); border: 1px solid hsl(var(--border)); }
.types-heading { padding: 16px; border-bottom: 1px solid hsl(var(--border)); }
.types-heading h1 { display: flex; align-items: center; gap: 6px; font-size: 16px; font-weight: 600; }
.types-heading svg { color: hsl(var(--primary)); stroke-width: 3; }
.types-content { flex: 1; min-height: 0; overflow: auto; padding: 12px 16px; }
.types-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-bottom: 18px; }
.types-search { max-width: 290px; }
.types-actions { margin-left: auto; display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.types-actions :deep(.el-select) { width: 156px; }
.types-actions :deep(.el-button + .el-button) { margin-left: 0; }
.type-color { display: inline-block; width: 18px; height: 18px; border-radius: 50%; vertical-align: middle; }
.relation-direction { display: block; margin-top: 4px; font-size: 12px; color: hsl(var(--muted-foreground)); }
.types-table :deep(.el-table__cell) { padding: 14px 0; }
.types-footer { display: flex; flex-wrap: wrap; gap: 12px; justify-content: space-between; padding: 18px 0; font-size: 12px; color: hsl(var(--muted-foreground)); }
@media (max-width: 767px) { .graph-types-page { height: calc(100% - 16px); margin: 8px; } .types-actions { margin-left: 0; } .types-search { max-width: none; } }
</style>
