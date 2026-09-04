<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
  vLoading,
} from 'element-plus'

import { toErrorMessage } from '@/services/api'
import { governanceApi } from '@/services/business'
import { hasPermission } from '@/services/session'
import type { DuplicateCandidate, FieldOverride, MergeDecision, PageResponse } from '@/types/api'
import { formatDateTime } from '@/utils/format'

const candidates = ref<PageResponse<DuplicateCandidate>>({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const detailVisible = ref(false)
const overrideVisible = ref(false)
const selected = ref<DuplicateCandidate | null>(null)
const latestDecision = ref<MergeDecision | null>(null)
const latestOverride = ref<FieldOverride | null>(null)
const canManage = computed(() => hasPermission('GOVERNANCE_MANAGE'))
const filters = reactive({
  entityType: '',
  status: 'PENDING',
  sourceId: undefined as number | undefined,
  ruleVersion: undefined as number | undefined,
})
const decisionForm = reactive({ canonicalEntityId: undefined as number | undefined, reason: '' })
const overrideForm = reactive({
  achievementId: undefined as number | undefined,
  fieldName: '',
  value: '',
  reason: '',
  version: 0,
})
const revertReason = ref('')

async function load(page = 0): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    candidates.value = await governanceApi.candidates({ ...filters, page, size: candidates.value.size })
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function openCandidate(candidate: DuplicateCandidate): Promise<void> {
  errorMessage.value = ''
  latestDecision.value = null
  decisionForm.canonicalEntityId = candidate.leftEntityId
  decisionForm.reason = ''
  revertReason.value = ''
  detailVisible.value = true
  try {
    selected.value = await governanceApi.candidate(candidate.id)
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  }
}

async function decide(action: 'accept' | 'reject'): Promise<void> {
  if (!selected.value || !decisionForm.reason.trim()) {
    errorMessage.value = '治理原因不能为空'
    return
  }
  if (action === 'accept' && !decisionForm.canonicalEntityId) {
    errorMessage.value = '请选择保留的规范实体 ID'
    return
  }
  saving.value = true
  try {
    latestDecision.value = action === 'accept'
      ? await governanceApi.accept(selected.value, decisionForm.canonicalEntityId as number, decisionForm.reason.trim())
      : await governanceApi.reject(selected.value, decisionForm.reason.trim())
    ElMessage.success(action === 'accept' ? '重复候选已接受' : '重复候选已拒绝')
    await load(candidates.value.page)
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    saving.value = false
  }
}

async function revertDecision(): Promise<void> {
  if (!latestDecision.value || !revertReason.value.trim()) {
    errorMessage.value = '撤销原因不能为空'
    return
  }
  saving.value = true
  try {
    latestDecision.value = await governanceApi.revertDecision(latestDecision.value, revertReason.value.trim())
    ElMessage.success('治理决定已撤销')
    await load(candidates.value.page)
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    saving.value = false
  }
}

function parseOverrideValue(): unknown {
  try {
    return JSON.parse(overrideForm.value)
  } catch {
    return overrideForm.value
  }
}

async function saveOverride(): Promise<void> {
  if (!overrideForm.achievementId || !overrideForm.fieldName.trim() || !overrideForm.reason.trim() || !overrideForm.value.trim()) {
    errorMessage.value = '成果 ID、字段名、覆盖值和原因均不能为空'
    return
  }
  saving.value = true
  try {
    latestOverride.value = await governanceApi.overrideField(
      overrideForm.achievementId,
      overrideForm.fieldName.trim(),
      parseOverrideValue(),
      overrideForm.reason.trim(),
      overrideForm.version,
    )
    ElMessage.success('字段人工覆盖已保存')
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    saving.value = false
  }
}

async function revertOverride(): Promise<void> {
  if (!latestOverride.value || !revertReason.value.trim()) {
    errorMessage.value = '撤销原因不能为空'
    return
  }
  saving.value = true
  try {
    latestOverride.value = await governanceApi.revertOverride(latestOverride.value, revertReason.value.trim())
    ElMessage.success('字段覆盖已撤销')
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    saving.value = false
  }
}

function openOverride(): void {
  latestOverride.value = null
  revertReason.value = ''
  overrideVisible.value = true
}

onMounted(() => load())
</script>

<template>
  <section class="page-stack">
    <header class="page-heading">
      <div>
        <span class="eyebrow">DATA / GOVERNANCE</span>
        <h1>数据治理</h1>
        <p>基于证据审阅重复候选；所有接受、拒绝、撤销与字段覆盖均保留原因和乐观锁版本。</p>
      </div>
      <ElButton v-if="canManage" type="primary" @click="openOverride">字段人工覆盖</ElButton>
    </header>

    <div class="filter-panel">
      <div class="filter-grid">
        <label><span>实体类型</span><ElInput v-model="filters.entityType" clearable /></label>
        <label><span>候选状态</span><ElSelect v-model="filters.status" clearable><ElOption label="待审阅" value="PENDING" /><ElOption label="已接受" value="ACCEPTED" /><ElOption label="已拒绝" value="REJECTED" /></ElSelect></label>
        <label><span>来源 ID</span><ElInputNumber v-model="filters.sourceId" :min="1" /></label>
        <label><span>规则版本</span><ElInputNumber v-model="filters.ruleVersion" :min="1" /></label>
      </div>
      <div class="filter-footer"><span class="meta-line">默认仅显示待审阅候选</span><ElButton type="primary" :loading="loading" @click="load()">查询候选</ElButton></div>
    </div>
    <ElAlert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
    <div class="content-panel">
      <div class="toolbar"><strong>重复候选</strong><span class="meta-line">共 {{ candidates.totalElements }} 条</span></div>
      <ElTable v-loading="loading" :data="candidates.items" empty-text="当前没有符合条件的重复候选">
        <ElTableColumn prop="id" label="编号" width="85" />
        <ElTableColumn prop="entityType" label="实体类型" width="130" />
        <ElTableColumn label="实体对" min-width="170"><template #default="{ row }">#{{ row.leftEntityId }} ↔ #{{ row.rightEntityId }}</template></ElTableColumn>
        <ElTableColumn prop="matchBasis" label="匹配依据" min-width="220" />
        <ElTableColumn prop="ruleVersion" label="规则版本" width="100" />
        <ElTableColumn label="状态" width="110"><template #default="{ row }"><ElTag effect="plain">{{ row.status }}</ElTag></template></ElTableColumn>
        <ElTableColumn label="创建时间" width="170"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></ElTableColumn>
        <ElTableColumn label="操作" width="110"><template #default="{ row }"><ElButton link type="primary" @click="openCandidate(row as DuplicateCandidate)">审阅证据</ElButton></template></ElTableColumn>
      </ElTable>
      <div v-if="candidates.totalPages > 1" class="pagination-row">
        <ElPagination :current-page="candidates.page + 1" :page-size="candidates.size" :total="candidates.totalElements" layout="prev, pager, next" @current-change="(page: number) => load(page - 1)" />
      </div>
    </div>

    <ElDialog v-model="detailVisible" title="重复候选审阅" width="760px">
      <template v-if="selected">
        <div class="candidate-pair"><span>#{{ selected.leftEntityId }}</span><strong>↔</strong><span>#{{ selected.rightEntityId }}</span></div>
        <p class="meta-line">匹配依据：{{ selected.matchBasis }} · 规则版本 {{ selected.ruleVersion }} · 数据版本 {{ selected.version }}</p>
        <pre class="json-evidence">{{ JSON.stringify(selected.evidence, null, 2) }}</pre>
        <template v-if="canManage && selected.status === 'PENDING'">
          <ElForm label-position="top" class="decision-form">
            <ElFormItem label="保留的规范实体 ID"><ElInputNumber v-model="decisionForm.canonicalEntityId" :min="1" /></ElFormItem>
            <ElFormItem label="治理原因"><ElInput v-model="decisionForm.reason" type="textarea" :rows="3" maxlength="1000" show-word-limit /></ElFormItem>
          </ElForm>
          <div class="dialog-actions"><ElButton type="danger" plain :loading="saving" @click="decide('reject')">拒绝候选</ElButton><ElButton type="primary" :loading="saving" @click="decide('accept')">接受并合并</ElButton></div>
        </template>
        <div v-if="latestDecision" class="revert-box">
          <strong>决定 #{{ latestDecision.id }} 已记录</strong>
          <ElInput v-model="revertReason" placeholder="填写撤销原因" maxlength="1000" />
          <ElButton :loading="saving" @click="revertDecision">撤销本次决定</ElButton>
        </div>
      </template>
    </ElDialog>

    <ElDialog v-model="overrideVisible" title="字段人工覆盖" width="660px">
      <ElAlert title="请先从成果详情确认当前数据版本；提交冲突时必须刷新后重试。" type="warning" :closable="false" />
      <ElForm label-position="top" class="dialog-form">
        <div class="form-grid">
          <ElFormItem label="成果 ID"><ElInputNumber v-model="overrideForm.achievementId" :min="1" /></ElFormItem>
          <ElFormItem label="成果数据版本"><ElInputNumber v-model="overrideForm.version" :min="0" /></ElFormItem>
          <ElFormItem label="字段名"><ElInput v-model="overrideForm.fieldName" maxlength="64" /></ElFormItem>
          <ElFormItem label="覆盖值（文本或 JSON）"><ElInput v-model="overrideForm.value" /></ElFormItem>
        </div>
        <ElFormItem label="覆盖原因"><ElInput v-model="overrideForm.reason" type="textarea" :rows="3" maxlength="1000" /></ElFormItem>
      </ElForm>
      <div v-if="latestOverride" class="revert-box">
        <strong>覆盖修订 #{{ latestOverride.revisionId }} 已保存</strong>
        <ElInput v-model="revertReason" placeholder="填写撤销原因" maxlength="1000" />
        <ElButton :loading="saving" @click="revertOverride">撤销本次覆盖</ElButton>
      </div>
      <template #footer><ElButton @click="overrideVisible = false">关闭</ElButton><ElButton type="primary" :loading="saving" @click="saveOverride">保存覆盖</ElButton></template>
    </ElDialog>
  </section>
</template>
