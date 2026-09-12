<script setup lang="ts">
import { ElButton, ElDatePicker, ElOption, ElPopover, ElSelect, ElTabPane, ElTabs } from 'element-plus'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { CalendarDays, Filter, RefreshCw } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'

import AuditLogTable from '@/components/business/AuditLogTable.vue'
import CompactFieldSearch from '@/components/business/CompactFieldSearch.vue'
import ErrorState from '@/components/business/ErrorState.vue'
import { toErrorMessage } from '@/services/api'
import { getAudits } from '@/services/audits'
import type { AuditCategory, AuditFilter, AuditLog, PageResponse } from '@/types/api'
import { auditActions } from '@/utils/audit'
import { instantRange } from '@/utils/date'

const route = useRoute()
const router = useRouter()
const category = ref<AuditCategory>('OPERATION')
const searchFields = [{ value: 'username', label: '账号' }] as const
const timeOpen = ref(false)
const actionOpen = ref(false)
const resultOpen = ref(false)
const timeDraft = reactive<{ from: string | null; to: string | null }>({ from: null, to: null })
const timeError = ref('')
/**
 * 时间控件以本地时间字符串编辑（YYYY-MM-DDTHH:mm:ss），
 * 提交前显式转换为后端要求的 ISO-8601 UTC；
 * 区间语义为起点包含、终点不包含。
 */
