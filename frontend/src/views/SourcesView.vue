<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'

import { toErrorMessage } from '@/services/api'
import { sourceApi } from '@/services/business'
import { hasPermission } from '@/services/session'
import type { DataSource, PageResponse, SourceConfigurationInput, SourceProbe } from '@/types/api'
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
const canManage = computed(() => hasPermission('SOURCE_MANAGE'))
const canProbe = computed(() => hasPermission('SOURCE_PROBE'))
const form = reactive<SourceConfigurationInput>({
  sourceType: 'OPENALEX',
  requestsPerSecond: 1,
  maxConcurrency: 1,
  connectTimeoutSeconds: 10,
  responseTimeoutSeconds: 30,
  maxRetries: 2,
  maxResponseBytes: 5_242_880,
  complianceNote: '',
})

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
  Object.assign(form, {
    sourceType: 'OPENALEX',
    requestsPerSecond: 1,
    maxConcurrency: 1,
    connectTimeoutSeconds: 10,
    responseTimeoutSeconds: 30,
    maxRetries: 2,
    maxResponseBytes: 5_242_880,
    complianceNote: '',
    version: undefined,
  })
  dialogVisible.value = true
}

function openEdit(source: DataSource): void {
  editing.value = source
  Object.assign(form, {
    sourceType: source.sourceType,
    requestsPerSecond: source.requestsPerSecond,
    maxConcurrency: source.maxConcurrency,
    connectTimeoutSeconds: source.connectTimeoutSeconds,
    responseTimeoutSeconds: source.responseTimeoutSeconds,
    maxRetries: source.maxRetries,
    maxResponseBytes: source.maxResponseBytes,
    complianceNote: source.complianceNote,
    version: source.version,
  })
  dialogVisible.value = true
}

async function save(): Promise<void> {
  if (!form.complianceNote.trim()) {
    errorMessage.value = '合规说明不能为空'
    return
  }
  saving.value = true
  errorMessage.value = ''
  try {
    if (editing.value) {
      await sourceApi.update(editing.value.id, { ...form, complianceNote: form.complianceNote.trim() })
    } else {
      await sourceApi.create({ ...form, complianceNote: form.complianceNote.trim() })
    }
    dialogVisible.value = false
    ElMessage.success(editing.value ? '数据源配置已更新' : '数据源已创建')
    await load(result.value.page)
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    saving.value = false
  }
}

