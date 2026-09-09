<script setup lang="ts">
import { ElAlert, ElButton, ElDialog, ElInput, ElInputNumber, ElMessage, ElOption, ElSelect, ElTag } from 'element-plus'
import FormField from '@/components/business/FormField.vue'
import { toTypedSchema } from '@vee-validate/zod'
import type { DataTableColumn } from '@/components/business/types'
import { Database, Plus, Radar } from 'lucide-vue-next'
import { computed, nextTick, onMounted, ref, useId } from 'vue'
import { useForm } from 'vee-validate'
import { z } from 'zod'

import { ConfirmDialog, DataTable, PageHeader, PanelSection, StatusPill } from '@/components/business'
import { toErrorMessage } from '@/services/api'
import { sourceApi } from '@/services/business'
import { useSessionStore } from '@/stores/session'
import type { DataSource, PageResponse, SourceProbe } from '@/types/api'
import { formatDateTime } from '@/utils/format'

const session = useSessionStore()
const { hasPermission } = session

const result = ref<PageResponse<DataSource>>({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const loading = ref(false)
const saving = ref(false)
const sourceFormId = useId()
const sourceForm = ref<HTMLFormElement | null>(null)
let dialogTrigger: HTMLElement | null = null

function restoreDialogFocus(): void {
  void nextTick(() => {
    if (dialogTrigger?.isConnected) dialogTrigger.focus()
    dialogTrigger = null
  })
}
const probingId = ref<number | null>(null)
const errorMessage = ref('')
const dialogVisible = ref(false)
const editing = ref<DataSource | null>(null)
const probeVisible = ref(false)
const probeResult = ref<SourceProbe | null>(null)
const confirmToggle = ref<DataSource | null>(null)
const canManage = computed(() => hasPermission('SOURCE_MANAGE'))
const canProbe = computed(() => hasPermission('SOURCE_PROBE'))

const schema = z.object({
  sourceType: z.enum(['OPENALEX', 'CROSSREF']),
  requestsPerSecond: z.coerce.number().min(1).max(10, '不超过 10'),
  maxConcurrency: z.coerce.number().min(1).max(4, '不超过 4'),
  connectTimeoutSeconds: z.coerce.number().min(1).max(30),
  responseTimeoutSeconds: z.coerce.number().min(1).max(120),
  maxRetries: z.coerce.number().min(0).max(5),
  maxResponseBytes: z.coerce.number().min(1024).max(20971520),
  complianceNote: z.string().trim().min(1, '合规说明不能为空').max(1000),
})

const defaults = {
  sourceType: 'OPENALEX' as const,
  requestsPerSecond: 1,
  maxConcurrency: 1,
  connectTimeoutSeconds: 10,
  responseTimeoutSeconds: 30,
  maxRetries: 2,
  maxResponseBytes: 5_242_880,
  complianceNote: '',
}

const { handleSubmit, defineField, errors, resetForm } = useForm({
  validationSchema: toTypedSchema(schema),
  initialValues: { ...defaults },
})
const [sourceType] = defineField('sourceType')
const [requestsPerSecond] = defineField('requestsPerSecond')
const [maxConcurrency] = defineField('maxConcurrency')
const [connectTimeoutSeconds] = defineField('connectTimeoutSeconds')
const [responseTimeoutSeconds] = defineField('responseTimeoutSeconds')
const [maxRetries] = defineField('maxRetries')
const [maxResponseBytes] = defineField('maxResponseBytes')
const [complianceNote] = defineField('complianceNote')

const columns: DataTableColumn<DataSource>[] = [
  { accessorKey: 'sourceCode', header: '来源代码', enableSorting: false },
  { accessorKey: 'sourceType', header: '类型', enableSorting: false, meta: { width: '100px' } },
  { accessorKey: 'baseUri', header: '基础地址', enableSorting: false },
  { id: 'rate', accessorFn: (row) => `${row.requestsPerSecond} req/s · ${row.maxConcurrency} 并发`, header: '限流', enableSorting: false, meta: { width: '150px' } },
  { id: 'enabled', accessorFn: (row) => (row.enabled ? '启用' : '停用'), header: '状态', enableSorting: false, meta: { width: '90px' } },
  { id: 'lastSuccess', accessorFn: (row) => formatDateTime(row.lastSuccessAt), header: '最近成功', enableSorting: false, meta: { width: '160px' } },
  { accessorKey: 'consecutiveFailures', header: '连续失败', enableSorting: false, meta: { width: '90px' } },
  { id: 'actions', header: '操作', enableSorting: false, meta: { width: '200px' } },
]

async function load(page = 0): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    result.value = await sourceApi.page(page, result.value.size)
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  dialogTrigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
  errorMessage.value = ''
  editing.value = null
  resetForm({ values: { ...defaults } })
  dialogVisible.value = true
}

function openEdit(source: DataSource): void {
  dialogTrigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
  errorMessage.value = ''
  editing.value = source
  resetForm({
    values: {
      sourceType: source.sourceType,
      requestsPerSecond: source.requestsPerSecond,
      maxConcurrency: source.maxConcurrency,
      connectTimeoutSeconds: source.connectTimeoutSeconds,
      responseTimeoutSeconds: source.responseTimeoutSeconds,
      maxRetries: source.maxRetries,
      maxResponseBytes: source.maxResponseBytes,
      complianceNote: source.complianceNote,
    },
  })
  dialogVisible.value = true
}

const onSubmit = handleSubmit(async (values) => {
  if (saving.value) return
  saving.value = true
  errorMessage.value = ''
  try {
    const payload = { ...values, complianceNote: values.complianceNote.trim(), version: editing.value?.version }
    if (editing.value) {
      await sourceApi.update(editing.value.id, payload)
    } else {
      await sourceApi.create(payload)
    }
    dialogVisible.value = false
    ElMessage.success(editing.value ? '数据源配置已更新' : '数据源已创建')
    await load(result.value.page)
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    saving.value = false
  }
}, async () => {
  await nextTick()
  const invalidField = sourceForm.value?.querySelector<HTMLElement>('[aria-invalid="true"]')
  invalidField?.focus({ preventScroll: true })
  invalidField?.scrollIntoView({ block: 'nearest', behavior: 'instant' })
})

async function applyToggle(): Promise<void> {
  const source = confirmToggle.value
  if (!source) return
  const enabled = !source.enabled
  try {
    await sourceApi.setEnabled(source, enabled)
    ElMessage.success('数据源状态已更新')
    confirmToggle.value = null
    await load(result.value.page)
  } catch (error) {
    confirmToggle.value = null
    errorMessage.value = toErrorMessage(error)
  }
}

async function probe(source: DataSource): Promise<void> {
  probingId.value = source.id
  errorMessage.value = ''
  try {
    probeResult.value = await sourceApi.probe(source.id)
    probeVisible.value = true
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    probingId.value = null
  }
}

onMounted(() => load())
</script>

<template>
  <section class="page-stack">
    <PageHeader
      title="数据源"
      description="查看采集来源的连接参数、健康记录和合规约束；管理操作仅向管理员开放。"
    >
      <template #actions>
        <ElButton v-if="canManage" @click="openCreate" type="primary"><Plus class="size-4" />新增数据源</ElButton>
      </template>
    </PageHeader>

    <ElAlert v-if="errorMessage && !dialogVisible && !probeVisible" type="error" :closable="false" show-icon><template #title>{{ errorMessage }}</template></ElAlert>

    <div v-if="result.items.length" class="source-highlights" aria-label="当前页来源概况">
      <article v-for="source in result.items.slice(0, 2)" :key="source.id" class="source-highlight"><Database aria-hidden="true" /><div><h2>{{ source.sourceCode }}</h2><p>{{ source.baseUri }}</p><p>{{ source.requestsPerSecond }} 次 / 秒 · 并发 {{ source.maxConcurrency }} · 最近成功 {{ formatDateTime(source.lastSuccessAt) }}</p></div><StatusPill :status="source.enabled ? 'ACTIVE' : 'DISABLED'" /></article>
    </div>

    <PanelSection title="来源配置" :subtitle="`共 ${result.totalElements} 个`">
      <template #actions><Database class="size-4 text-muted-foreground" aria-hidden="true" /></template>
      <DataTable fill
        :columns="columns"
        :data="result.items"
        :loading="loading"
        :page="result.page"
        :size="result.size"
        :total="result.totalElements"
        empty-text="暂无数据源"
        :get-row-id="(row) => String(row.id)"
        @update:page="load"
      >
        <template #cell-enabled="{ row }">
          <StatusPill :status="row.enabled ? 'ACTIVE' : 'DISABLED'" />
        </template>
        <template #cell-consecutiveFailures="{ row }">
          <span :class="row.consecutiveFailures > 0 ? 'font-medium text-destructive' : 'text-muted-foreground'">
            {{ row.consecutiveFailures }}
          </span>
        </template>
        <template #cell-actions="{ row }">
          <div class="flex flex-wrap items-center gap-1">
            <ElButton v-if="canManage" size="small" class="h-auto p-0" @click="openEdit(row)" link type="primary">编辑</ElButton>
            <ElButton
              v-if="canManage"
              size="small"
              class="h-auto p-0"
              :class="row.enabled ? 'text-destructive' : 'text-success'"
              @click="confirmToggle = row"
             link type="primary">
              {{ row.enabled ? '停用' : '启用' }}
            </ElButton>
            <ElButton v-if="canProbe" size="small" class="h-auto p-0" :loading="probingId === row.id" @click="probe(row)" link type="primary">
              <Radar class="size-3.5" />探测
            </ElButton>
          </div>
        </template>
      </DataTable>
    </PanelSection>

    <!-- 新建/编辑对话框 -->
    <ElDialog v-model="dialogVisible" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" width="min(672px, calc(100vw - 32px))" append-to-body destroy-on-close class="aacv-form-dialog" body-class="aacv-dialog-body" @closed="restoreDialogFocus">
          <template #header="{ titleId }">
            <h2 :id="titleId">{{ editing ? '编辑数据源' : '新增数据源' }}</h2>
            <ElAlert v-if="errorMessage" class="mt-3" type="error" :closable="false" :title="errorMessage" show-icon />
          </template>
          <p class="mb-4 text-sm text-muted-foreground">配置采集来源的限流、超时与合规约束。</p>

        <form :id="sourceFormId" ref="sourceForm" class="grid gap-4" novalidate @submit.prevent="onSubmit">
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <FormField for="sourceType" label="来源类型" :error="errors.sourceType">
              <ElSelect v-model="sourceType" :disabled="Boolean(editing)" id="sourceType" :aria-describedby="errors.sourceType ? 'sourceType-error' : undefined" placeholder="选择来源类型"  filterable>
                  <ElOption value="OPENALEX" :label="'OpenAlex'" />
                  <ElOption value="CROSSREF" :label="'Crossref'" />
                </ElSelect>
            </FormField>
            <FormField for="requestsPerSecond" label="每秒请求数" :error="errors.requestsPerSecond">
              <ElInputNumber id="requestsPerSecond" :aria-describedby="errors.requestsPerSecond ? 'requestsPerSecond-error' : undefined" v-model="requestsPerSecond"  :min="1" :max="10" :aria-invalid="Boolean(errors.requestsPerSecond)"  controls-position="right" style="width: 100%" />
            </FormField>
            <FormField for="maxConcurrency" label="最大并发数" :error="errors.maxConcurrency">
              <ElInputNumber id="maxConcurrency" :aria-describedby="errors.maxConcurrency ? 'maxConcurrency-error' : undefined" v-model="maxConcurrency"  :min="1" :max="4" :aria-invalid="Boolean(errors.maxConcurrency)"  controls-position="right" style="width: 100%" />
            </FormField>
            <FormField for="connectTimeoutSeconds" label="连接超时（秒）" :error="errors.connectTimeoutSeconds">
              <ElInputNumber id="connectTimeoutSeconds" :aria-describedby="errors.connectTimeoutSeconds ? 'connectTimeoutSeconds-error' : undefined" v-model="connectTimeoutSeconds"  :min="1" :max="30"  controls-position="right" style="width: 100%" />
            </FormField>
            <FormField for="responseTimeoutSeconds" label="响应超时（秒）" :error="errors.responseTimeoutSeconds">
              <ElInputNumber id="responseTimeoutSeconds" :aria-describedby="errors.responseTimeoutSeconds ? 'responseTimeoutSeconds-error' : undefined" v-model="responseTimeoutSeconds"  :min="1" :max="120"  controls-position="right" style="width: 100%" />
            </FormField>
            <FormField for="maxRetries" label="最大重试次数" :error="errors.maxRetries">
              <ElInputNumber id="maxRetries" :aria-describedby="errors.maxRetries ? 'maxRetries-error' : undefined" v-model="maxRetries"  :min="0" :max="5"  controls-position="right" style="width: 100%" />
            </FormField>
            <FormField class="sm:col-span-2" for="maxResponseBytes" label="最大响应字节数" :error="errors.maxResponseBytes">
              <ElInputNumber id="maxResponseBytes" :aria-describedby="errors.maxResponseBytes ? 'maxResponseBytes-error' : undefined" v-model="maxResponseBytes"  :min="1024" :max="20971520"  controls-position="right" style="width: 100%" />
            </FormField>
          </div>
          <FormField for="complianceNote" required label="合规说明" :error="errors.complianceNote">
            <ElInput type="textarea" id="complianceNote" :aria-describedby="errors.complianceNote ? 'complianceNote-error' : undefined" v-model="complianceNote" :rows="4" placeholder="说明该来源的使用条款与合规约束" :aria-invalid="Boolean(errors.complianceNote)" />
          </FormField>
        </form>
        <template #footer>
          <div class="flex flex-wrap justify-end gap-2">
            <ElButton native-type="button" :disabled="saving" @click="dialogVisible = false" plain>取消</ElButton>
            <ElButton native-type="submit" :form="sourceFormId" :loading="saving" type="primary">保存</ElButton>
          </div>
        </template>
      </ElDialog>

    <!-- 探测结果 -->
    <ElDialog v-model="probeVisible" width="min(512px, calc(100vw - 32px))" append-to-body destroy-on-close class="aacv-form-dialog" body-class="aacv-dialog-body">
        <ElAlert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" show-icon />

          <template #header="{ titleId }"><h2 :id="titleId">连通性检查结果</h2></template>

        <dl v-if="probeResult" class="grid grid-cols-2 gap-4 text-sm">
          <div class="space-y-1"><dt class="text-xs text-muted-foreground">是否可达</dt><dd><StatusPill :status="probeResult.reachable ? 'UP' : 'DOWN'" :label="probeResult.reachable ? '可达' : '不可达'" /></dd></div>
          <div class="space-y-1"><dt class="text-xs text-muted-foreground">HTTP 状态</dt><dd>{{ probeResult.statusCode ?? '—' }}</dd></div>
          <div class="space-y-1"><dt class="text-xs text-muted-foreground">错误分类</dt><dd>{{ probeResult.errorCategory || '—' }}</dd></div>
          <div class="space-y-1"><dt class="text-xs text-muted-foreground">检查时间</dt><dd>{{ formatDateTime(probeResult.checkedAt) }}</dd></div>
          <div class="col-span-2 space-y-1">
            <dt class="text-xs text-muted-foreground">限流摘要</dt>
            <dd class="flex flex-wrap gap-1.5">
              <ElTag v-for="(value, key) in probeResult.rateLimitSummary" :key="key" type="info" class="mono-evidence" size="small">{{ key }}: {{ value }}</ElTag>
              <span v-if="!Object.keys(probeResult.rateLimitSummary).length" class="text-muted-foreground">—</span>
            </dd>
          </div>
        </dl>
      </ElDialog>

    <!-- 状态变更确认 -->
    <ConfirmDialog
      :open="Boolean(confirmToggle)"
      title="状态变更确认"
      :description="confirmToggle ? `确认${confirmToggle.enabled ? '停用' : '启用'}数据源 ${confirmToggle.sourceCode}？` : ''"
      :confirm-text="confirmToggle?.enabled ? '停用' : '启用'"
      :destructive="confirmToggle?.enabled === true"
      @update:open="(v) => { if (!v) confirmToggle = null }"
      @confirm="applyToggle"
    />
  </section>
</template>
