<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import type { ColumnDef } from '@tanstack/vue-table'
import { Database, Plus, Radar } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useForm } from 'vee-validate'
import { z } from 'zod'

import { ConfirmDialog, DataTable, PageHeader, PanelSection, StatusPill } from '@/components/business'
import { Alert, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger } from '@/components/ui/select'
import { toast } from '@/components/ui/sonner'
import { Textarea } from '@/components/ui/textarea'
import { toErrorMessage } from '@/services/api'
import { sourceApi } from '@/services/business'
import { hasPermission } from '@/services/session'
import type { DataSource, PageResponse, SourceProbe } from '@/types/api'
import { formatDateTime } from '@/utils/format'

const result = ref<PageResponse<DataSource>>({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const loading = ref(false)
const saving = ref(false)
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

const columns: ColumnDef<DataSource, any>[] = [
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
  editing.value = null
  resetForm({ values: { ...defaults } })
  dialogVisible.value = true
}

function openEdit(source: DataSource): void {
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
    toast.success(editing.value ? '数据源配置已更新' : '数据源已创建')
    await load(result.value.page)
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    saving.value = false
  }
})

async function applyToggle(): Promise<void> {
  const source = confirmToggle.value
  if (!source) return
  const enabled = !source.enabled
  try {
    await sourceApi.setEnabled(source, enabled)
    toast.success('数据源状态已更新')
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
        <Button v-if="canManage" @click="openCreate"><Plus class="size-4" />新增数据源</Button>
      </template>
    </PageHeader>

    <Alert v-if="errorMessage" variant="destructive"><AlertTitle>{{ errorMessage }}</AlertTitle></Alert>

    <PanelSection title="来源配置" :subtitle="`共 ${result.totalElements} 个`">
      <template #actions><Database class="size-4 text-muted-foreground" aria-hidden="true" /></template>
      <DataTable
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
            <Button v-if="canManage" variant="link" size="sm" class="h-auto p-0" @click="openEdit(row)">编辑</Button>
            <Button
              v-if="canManage"
              variant="link"
              size="sm"
              class="h-auto p-0"
              :class="row.enabled ? 'text-destructive' : 'text-success'"
              @click="confirmToggle = row"
            >
              {{ row.enabled ? '停用' : '启用' }}
            </Button>
            <Button v-if="canProbe" variant="link" size="sm" class="h-auto p-0" :loading="probingId === row.id" @click="probe(row)">
              <Radar class="size-3.5" />探测
            </Button>
          </div>
        </template>
      </DataTable>
    </PanelSection>

    <!-- 新建/编辑对话框 -->
    <Dialog v-model:open="dialogVisible">
      <DialogContent class="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{{ editing ? '编辑数据源' : '新增数据源' }}</DialogTitle>
          <DialogDescription>配置采集来源的限流、超时与合规约束。</DialogDescription>
        </DialogHeader>
        <form class="grid gap-4" novalidate @submit.prevent="onSubmit">
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <FormItem>
              <FormLabel for="sourceType">来源类型</FormLabel>
              <Select v-model="sourceType" :disabled="Boolean(editing)">
                <SelectTrigger id="sourceType" placeholder="选择来源类型" />
                <SelectContent>
                  <SelectItem value="OPENALEX">OpenAlex</SelectItem>
                  <SelectItem value="CROSSREF">Crossref</SelectItem>
                </SelectContent>
              </Select>
            </FormItem>
            <FormItem>
              <FormLabel for="requestsPerSecond">每秒请求数</FormLabel>
              <Input id="requestsPerSecond" v-model="requestsPerSecond" type="number" min="1" max="10" :invalid="Boolean(errors.requestsPerSecond)" />
              <FormMessage :message="errors.requestsPerSecond" />
            </FormItem>
            <FormItem>
              <FormLabel for="maxConcurrency">最大并发数</FormLabel>
              <Input id="maxConcurrency" v-model="maxConcurrency" type="number" min="1" max="4" :invalid="Boolean(errors.maxConcurrency)" />
              <FormMessage :message="errors.maxConcurrency" />
            </FormItem>
            <FormItem>
              <FormLabel for="connectTimeoutSeconds">连接超时（秒）</FormLabel>
              <Input id="connectTimeoutSeconds" v-model="connectTimeoutSeconds" type="number" min="1" max="30" />
            </FormItem>
            <FormItem>
              <FormLabel for="responseTimeoutSeconds">响应超时（秒）</FormLabel>
              <Input id="responseTimeoutSeconds" v-model="responseTimeoutSeconds" type="number" min="1" max="120" />
            </FormItem>
            <FormItem>
              <FormLabel for="maxRetries">最大重试次数</FormLabel>
              <Input id="maxRetries" v-model="maxRetries" type="number" min="0" max="5" />
            </FormItem>
            <FormItem class="sm:col-span-2">
              <FormLabel for="maxResponseBytes">最大响应字节数</FormLabel>
              <Input id="maxResponseBytes" v-model="maxResponseBytes" type="number" min="1024" max="20971520" />
            </FormItem>
          </div>
          <FormItem>
            <FormLabel for="complianceNote" required>合规说明</FormLabel>
            <Textarea id="complianceNote" v-model="complianceNote" :rows="4" placeholder="说明该来源的使用条款与合规约束" :aria-invalid="Boolean(errors.complianceNote)" />
            <FormMessage :message="errors.complianceNote" />
          </FormItem>
          <DialogFooter>
            <Button type="button" variant="outline" @click="dialogVisible = false">取消</Button>
            <Button type="submit" :loading="saving">保存</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>

    <!-- 探测结果 -->
    <Dialog v-model:open="probeVisible">
      <DialogContent class="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>连通性检查结果</DialogTitle>
        </DialogHeader>
        <dl v-if="probeResult" class="grid grid-cols-2 gap-4 text-sm">
          <div class="space-y-1"><dt class="text-xs text-muted-foreground">是否可达</dt><dd><StatusPill :status="probeResult.reachable ? 'UP' : 'DOWN'" :label="probeResult.reachable ? '可达' : '不可达'" /></dd></div>
          <div class="space-y-1"><dt class="text-xs text-muted-foreground">HTTP 状态</dt><dd>{{ probeResult.statusCode ?? '—' }}</dd></div>
          <div class="space-y-1"><dt class="text-xs text-muted-foreground">错误分类</dt><dd>{{ probeResult.errorCategory || '—' }}</dd></div>
          <div class="space-y-1"><dt class="text-xs text-muted-foreground">检查时间</dt><dd>{{ formatDateTime(probeResult.checkedAt) }}</dd></div>
          <div class="col-span-2 space-y-1">
            <dt class="text-xs text-muted-foreground">限流摘要</dt>
            <dd class="flex flex-wrap gap-1.5">
              <Badge v-for="(value, key) in probeResult.rateLimitSummary" :key="key" variant="subtle" class="mono-evidence">{{ key }}: {{ value }}</Badge>
              <span v-if="!Object.keys(probeResult.rateLimitSummary).length" class="text-muted-foreground">—</span>
            </dd>
          </div>
        </dl>
      </DialogContent>
    </Dialog>

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
