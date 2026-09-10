<script setup lang="ts">
import { ElButton, ElInput, ElTag } from 'element-plus'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { sourceApi } from '@/services/business'
import { ApiError, toErrorMessage } from '@/services/api'
import type { SourceEntity, SourceEntityKind } from '@/types/api'

const props = defineProps<{ id: string; sourceId: number; kind: SourceEntityKind; modelValue: string[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: string[]]; ready: [value: boolean] }>()
const label = computed(() => props.kind === 'authors' ? '作者' : '机构')
const query = ref('')
const candidates = ref<SourceEntity[]>([])
const known = ref(new Map<string, SourceEntity>())
const searching = ref(false)
const resolving = ref(false)
const searched = ref(false)
const searchError = ref('')
const resolveError = ref('')
let timer: ReturnType<typeof setTimeout> | undefined
let searchController: AbortController | undefined
let resolveController: AbortController | undefined
let searchVersion = 0
let resolveVersion = 0

function key(id: string): string { return id.replace(/^https?:\/\/openalex\.org\//, '') }
const unresolved = computed(() => props.modelValue.filter((id) => !known.value.has(key(id))))
const ready = computed(() => !query.value.trim() && !resolving.value && unresolved.value.length === 0)
watch(ready, (value) => emit('ready', value), { immediate: true, flush: 'sync' })

function stopSearch(): void {
  clearTimeout(timer)
  searchController?.abort()
  searchVersion++
  searching.value = false
}

/** 输入仍待选择时阻止提交；过期搜索不得覆盖新候选。 */
function queueSearch(): void {
  stopSearch()
  candidates.value = []
  searched.value = false
  searchError.value = ''
  if (!query.value.trim()) return
  searching.value = true
  timer = setTimeout(() => { void search() }, 300)
}

async function search(): Promise<void> {
  stopSearch()
  const text = query.value.trim()
  if (!text) return
  const version = searchVersion
  searchController = new AbortController()
  searching.value = true
  searchError.value = ''
  try {
    const result = await sourceApi.entities(props.sourceId, props.kind, text, searchController.signal)
    if (version !== searchVersion) return
    if (!Array.isArray(result)) throw new ApiError('名称响应格式无效，请重试', 502)
    candidates.value = result
    searched.value = true
  } catch (error) {
    if (version === searchVersion) searchError.value = toErrorMessage(error)
  } finally {
    if (version === searchVersion) searching.value = false
  }
}

/** 已有任务一次回显最多50个名称，缺失项保留原值并明确提示。 */
async function resolveNames(): Promise<void> {
  resolveController?.abort()
  const version = ++resolveVersion
  const ids = unresolved.value.map(key)
  resolveError.value = ''
  resolving.value = ids.length > 0
  if (!ids.length) return
  resolveController = new AbortController()
  try {
    const result = await sourceApi.resolveEntities(props.sourceId, props.kind, ids, resolveController.signal)
    if (version !== resolveVersion) return
    if (!Array.isArray(result)) throw new ApiError('名称响应格式无效，请重试', 502)
    for (const entity of result) known.value.set(entity.id, entity)
    if (unresolved.value.length) resolveError.value = `部分${label.value}名称未能读取，请重试或移除后重新选择。原筛选条件已保留。`
  } catch (error) {
    if (version === resolveVersion) resolveError.value = toErrorMessage(error)
  } finally {
    if (version === resolveVersion) resolving.value = false
  }
}

function selected(entity: SourceEntity): boolean { return props.modelValue.some((id) => key(id) === entity.id) }
function choose(entity: SourceEntity): void {
  if (selected(entity) || props.modelValue.length >= 50) return
  known.value.set(entity.id, entity)
  emit('update:modelValue', [...props.modelValue, entity.id])
  query.value = ''
}
function remove(id: string): void { emit('update:modelValue', props.modelValue.filter((value) => value !== id)) }

watch(query, queueSearch)
watch(() => [props.sourceId, props.kind] as const, () => {
  stopSearch()
  query.value = ''
  candidates.value = []
  known.value = new Map()
  void resolveNames()
}, { immediate: true })
watch(() => props.modelValue, () => {
  const selectedKeys = new Set(props.modelValue.map(key))
  for (const id of known.value.keys()) if (!selectedKeys.has(id)) known.value.delete(id)
  void resolveNames()
}, { deep: true })
onBeforeUnmount(() => {
  stopSearch()
  resolveVersion++
  resolveController?.abort()
})
</script>

<template>
  <div class="grid min-w-0 gap-2">
    <div v-if="modelValue.length" class="flex flex-wrap gap-1" :aria-label="`已选${label}`">
      <ElTag v-for="value in modelValue" :key="value" closable class="max-w-full"
        :title="known.get(key(value))?.hint" @close="remove(value)">
        {{ known.get(key(value))?.displayName ?? (resolving ? `正在读取${label}名称…` : `${label}名称未能读取`) }}
      </ElTag>
    </div>
    <ElInput :id="id" v-model="query" clearable :maxlength="200" :placeholder="`输入${label}名称搜索，可多选`"
      :aria-describedby="`${id}-hint`" @keydown.enter.prevent="search" />
    <p :id="`${id}-hint`" class="text-xs text-muted-foreground">
      从候选中选择后生效，最多50项。{{ kind === 'authors' ? '请结合所属机构区分同名作者。' : '可尝试机构的英文名或简称。' }}
    </p>
    <p v-if="query.trim()" class="text-xs text-muted-foreground">请选中候选，或清空搜索文字后保存。</p>
    <p v-if="searching || resolving" role="status" class="text-xs text-muted-foreground">{{ resolving ? '正在读取已选名称…' : '正在搜索…' }}</p>
    <div v-if="resolveError" role="alert" class="text-xs text-destructive">
      {{ resolveError }} <ElButton link :disabled="resolving" @click="resolveNames">重试读取名称</ElButton>
    </div>
    <div v-if="searchError" role="alert" class="text-xs text-destructive">
      {{ searchError }} <ElButton link :disabled="searching" @click="search">重试搜索</ElButton>
    </div>
    <ul v-if="candidates.length" :aria-label="`${label}搜索结果`" class="max-h-56 overflow-y-auto rounded border border-border">
      <li v-for="entity in candidates" :key="entity.id">
        <button type="button" class="grid w-full gap-1 px-3 py-2 text-left hover:bg-muted focus-visible:bg-muted disabled:opacity-50"
          :disabled="selected(entity) || modelValue.length >= 50" @click="choose(entity)">
          <span class="break-words">{{ entity.displayName }}{{ selected(entity) ? '（已选）' : '' }}</span>
          <span class="text-xs text-muted-foreground">{{ entity.hint || '暂无所属机构或地区信息' }}<template v-if="entity.worksCount != null"> · {{ entity.worksCount }} 项成果</template></span>
        </button>
      </li>
    </ul>
    <p v-else-if="searched && !searching && !searchError && query.trim()" role="status" class="text-xs text-muted-foreground">未找到匹配名称，请尝试其他名称、英文名或简称。</p>
  </div>
</template>
