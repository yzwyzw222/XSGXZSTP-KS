<script setup lang="ts">
import { ElInput } from 'element-plus'
import { Search } from 'lucide-vue-next'
import { onBeforeUnmount, ref, watch } from 'vue'

import { toErrorMessage } from '@/services/api'
import { catalogApi } from '@/services/business'
import type { CatalogCollection, CatalogEntity } from '@/types/api'

const props = withDefaults(defineProps<{
  collection: CatalogCollection
  label: string
  modelValue: string
  entityId?: number
  /** text 模式把名称写回筛选值；id 模式把选中实体的规范ID写回筛选值。 */
  mode?: 'text' | 'id'
  placeholder?: string
}>(), { mode: 'text', placeholder: '输入名称检索后选择' })

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'update:entityId', value: number | undefined): void
  (e: 'select', entity: CatalogEntity): void
  (e: 'enter'): void
}>()

const text = ref(props.mode === 'text' ? props.modelValue : '')
const options = ref<CatalogEntity[]>([])
const listOpen = ref(false)
const loading = ref(false)
const error = ref('')
let searchTimer: number | undefined
let hideTimer: number | undefined
let requestVersion = 0

/** 外部重置或恢复筛选时同步显示文本；id 模式只在筛选值清空时清空文本。 */
watch(() => props.modelValue, (value) => {
  if (props.mode === 'text') {
    if (value !== text.value) text.value = value
  } else if (!value && text.value) {
    text.value = ''
  }
})

function onInput(value: string): void {
  emit('update:entityId', undefined)
  text.value = value
  if (props.mode === 'text') emit('update:modelValue', value)
  else if (props.modelValue !== '') emit('update:modelValue', '')
  scheduleSearch()
}

function scheduleSearch(): void {
  window.clearTimeout(searchTimer)
  requestVersion++
  options.value = []
  listOpen.value = false
  loading.value = false
  const keyword = text.value.trim()
  if (!keyword) {
    error.value = ''
    return
  }
  searchTimer = window.setTimeout(() => void search(keyword), 250)
}

async function search(keyword: string): Promise<void> {
  const version = ++requestVersion
  loading.value = true
  error.value = ''
  try {
    const page = await catalogApi.entities(props.collection, keyword, 0, 8)
    if (version !== requestVersion) return
    options.value = page.items
    listOpen.value = page.items.length > 0
  } catch (cause) {
    if (version !== requestVersion) return
    options.value = []
    listOpen.value = false
    error.value = toErrorMessage(cause)
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

function select(entity: CatalogEntity): void {
  window.clearTimeout(searchTimer)
  requestVersion++
  loading.value = false
  text.value = entity.displayName
  options.value = []
  listOpen.value = false
  error.value = ''
  emit('update:modelValue', props.mode === 'id' ? String(entity.id) : entity.displayName)
  emit('select', entity)
  emit('update:entityId', entity.id)
}

function onEnter(): void {
  if (listOpen.value && options.value.length === 1) select(options.value[0]!)
  else emit('enter')
}

function onFocus(): void {
  if (options.value.length) listOpen.value = true
}

function scheduleHide(): void {
  window.clearTimeout(hideTimer)
  hideTimer = window.setTimeout(() => { listOpen.value = false }, 150)
}

// 卸载时释放防抖定时器并作废在途请求，避免迟到响应写入已销毁组件。
onBeforeUnmount(() => {
  window.clearTimeout(searchTimer)
  window.clearTimeout(hideTimer)
  requestVersion++
})
</script>

<template>
  <div class="relative min-w-0">
    <Search
      class="pointer-events-none absolute left-3 top-1/2 z-10 size-4 -translate-y-1/2 text-muted-foreground"
      aria-hidden="true"
    />
    <ElInput
      :model-value="text"
      class="aacv-suggest-input"
      :placeholder="placeholder"
      :aria-label="`${label}名称检索`"
      :maxlength="200"
      @update:model-value="onInput(String($event ?? ''))"
      @focus="onFocus"
      @blur="scheduleHide"
      @keydown.enter.prevent="onEnter"
    />
    <ul
      v-if="listOpen && options.length"
      :aria-label="`${label}候选`"
      class="absolute z-30 mt-1 w-full overflow-hidden rounded-md border border-border bg-popover text-popover-foreground shadow-md"
    >
      <li v-for="option in options" :key="option.id">
        <button
          type="button"
          class="flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm transition-colors hover:bg-accent focus-visible:bg-accent"
          @mousedown.prevent
          @click="select(option)"
        >
          <span class="truncate">{{ option.displayName }}</span>
          <span class="shrink-0 text-xs tabular-nums text-muted-foreground">{{ option.achievementCount }} 成果</span>
        </button>
      </li>
    </ul>
    <p v-if="error" role="alert" class="mt-1 text-xs text-destructive">{{ error }}</p>
  </div>
</template>

<style scoped>
/* 为前置检索图标留出空间；只调整内边距，不改写 Element Plus 的结构样式。 */
.aacv-suggest-input :deep(.el-input__wrapper) {
  padding-left: 34px;
}
</style>
