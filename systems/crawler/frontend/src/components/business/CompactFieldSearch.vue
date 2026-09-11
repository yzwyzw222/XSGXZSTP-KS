<script setup lang="ts">
import { ElButton, ElInput } from 'element-plus'
import { Search } from 'lucide-vue-next'
import EntitySuggestInput from './EntitySuggestInput.vue'
import type { CatalogCollection } from '@/types/api'
import { computed } from 'vue'

const props = defineProps<{
  fields: readonly { value: string; label: string; collection?: CatalogCollection }[]
  field: string
  modelValue: string
  entityId?: number
  loading?: boolean
}>()
const emit = defineEmits<{
  (e: 'update:field', value: string): void
  (e: 'update:modelValue', value: string): void
  (e: 'update:entityId', value: number | undefined): void
  (e: 'submit'): void
}>()
const selected = computed(() => props.fields.find(item => item.value === props.field) ?? props.fields[0]!)
</script>

<template>
  <div class="compact-search" role="search" aria-label="按字段搜索">
    <select :value="field" aria-label="搜索字段" @change="emit('update:field', ($event.target as HTMLSelectElement).value)">
      <option v-for="item in fields" :key="item.value" :value="item.value">{{ item.label }}</option>
    </select>
    <EntitySuggestInput v-if="selected.collection" :key="field" class="compact-search__input" :model-value="modelValue" :entity-id="entityId" :collection="selected.collection" :label="selected.label" :placeholder="`搜索${selected.label}`" @update:model-value="emit('update:modelValue', $event)" @update:entity-id="emit('update:entityId', $event)" @enter="emit('submit')" />
    <ElInput v-else class="compact-search__input" :model-value="modelValue" :aria-label="`${selected.label}搜索`" :placeholder="`搜索${selected.label}`" :maxlength="200" clearable @update:model-value="emit('update:modelValue', String($event ?? ''))" @keydown.enter="emit('submit')" />
    <ElButton :loading="loading" aria-label="搜索" title="搜索" @click="emit('submit')"><Search v-if="!loading" :size="15" /></ElButton>
  </div>
</template>

<style scoped>
.compact-search { display: flex; gap: 5px; width: min(100%, 365px); min-width: 0; align-items: center; }
.compact-search select { width: 78px; flex-shrink: 0; height: 32px; border: 1px solid hsl(var(--border)); border-radius: 4px; padding: 0 6px; background: hsl(var(--card)); color: hsl(var(--foreground)); font-size: 12px; }
.compact-search select:focus-visible { outline: 2px solid hsl(var(--primary)); outline-offset: 2px; }
.compact-search__input { min-width: 0; flex: 1; }
.compact-search > .el-button { padding-inline: 9px; margin-left: 0; }
</style>