async function toggle(source: DataSource): Promise<void> {
  const enabled = !source.enabled
  try {
    await ElMessageBox.confirm(
      '确认' + (enabled ? '启用' : '停用') + '数据源 ' + source.sourceCode + '？',
      '状态变更确认',
      { type: 'warning' },
    )
    await sourceApi.setEnabled(source, enabled)
    ElMessage.success('数据源状态已更新')
    await load(result.value.page)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') errorMessage.value = toErrorMessage(error)
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
    <header class="page-heading">
      <div>
        <span class="eyebrow">INGESTION / SOURCES</span>
        <h1>数据源</h1>
        <p>查看采集来源的连接参数、健康记录和合规约束；管理操作仅向管理员开放。</p>
      </div>
      <ElButton v-if="canManage" type="primary" @click="openCreate">新增数据源</ElButton>
    </header>

    <ElAlert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
    <div class="content-panel">
      <div class="toolbar"><strong>来源配置</strong><span class="meta-line">共 {{ result.totalElements }} 个</span></div>
      <div v-if="loading" class="source-skeleton-grid" aria-label="正在加载数据源">
        <article v-for="key in ['openalex', 'crossref', 'source-c', 'source-d']" :key="key" class="source-skeleton-card">
          <span class="source-skeleton-icon" />
          <span class="source-skeleton-copy"><strong /><small /><small /></span>
        </article>
      </div>
      <ElTable v-else :data="result.items" empty-text="暂无数据源">
        <ElTableColumn prop="sourceCode" label="来源代码" min-width="130" />
        <ElTableColumn prop="sourceType" label="类型" width="110" />
        <ElTableColumn prop="baseUri" label="基础地址" min-width="220" />
        <ElTableColumn label="限流" width="130">
          <template #default="{ row }">{{ row.requestsPerSecond }} req/s · {{ row.maxConcurrency }} 并发</template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="100">
          <template #default="{ row }"><ElTag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</ElTag></template>
        </ElTableColumn>
        <ElTableColumn label="最近成功" width="170">
          <template #default="{ row }">{{ formatDateTime(row.lastSuccessAt) }}</template>
        </ElTableColumn>
        <ElTableColumn label="连续失败" width="100">
          <template #default="{ row }"><span :class="{ 'danger-text': row.consecutiveFailures > 0 }">{{ row.consecutiveFailures }}</span></template>
        </ElTableColumn>
        <ElTableColumn v-if="canManage || canProbe" label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <ElButton v-if="canManage" link type="primary" @click="openEdit(row as DataSource)">编辑</ElButton>
            <ElButton v-if="canManage" link :type="row.enabled ? 'danger' : 'success'" @click="toggle(row as DataSource)">
              {{ row.enabled ? '停用' : '启用' }}
            </ElButton>
            <ElButton v-if="canProbe" link type="primary" :loading="probingId === row.id" @click="probe(row as DataSource)">探测</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <div v-if="result.totalPages > 1" class="pagination-row">
        <ElPagination :current-page="result.page + 1" :page-size="result.size" :total="result.totalElements" layout="prev, pager, next" @current-change="(page: number) => load(page - 1)" />
      </div>
    </div>

    <ElDialog v-model="dialogVisible" :title="editing ? '编辑数据源' : '新增数据源'" width="620px">
      <ElForm label-position="top">
        <div class="form-grid">
          <ElFormItem label="来源类型">
            <ElSelect v-model="form.sourceType" :disabled="Boolean(editing)">
              <ElOption label="OpenAlex" value="OPENALEX" />
              <ElOption label="Crossref" value="CROSSREF" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="每秒请求数"><ElInputNumber v-model="form.requestsPerSecond" :min="1" :max="10" /></ElFormItem>
          <ElFormItem label="最大并发数"><ElInputNumber v-model="form.maxConcurrency" :min="1" :max="4" /></ElFormItem>
          <ElFormItem label="连接超时（秒）"><ElInputNumber v-model="form.connectTimeoutSeconds" :min="1" :max="30" /></ElFormItem>
          <ElFormItem label="响应超时（秒）"><ElInputNumber v-model="form.responseTimeoutSeconds" :min="1" :max="120" /></ElFormItem>
          <ElFormItem label="最大重试次数"><ElInputNumber v-model="form.maxRetries" :min="0" :max="5" /></ElFormItem>
          <ElFormItem label="最大响应字节数"><ElInputNumber v-model="form.maxResponseBytes" :min="1024" :max="20971520" /></ElFormItem>
        </div>
        <ElFormItem label="合规说明">
          <ElInput v-model="form.complianceNote" type="textarea" :rows="4" maxlength="1000" show-word-limit />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="save">保存</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="probeVisible" title="连通性检查结果" width="560px">
      <ElDescriptions v-if="probeResult" :column="2" border>
        <ElDescriptionsItem label="是否可达"><ElTag :type="probeResult.reachable ? 'success' : 'danger'">{{ probeResult.reachable ? '可达' : '不可达' }}</ElTag></ElDescriptionsItem>
        <ElDescriptionsItem label="HTTP 状态">{{ probeResult.statusCode ?? '—' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="错误分类">{{ probeResult.errorCategory || '—' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="检查时间">{{ formatDateTime(probeResult.checkedAt) }}</ElDescriptionsItem>
        <ElDescriptionsItem label="限流摘要" :span="2">{{ JSON.stringify(probeResult.rateLimitSummary) }}</ElDescriptionsItem>
      </ElDescriptions>
    </ElDialog>
  </section>
</template>
