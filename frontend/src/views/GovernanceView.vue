<script setup lang="ts">
import type { ColumnDef } from '@tanstack/vue-table'
import { GitCompareArrows, ShieldCheck } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'

import { DataTable, FilterBar, FilterField, JsonEvidence, PageHeader, PanelSection, StatusPill } from '@/components/business'
import { Alert, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { FormItem, FormLabel } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger } from '@/components/ui/select'
import { toast } from '@/components/ui/sonner'
import { Textarea } from '@/components/ui/textarea'
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
const filters = reactive({ entityType: '', status: 'PENDING', sourceId: '', ruleVersion: '' })
const decisionForm = reactive({ canonicalEntityId: '' as string, reason: '' })
const overrideForm = reactive({ achievementId: '', fieldName: '', value: '', reason: '', version: '0' })
const revertReason = ref('')

const columns: ColumnDef<DuplicateCandidate, any>[] = [
  { accessorKey: 'id', header: '编号', enableSorting: false, meta: { width: '80px' } },
  { accessorKey: 'entityType', header: '实体类型', enableSorting: false, meta: { width: '120px' } },
  { id: 'pair', accessorFn: (row) => `#${row.leftEntityId} ↔ #${row.rightEntityId}`, header: '实体对', enableSorting: false },
  { accessorKey: 'matchBasis', header: '匹配依据', enableSorting: false },
  { accessorKey: 'ruleVersion', header: '规则版本', enableSorting: false, meta: { width: '90px' } },
  { accessorKey: 'status', header: '状态', enableSorting: false, meta: { width: '110px' } },
  { id: 'createdAt', accessorFn: (row) => formatDateTime(row.createdAt), header: '创建时间', enableSorting: false, meta: { width: '160px' } },
  { id: 'actions', header: '操作', enableSorting: false, meta: { width: '100px' } },
]

async function load(page = 0): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    candidates.value = await governanceApi.candidates({
      entityType: filters.entityType.trim() || undefined,
      status: filters.status || undefined,
      sourceId: filters.sourceId ? Number(filters.sourceId) : undefined,
      ruleVersion: filters.ruleVersion ? Number(filters.ruleVersion) : undefined,
      page,
      size: candidates.value.size,
    })
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    loading.value = false
  }
}

function reset(): void {
  filters.entityType = ''
  filters.status = 'PENDING'
  filters.sourceId = ''
  filters.ruleVersion = ''
  void load()
}

