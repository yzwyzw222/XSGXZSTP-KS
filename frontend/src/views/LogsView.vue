<script setup lang="ts">
import { ElButton, ElDatePicker, ElInput, ElOption, ElSelect, ElTabPane, ElTabs } from 'element-plus'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'

import { PageHeader, PanelSection } from '@/components/business'
import AuditLogTable from '@/components/business/AuditLogTable.vue'
import ErrorState from '@/components/business/ErrorState.vue'
import { toErrorMessage } from '@/services/api'
import { getAudits } from '@/services/audits'
import type { AuditCategory, AuditFilter, AuditLog, PageResponse } from '@/types/api'
import { auditActions } from '@/utils/audit'
import { instantRange } from '@/utils/date'

const route = useRoute()
const router = useRouter()
const category = ref<AuditCategory>('OPERATION')
const narrow = useMediaQuery('(max-width: 767px)')
const filtersExpanded = ref(false)
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
  search()
}

/** 分类以路由查询参数为唯一来源，标签页只负责发起导航。 */
function onCategoryChange(value: string | number): void {
  void router.replace({ query: { ...route.query, category: String(value) } })
}

watch(() => route.query.category, (value) => {
  category.value = value === 'LOGIN' ? 'LOGIN' : 'OPERATION'
  form.action = ''
  logs.value = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
  search()
}, { immediate: true })

onBeforeUnmount(() => request?.abort())
</script>

<template>
  <section class="page-stack">
    <PageHeader title="日志管理" description="查询关键业务操作与账号登录活动，按时间、账号和结果追溯。" />

    <ElTabs :model-value="category" @tab-change="onCategoryChange">
      <ElTabPane label="操作日志" name="OPERATION" />
      <ElTabPane label="登录日志" name="LOGIN" />
    </ElTabs>

    <PanelSection
      :title="category === 'LOGIN' ? '登录日志' : '操作日志'"
      subtitle="时间范围采用本地时间，结束时间不包含在结果中。"
    >
      <template #actions><ElButton v-if="narrow" text :aria-expanded="filtersExpanded" @click="filtersExpanded = !filtersExpanded">{{ filtersExpanded ? '收起筛选' : '展开筛选' }}</ElButton></template>
      <form v-show="!narrow || filtersExpanded" class="logs-filters mb-3 grid items-end gap-3 sm:grid-cols-2 xl:grid-cols-4" @submit.prevent="search">
        <div class="grid gap-1.5 text-sm">
          <label class="font-medium text-muted-foreground" for="logs-username">账号</label>
          <ElInput
            id="logs-username"
            v-model="form.username"
            :maxlength="64"
            placeholder="输入账号关键字"
            clearable
          />
        </div>
        <div class="grid gap-1.5 text-sm">
          <label class="font-medium text-muted-foreground" for="logs-from">开始时间</label>
          <ElDatePicker
            id="logs-from"
            v-model="form.from"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择开始时间"
            style="width: 100%"
          />
        </div>
        <div class="grid gap-1.5 text-sm">
          <label class="font-medium text-muted-foreground" for="logs-to">结束时间</label>
          <ElDatePicker
            id="logs-to"
            v-model="form.to"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择结束时间"
            style="width: 100%"
          />
        </div>
        <div class="grid gap-1.5 text-sm">
          <label class="font-medium text-muted-foreground" for="logs-result">结果</label>
          <ElSelect id="logs-result" v-model="form.result" filterable>
            <ElOption
              v-for="option in resultOptions"
              :key="option.value"
              :value="option.value"
              :label="option.label"
            />
          </ElSelect>
        </div>
        <div class="grid gap-1.5 text-sm">
          <label class="font-medium text-muted-foreground" for="logs-action">操作类型</label>
          <ElSelect id="logs-action" v-model="form.action" filterable clearable placeholder="全部类型">
            <ElOption value="" label="全部类型" />
            <ElOption
              v-for="[value, label] in actionOptions"
              :key="value"
              :value="value"
              :label="label"
            />
          </ElSelect>
        </div>
        <div class="flex flex-wrap gap-2">
          <ElButton type="primary" native-type="submit" :loading="loading">查询</ElButton>
          <ElButton plain @click="reset">重置</ElButton>
          <ElButton text :disabled="loading" @click="load(logs.page)">刷新</ElButton>
        </div>
      </form>

      <ErrorState v-if="error" :message="error" retryable @retry="search" />
      <AuditLogTable fill
        v-else
        :items="logs.items"
        :page="logs.page"
        :size="logs.size"
        :total="logs.totalElements"
        :loading="loading"
        @update:page="load"
      />
    </PanelSection>
  </section>
</template>

<style scoped>
.logs-filters { max-height: 34dvh; overflow: auto; }
</style>
