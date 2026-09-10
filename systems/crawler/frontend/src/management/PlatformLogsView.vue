<script setup lang="ts">
import { onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { ElButton, ElInput, ElSelect, ElOption } from 'element-plus'
import { PageHeader, PanelSection } from '@/components/business'
import { getPlatformLogs, type PlatformLogPage } from './logs'
import { instantRange } from '@/utils/date'
import { formatDateTime } from '@/utils/format'

const systems: Record<string, string> = { portal: '统一门户', relation: '学术关系分析', crawler: '成果采集与可视化' }
const form = reactive({ system: '', username: '', result: '', from: '', to: '' })
const data = ref<PlatformLogPage>({ items: [], page: 0, totalPages: 0, totalElements: 0, retentionLimit: 10000 })
const loading = ref(false)
const error = ref('')
let controller: AbortController | undefined
let applied: Record<string, string> = {}

/** 筛选提交后再翻页，取消旧查询以防止较慢响应覆盖当前结果。 */
async function load(page = 0, search = false) {
  if (search) {
    try {
      const range = instantRange(form.from || null, form.to || null)
      applied = { system: form.system, username: form.username.trim(), result: form.result, from: range.from || '', to: range.to || '' }
    }
    catch (failure) { error.value = failure instanceof Error ? failure.message : '时间范围无效。'; return }
  }
  controller?.abort()
  const current = controller = new AbortController()
  loading.value = true
  error.value = ''
  try { const result = await getPlatformLogs(applied, page, current.signal); if (!current.signal.aborted) data.value = result }
  catch (failure) { if (!current.signal.aborted) error.value = failure instanceof Error ? failure.message : '日志加载失败。' }
  finally { if (!current.signal.aborted) loading.value = false }
}
onMounted(() => load())
onBeforeUnmount(() => controller?.abort())
</script>
<template>
  <section class="page-stack">
    <PageHeader title="平台日志管理" description="统一查看门户及两个子系统的接口访问、操作结果与耗时。">
      <template #actions><RouterLink to="/audits">登录与采集后台审计</RouterLink></template>
    </PageHeader>
    <PanelSection title="全部系统日志">
      <form class="log-filters" @submit.prevent="load(0, true)">
        <label>系统<ElSelect v-model="form.system" aria-label="系统" placeholder="全部系统"><ElOption label="全部系统" value="" /><ElOption v-for="(label, key) in systems" :key="key" :label="label" :value="key" /></ElSelect></label>
        <label>用户<ElInput v-model="form.username" maxlength="64" aria-label="用户" clearable /></label>
        <label>结果<ElSelect v-model="form.result" aria-label="结果" placeholder="全部结果"><ElOption label="全部结果" value="" /><ElOption label="成功" value="SUCCESS" /><ElOption label="失败" value="FAILURE" /></ElSelect></label>
        <label>开始时间<input v-model="form.from" type="datetime-local" /></label>
        <label>结束时间<input v-model="form.to" type="datetime-local" /></label>
        <ElButton native-type="submit" type="primary" :loading="loading">查询</ElButton>
      </form>
      <p v-if="error" role="alert" class="log-error">{{ error }} <ElButton @click="load(data.page)">重试</ElButton></p>
      <div class="log-table" :aria-busy="loading">
        <table><thead><tr><th>时间</th><th>系统</th><th>用户</th><th>请求</th><th>结果</th><th>耗时</th></tr></thead>
          <tbody><tr v-for="entry in data.items" :key="entry.id"><td>{{ formatDateTime(entry.createdAt) }}</td><td>{{ systems[entry.system] || entry.system }}</td><td>{{ entry.username }}</td><td><code>{{ entry.method }} {{ entry.resource }}</code></td><td :class="{ 'log-error': entry.status >= 400 }">{{ entry.status < 400 ? '成功' : '失败' }} · {{ entry.status }}</td><td>{{ entry.durationMs }} ms</td></tr></tbody>
        </table>
        <p v-if="!loading && !error && !data.items.length">当前筛选范围暂无日志。</p>
      </div>
      <footer class="log-pagination"><span>共 {{ data.totalElements }} 条 · 第 {{ data.page + 1 }} / {{ Math.max(1, data.totalPages) }} 页</span><ElButton :disabled="loading || data.page === 0" @click="load(data.page - 1)">上一页</ElButton><ElButton :disabled="loading || data.page + 1 >= data.totalPages" @click="load(data.page + 1)">下一页</ElButton></footer>
      <p class="log-note">保存最近 {{ data.retentionLimit }} 条平台请求记录；后台任务及历史登录明细请查看“登录与采集后台审计”。</p>
    </PanelSection>
  </section>
</template>
<style scoped>
.log-filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: end; margin-bottom: 20px; }
.log-filters label { display: grid; gap: 6px; width: 180px; }
.log-filters input { min-height: 32px; padding: 5px; border: 1px solid var(--border); background: var(--background); color: var(--foreground); border-radius: 4px; }
.log-table { overflow: auto; } table { width: 100%; border-collapse: collapse; text-align: left; } th, td { padding: 14px 10px; border-bottom: 1px solid var(--border); white-space: nowrap; }
.log-pagination { display: flex; align-items: center; gap: 12px; margin-top: 20px; flex-wrap: wrap; }
.log-error { color: #ff7c99; } .log-note { margin-top: 16px; color: var(--muted-foreground); }
</style>