async function openCandidate(candidate: DuplicateCandidate): Promise<void> {
  errorMessage.value = ''
  latestDecision.value = null
  decisionForm.canonicalEntityId = String(candidate.leftEntityId)
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
      ? await governanceApi.accept(selected.value, Number(decisionForm.canonicalEntityId), decisionForm.reason.trim())
      : await governanceApi.reject(selected.value, decisionForm.reason.trim())
    toast.success(action === 'accept' ? '重复候选已接受' : '重复候选已拒绝')
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
    toast.success('治理决定已撤销')
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
      Number(overrideForm.achievementId),
      overrideForm.fieldName.trim(),
      parseOverrideValue(),
      overrideForm.reason.trim(),
      Number(overrideForm.version) || 0,
    )
    toast.success('字段人工覆盖已保存')
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
    toast.success('字段覆盖已撤销')
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
    <PageHeader
      title="数据治理"
      description="基于证据审阅重复候选；所有接受、拒绝、撤销与字段覆盖均保留原因和乐观锁版本。"
    >
      <template #actions>
        <Button v-if="canManage" @click="openOverride"><ShieldCheck class="size-4" />字段人工覆盖</Button>
      </template>
    </PageHeader>

    <FilterBar :columns="4" :applying="loading" apply-text="查询候选" @apply="load()" @reset="reset">
      <FilterField label="实体类型"><Input v-model="filters.entityType" /></FilterField>
      <FilterField label="候选状态">
        <Select v-model="filters.status">
          <SelectTrigger placeholder="全部状态" />
          <SelectContent>
            <SelectItem value="PENDING">待审阅</SelectItem>
            <SelectItem value="ACCEPTED">已接受</SelectItem>
            <SelectItem value="REJECTED">已拒绝</SelectItem>
          </SelectContent>
        </Select>
      </FilterField>
      <FilterField label="来源 ID"><Input v-model="filters.sourceId" type="number" min="1" /></FilterField>
      <FilterField label="规则版本"><Input v-model="filters.ruleVersion" type="number" min="1" /></FilterField>
      <template #meta>默认仅显示待审阅候选</template>
    </FilterBar>

    <Alert v-if="errorMessage" variant="destructive"><AlertTitle>{{ errorMessage }}</AlertTitle></Alert>

    <PanelSection title="重复候选" :subtitle="`共 ${candidates.totalElements} 条`">
      <template #actions><GitCompareArrows class="size-4 text-muted-foreground" aria-hidden="true" /></template>
      <DataTable
        :columns="columns"
        :data="candidates.items"
        :loading="loading"
        :page="candidates.page"
        :size="candidates.size"
        :total="candidates.totalElements"
        empty-text="当前没有符合条件的重复候选"
        :get-row-id="(row) => String(row.id)"
        @update:page="load"
      >
        <template #cell-status="{ row }">
          <StatusPill
            :status="row.status === 'ACCEPTED' ? 'SUCCEEDED' : row.status === 'REJECTED' ? 'FAILED' : 'PENDING'"
            :label="row.status"
          />
        </template>
        <template #cell-actions="{ row }">
          <Button variant="link" size="sm" class="h-auto p-0" @click="openCandidate(row)">审阅证据</Button>
        </template>
      </DataTable>
    </PanelSection>

    <!-- 候选审阅 -->
    <Dialog v-model:open="detailVisible">
      <DialogContent class="sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>重复候选审阅</DialogTitle>
        </DialogHeader>
        <template v-if="selected">
          <div class="flex items-center justify-center gap-5 text-2xl font-semibold">
            <span>#{{ selected.leftEntityId }}</span>
            <Badge variant="subtle">↔</Badge>
            <span class="text-primary">#{{ selected.rightEntityId }}</span>
          </div>
          <p class="text-center text-xs text-muted-foreground">
            匹配依据：{{ selected.matchBasis }} · 规则版本 {{ selected.ruleVersion }} · 数据版本 {{ selected.version }}
          </p>
          <JsonEvidence :data="selected.evidence" max-height="220px" label="重复候选证据" />

          <div v-if="canManage && selected.status === 'PENDING'" class="grid gap-4 border-t border-border pt-4">
            <FormItem>
              <FormLabel for="canonicalEntityId">保留的规范实体 ID</FormLabel>
              <Input id="canonicalEntityId" v-model="decisionForm.canonicalEntityId" type="number" min="1" />
            </FormItem>
            <FormItem>
              <FormLabel for="decisionReason" required>治理原因</FormLabel>
              <Textarea id="decisionReason" v-model="decisionForm.reason" :rows="3" :maxlength="1000" placeholder="说明接受或拒绝的依据" />
            </FormItem>
            <div class="flex justify-end gap-2">
              <Button variant="outline" :loading="saving" @click="decide('reject')">拒绝候选</Button>
              <Button :loading="saving" @click="decide('accept')">接受并合并</Button>
            </div>
          </div>

          <div v-if="latestDecision" class="grid gap-2 rounded-lg border border-warning/40 bg-warning/8 p-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
            <div class="space-y-2">
              <strong class="text-sm">决定 #{{ latestDecision.id }} 已记录</strong>
              <Input v-model="revertReason" placeholder="填写撤销原因" :maxlength="1000" />
            </div>
            <Button variant="outline" :loading="saving" @click="revertDecision">撤销本次决定</Button>
          </div>
        </template>
      </DialogContent>
    </Dialog>

    <!-- 字段覆盖 -->
    <Dialog v-model:open="overrideVisible">
      <DialogContent class="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>字段人工覆盖</DialogTitle>
          <DialogDescription>请先从成果详情确认当前数据版本；提交冲突时必须刷新后重试。</DialogDescription>
        </DialogHeader>
        <Alert variant="warning"><AlertTitle>覆盖会记录修订与原因，可撤销但全程留痕。</AlertTitle></Alert>
        <form class="grid gap-4" novalidate @submit.prevent="saveOverride">
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <FormItem><FormLabel for="ovAchievementId">成果 ID</FormLabel><Input id="ovAchievementId" v-model="overrideForm.achievementId" type="number" min="1" /></FormItem>
            <FormItem><FormLabel for="ovVersion">成果数据版本</FormLabel><Input id="ovVersion" v-model="overrideForm.version" type="number" min="0" /></FormItem>
            <FormItem><FormLabel for="ovField">字段名</FormLabel><Input id="ovField" v-model="overrideForm.fieldName" :maxlength="64" /></FormItem>
            <FormItem><FormLabel for="ovValue">覆盖值（文本或 JSON）</FormLabel><Input id="ovValue" v-model="overrideForm.value" /></FormItem>
          </div>
          <FormItem><FormLabel for="ovReason" required>覆盖原因</FormLabel><Textarea id="ovReason" v-model="overrideForm.reason" :rows="3" :maxlength="1000" /></FormItem>

          <div v-if="latestOverride" class="grid gap-2 rounded-lg border border-warning/40 bg-warning/8 p-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
            <div class="space-y-2">
              <strong class="text-sm">覆盖修订 #{{ latestOverride.revisionId }} 已保存</strong>
              <Input v-model="revertReason" placeholder="填写撤销原因" :maxlength="1000" />
            </div>
            <Button variant="outline" :loading="saving" @click="revertOverride">撤销本次覆盖</Button>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" @click="overrideVisible = false">关闭</Button>
            <Button type="submit" :loading="saving">保存覆盖</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  </section>
</template>
