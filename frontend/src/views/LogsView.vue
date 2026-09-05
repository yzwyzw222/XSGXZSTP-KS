<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PageHeader, PanelSection } from '@/components/business'
import AuditLogTable from '@/components/business/AuditLogTable.vue'
import ErrorState from '@/components/business/ErrorState.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { FormItem, FormLabel } from '@/components/ui/form'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { getAudits } from '@/services/audits'
import { toErrorMessage } from '@/services/api'
import type { AuditCategory, AuditFilter, AuditLog, PageResponse } from '@/types/api'
import { auditActions } from '@/utils/audit'

const route = useRoute()
const router = useRouter()
const category = ref<AuditCategory>('OPERATION')
const form = reactive({ username: '', from: '', to: '', result: '', action: '' })
const applied = ref<AuditFilter>({})
const logs = ref<PageResponse<AuditLog>>({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const loading = ref(false)
const error = ref('')
let request: AbortController | undefined
const actionOptions = computed(() => Object.entries(auditActions).filter(([action]) =>
  ['LOGIN_SUCCEEDED', 'LOGIN_FAILED', 'LOGOUT'].includes(action) === (category.value === 'LOGIN')))

/** 取消旧请求，确保快速切换分类或筛选时只显示最后一次查询。 */
async function load(page = 0): Promise<void> {
  request?.abort()
  const current = request = new AbortController()
  loading.value = true
  error.value = ''
  try {
    const response = await getAudits(applied.value, page, 20, current.signal)
    if (!current.signal.aborted) logs.value = response
  } catch (failure) { if (!current.signal.aborted) error.value = toErrorMessage(failure) }
  finally { if (!current.signal.aborted) loading.value = false }
}

function search(): void {
  if (form.from && form.to && new Date(form.from) >= new Date(form.to)) {
    error.value = '开始时间必须早于结束时间'
    return
  }
  applied.value = { category: category.value, username: form.username.trim(),
    from: form.from ? new Date(form.from).toISOString() : undefined,
    to: form.to ? new Date(form.to).toISOString() : undefined,
    result: form.result as AuditFilter['result'], action: form.action }
  void load()
}

function reset(): void {
  Object.assign(form, { username: '', from: '', to: '', result: '', action: '' })
  search()
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
    <Tabs :model-value="category" @update:model-value="(value) => router.replace({ query: { category: String(value) } })">
      <TabsList><TabsTrigger value="OPERATION">操作日志</TabsTrigger><TabsTrigger value="LOGIN">登录日志</TabsTrigger></TabsList>
    </Tabs>
    <PanelSection :title="category === 'LOGIN' ? '登录日志' : '操作日志'" subtitle="时间范围采用本地时间，结束时间不包含在结果中。">
      <form class="mb-5 grid items-end gap-3 sm:grid-cols-2 xl:grid-cols-4" @submit.prevent="search">
        <FormItem><FormLabel for="logs-username">账号</FormLabel><Input id="logs-username" v-model="form.username" :maxlength="64" placeholder="输入账号关键字" /></FormItem>
        <FormItem><FormLabel for="logs-from">开始时间</FormLabel><Input id="logs-from" v-model="form.from" type="datetime-local" /></FormItem>
        <FormItem><FormLabel for="logs-to">结束时间</FormLabel><Input id="logs-to" v-model="form.to" type="datetime-local" /></FormItem>
        <FormItem><FormLabel for="logs-result">结果</FormLabel><select id="logs-result" v-model="form.result" class="h-9 w-full rounded-md border border-input bg-background px-3 text-sm"><option value="">全部结果</option><option value="SUCCESS">成功</option><option value="FAILURE">失败</option></select></FormItem>
        <FormItem><FormLabel for="logs-action">操作类型</FormLabel><select id="logs-action" v-model="form.action" class="h-9 w-full rounded-md border border-input bg-background px-3 text-sm"><option value="">全部类型</option><option v-for="[value, label] in actionOptions" :key="value" :value="value">{{ label }}</option></select></FormItem>
        <div class="flex gap-2"><Button type="submit" :disabled="loading">查询</Button><Button type="button" variant="outline" @click="reset">重置</Button><Button type="button" variant="ghost" :disabled="loading" @click="load(logs.page)">刷新</Button></div>
      </form>
      <ErrorState v-if="error" :message="error" retryable @retry="search" />
      <AuditLogTable v-else :items="logs.items" :page="logs.page" :size="logs.size" :total="logs.totalElements" :loading="loading" @update:page="load" />
    </PanelSection>
  </section>
</template>
