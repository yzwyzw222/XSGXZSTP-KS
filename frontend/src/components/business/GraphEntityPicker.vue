<script setup lang="ts">
import { ElButton, ElInput, ElInputNumber } from 'element-plus'
import { onBeforeUnmount, ref, watch } from 'vue'

import { toErrorMessage } from '@/services/api'
import { findGraphEntities, type GraphEntityOption } from '@/services/graph-lookup'
import { useSessionStore } from '@/stores/session'
import type { GraphNodeType } from '@/types/api'

const props = defineProps<{ type: GraphNodeType; label: string; modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const sessionStore = useSessionStore()
const { hasPermission } = sessionStore

const query = ref('')
const options = ref<GraphEntityOption[]>([])
const loading = ref(false)
const searched = ref(false)
const error = ref('')
let requestVersion = 0

/** 输入或类型变化使旧请求失效，防止把上一类实体的ID带入新查询。 */
function resetSearch(): void {
  requestVersion++
  options.value = []
  loading.value = false
  searched.value = false
  error.value = ''
}

watch(query, resetSearch, { flush: 'sync' })
watch(() => props.type, () => {
  resetSearch()
  emit('update:modelValue', '')
})
// 卸载后到达的响应必须被丢弃，避免写入已销毁组件的状态。
onBeforeUnmount(() => { requestVersion++ })

async function search(): Promise<void> {
  resetSearch()
  if (!query.value.trim()) {
    error.value = '请先输入名称或论文标题'
    return
  }
  const version = requestVersion
  loading.value = true
  try {
    const result = await findGraphEntities(props.type, query.value)
    if (version !== requestVersion) return
    options.value = result
    searched.value = true
  } catch (cause) {
    if (version === requestVersion) error.value = toErrorMessage(cause)
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

/** 同名结果保留独立ID，只有用户明确选择后才写入中心。 */
function select(option: GraphEntityOption): void {
  query.value = option.label
  emit('update:modelValue', String(option.id))
}
</script>

<template>
  <div class="min-w-0 space-y-2">
    <div class="grid grid-cols-1 gap-2 sm:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)] sm:items-end">
      <label v-if="hasPermission('CATALOG_READ')" class="block space-y-1.5 text-sm font-medium text-muted-foreground">
        <span>{{ label }}名称</span>
        <span class="flex gap-2">
          <ElInput
            v-model="query"
            :aria-label="`${label}名称`"
            :maxlength="200"
            placeholder="输入名称或论文标题"
            clearable
            @keydown.enter.prevent="search"
          />
          <ElButton plain :loading="loading" :aria-label="`查找${label}`" @click="search">查找</ElButton>
        </span>
      </label>
      <label class="block space-y-1.5 text-xs text-muted-foreground">
        <span>{{ label }}业务ID</span>
        <ElInputNumber
          :model-value="modelValue ? Number(modelValue) : undefined"
          :min="1"
          :max="Number.MAX_SAFE_INTEGER"
          :step="1"
          :precision="0"
          controls-position="right"
          style="width: 100%"
          placeholder="选择名称后自动填入，也可直接输入"
          @update:model-value="emit('update:modelValue', String($event ?? ''))"
        />
      </label>
    </div>
    <p v-if="error" role="alert" class="text-xs text-destructive">{{ error }}</p>
    <p v-else-if="searched && !options.length" role="status" class="text-xs text-muted-foreground">没有匹配结果，请调整名称。</p>
    <ul
      v-if="options.length"
      :aria-label="`${label}候选`"
      class="max-h-52 overflow-y-auto rounded-md border border-border bg-background"
    >
      <li v-for="option in options" :key="option.id">
        <button
          type="button"
          class="w-full space-y-1 px-3 py-2 text-left transition-colors hover:bg-accent focus-visible:bg-accent"
          @click="select(option)"
        >
          <span class="block truncate text-sm font-medium">{{ option.label }}</span>
          <span class="block truncate text-xs text-muted-foreground">#{{ option.id }} · {{ option.description }}</span>
        </button>
      </li>
    </ul>
  </div>
</template>