const form = reactive<{
  username: string
  from: string | null
  to: string | null
  result: string
  action: string
}>({ username: '', from: null, to: null, result: '', action: '' })
const applied = ref<AuditFilter>({})
const logs = ref<PageResponse<AuditLog>>({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const loading = ref(false)
const error = ref('')
let request: AbortController | undefined

const actionOptions = computed(() => Object.entries(auditActions).filter(([action]) =>
  ['LOGIN_SUCCEEDED', 'LOGIN_FAILED', 'LOGOUT'].includes(action) === (category.value === 'LOGIN')))

const resultOptions = [
  { value: '', label: '全部结果' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILURE', label: '失败' },
]

/** 取消旧请求，确保快速切换分类或筛选时只显示最后一次查询。 */
async function load(page = 0): Promise<void> {
  request?.abort()
  const current = request = new AbortController()
  loading.value = true
  error.value = ''
  try {
    const response = await getAudits(applied.value, page, 20, current.signal)
    if (!current.signal.aborted) logs.value = response
  } catch (failure) {
    if (!current.signal.aborted) error.value = toErrorMessage(failure)
  } finally {
    if (!current.signal.aborted) loading.value = false
  }
}

function search(): void {
  let range: ReturnType<typeof instantRange>
  try {
    range = instantRange(form.from, form.to)
  } catch (failure) {
    error.value = failure instanceof RangeError ? failure.message : toErrorMessage(failure)
    return
  }
  applied.value = {
    category: category.value,
    username: form.username.trim(),
    ...range,
    result: form.result as AuditFilter['result'],
    action: form.action,
  }
  void load()
}

function reset(): void {
  Object.assign(form, { username: '', from: null, to: null, result: '', action: '' })
  timeOpen.value = actionOpen.value = resultOpen.value = false
  timeError.value = ''
  search()
}

watch(timeOpen, open => {
  if (open) {
    Object.assign(timeDraft, { from: form.from, to: form.to })
    timeError.value = ''
  }
})

function applyTimeRange(): void {
  try {
    instantRange(timeDraft.from, timeDraft.to)
  } catch (failure) {
    timeError.value = failure instanceof RangeError ? failure.message : toErrorMessage(failure)
    return
  }
  Object.assign(form, timeDraft)
  timeOpen.value = false
  search()
}

function clearTimeRange(): void {
  Object.assign(timeDraft, { from: null, to: null })
  applyTimeRange()
}

/** 分类以路由查询参数为唯一来源，标签页只负责发起导航。 */
function onCategoryChange(value: string | number): void {
  void router.replace({ query: { ...route.query, category: String(value) } })
}

watch(() => route.query.category, (value) => {
  category.value = value === 'LOGIN' ? 'LOGIN' : 'OPERATION'
  form.action = ''
  timeOpen.value = actionOpen.value = resultOpen.value = false
  logs.value = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
  search()
}, { immediate: true })

onBeforeUnmount(() => request?.abort())
</script>

<template>
  <section class="page-stack logs-page">
    <header class="logs-toolbar">
      <div class="logs-heading"><h1>日志管理</h1><span>{{ loading ? '正在读取…' : `共 ${logs.totalElements.toLocaleString('zh-CN')} 条` }}</span></div>
      <div class="logs-actions">
        <CompactFieldSearch v-model="form.username" field="username" :fields="searchFields" :maxlength="64" :loading="loading" @submit="search" />
        <ElButton text size="small" @click="reset">重置</ElButton>
        <ElButton size="small" :disabled="loading" aria-label="刷新日志" title="刷新日志" @click="load(logs.page)"><RefreshCw :size="14" /></ElButton>
      </div>
    </header>

    <ElTabs class="logs-tabs" :model-value="category" @tab-change="onCategoryChange">
      <ElTabPane label="操作日志" name="OPERATION" />
      <ElTabPane label="登录日志" name="LOGIN" />
    </ElTabs>

    <ErrorState v-if="error" :message="error" retryable @retry="search" />
    <section v-else class="logs-workspace" aria-label="日志查询结果">
      <AuditLogTable fill
        :items="logs.items"
        :page="logs.page"
        :size="logs.size"
        :total="logs.totalElements"
        :loading="loading"
        @update:page="load"
      >
        <template #header-createdAt>
          <span class="logs-column-heading">时间
            <ElPopover v-model:visible="timeOpen" trigger="click" placement="bottom-start" :width="300">
              <template #reference><ElButton size="small" :type="applied.from || applied.to ? 'primary' : undefined" :plain="!!(applied.from || applied.to)" :aria-pressed="!!(applied.from || applied.to)" aria-label="按时间范围筛选" title="按时间范围筛选"><CalendarDays :size="14" /></ElButton></template>
              <form class="logs-time-filter" @submit.prevent="applyTimeRange">
                <label for="logs-from">开始时间</label>
                <ElDatePicker id="logs-from" v-model="timeDraft.from" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择开始时间" style="width: 100%" />
                <label for="logs-to">结束时间</label>
                <ElDatePicker id="logs-to" v-model="timeDraft.to" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择结束时间" style="width: 100%" />
                <p class="text-xs text-muted-foreground">采用本地时间，结束时间不包含在结果中。</p>
                <p v-if="timeError" role="alert" class="text-xs text-destructive">{{ timeError }}</p>
                <div class="flex justify-end gap-2"><ElButton size="small" @click="clearTimeRange">清除时间</ElButton><ElButton size="small" type="primary" native-type="submit">应用时间</ElButton></div>
              </form>
            </ElPopover>
          </span>
        </template>
        <template #header-action>
          <span class="logs-column-heading">事件
            <ElPopover v-model:visible="actionOpen" trigger="click" placement="bottom-start" :width="260">
              <template #reference><ElButton size="small" :type="applied.action ? 'primary' : undefined" :plain="!!applied.action" :aria-pressed="!!applied.action" :aria-label="applied.action ? `按事件类型筛选：${auditActions[applied.action] || applied.action}` : '按事件类型筛选'" title="按事件类型筛选"><Filter :size="14" /></ElButton></template>
              <label class="logs-filter-label" for="logs-action">{{ category === 'LOGIN' ? '登录事件' : '操作类型' }}</label>
              <ElSelect id="logs-action" v-model="form.action" filterable clearable placeholder="全部类型" @change="actionOpen = false; search()">
                <ElOption value="" label="全部类型" />
                <ElOption v-for="[value, label] in actionOptions" :key="value" :value="value" :label="label" />
              </ElSelect>
            </ElPopover>
          </span>
        </template>
        <template #header-result>
          <span class="logs-column-heading">结果
            <ElPopover v-model:visible="resultOpen" trigger="click" placement="bottom-start" :width="200">
              <template #reference><ElButton size="small" :type="applied.result ? 'primary' : undefined" :plain="!!applied.result" :aria-pressed="!!applied.result" :aria-label="applied.result ? `按结果筛选：${applied.result === 'SUCCESS' ? '成功' : '失败'}` : '按结果筛选'" title="按结果筛选"><Filter :size="14" /></ElButton></template>
              <label class="logs-filter-label" for="logs-result">结果</label>
              <ElSelect id="logs-result" v-model="form.result" placeholder="全部结果" @change="resultOpen = false; search()">
                <ElOption v-for="option in resultOptions" :key="option.value" :value="option.value" :label="option.label" />
              </ElSelect>
            </ElPopover>
          </span>
        </template>
      </AuditLogTable>
    </section>
  </section>
</template>

<style scoped>
.logs-page { padding: 8px 20px 16px; gap: 9px; }
.logs-toolbar, .logs-actions, .logs-heading { display: flex; align-items: center; gap: 9px; min-width: 0; }
.logs-toolbar { justify-content: space-between; flex-wrap: wrap; }
.logs-heading h1 { font-size: 24px; font-weight: 650; white-space: nowrap; }
.logs-heading > span { font-size: 13px; color: hsl(var(--muted-foreground)); }
.logs-actions { justify-content: flex-end; }
.logs-actions > .el-button + .el-button { margin-left: 0; }
.logs-tabs :deep(.el-tabs__header) { margin: 0; }
.logs-tabs :deep(.el-tabs__content) { display: none; }
.logs-workspace { display: flex; flex: 1; min-width: 0; min-height: 0; overflow: hidden; border: 1px solid hsl(var(--border)); border-radius: 6px; background: hsl(var(--card)); }
.logs-workspace > :deep(.data-table) { flex: 1; min-width: 0; min-height: 0; }
.logs-column-heading { display: inline-flex; align-items: center; gap: 5px; white-space: nowrap; }
.logs-column-heading > .el-button { margin-left: 0; padding-inline: 6px; }
.logs-time-filter { display: grid; gap: 8px; font-size: 12px; }
.logs-filter-label { display: block; margin-bottom: 8px; font-size: 12px; }
@media (max-width: 700px) {
  .logs-page { padding: 8px; }
  .logs-toolbar { gap: 7px; }
  .logs-actions { width: 100%; gap: 4px; }
  .logs-actions > .compact-search { flex: 1; }
}
</style>
