<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { session } from '@/services/session'
import { graphFilterSchema, savedGraphQuerySchema, type GraphFilters, type SavedGraphQuery } from '@/utils/graph-query'

const props = defineProps<{ filters: GraphFilters }>()
const emit = defineEmits<{ restore: [filters: GraphFilters] }>()
const name = ref('')
const saved = ref<SavedGraphQuery[]>([])
const message = ref('')
const storageKey = computed(() => session.user ? `aacv-graph-queries-v1:${session.user.id}` : null)

watch(storageKey, (key) => {
  saved.value = []
  message.value = ''
  if (!key) return
  try {
    const value = localStorage.getItem(key)
    if (value) saved.value = savedGraphQuerySchema.parse(JSON.parse(value))
  } catch {
    message.value = '本机常用查询无法读取，可重新保存；不影响图谱查询。'
  }
}, { immediate: true })

/** 仅保存当前账号的查询条件，写入失败时保留内存中的原有列表。 */
function persist(next: SavedGraphQuery[]): boolean {
  if (!storageKey.value) return false
  try {
    localStorage.setItem(storageKey.value, JSON.stringify(next))
    saved.value = next
    return true
  } catch {
    message.value = '浏览器未允许保存，请检查本地存储权限或空间。'
    return false
  }
}

function save(): void {
  const parsed = graphFilterSchema.safeParse(props.filters)
  const label = name.value.trim()
  if (!label || !parsed.success) {
    message.value = '请填写名称，并确认中心节点、年份和查询上限有效。'
    return
  }
  const remaining = saved.value.filter((item) => item.name !== label)
  if (remaining.length >= 10) {
    message.value = '最多保存10个查询，请先移除一个。'
    return
  }
  if (persist([{ name: label, filters: parsed.data }, ...remaining])) {
    message.value = '已保存到当前账号的本机浏览器；同名查询已更新。'
    name.value = ''
  }
}

function remove(label: string): void {
  if (persist(saved.value.filter((item) => item.name !== label))) message.value = '已移除常用查询。'
}

function restore(item: SavedGraphQuery): void {
  emit('restore', graphFilterSchema.parse(item.filters))
}
</script>

<template>
  <details class="rounded-lg border border-border bg-muted/20 px-4">
    <summary class="cursor-pointer py-3 text-sm font-medium">常用查询 <span class="text-muted-foreground">{{ saved.length }}/10</span></summary>
    <div class="space-y-3 pb-4">
      <div class="flex flex-wrap gap-2">
        <Input v-model="name" class="min-w-0 flex-1" aria-label="常用查询名称" placeholder="给当前子图查询命名" :maxlength="40" @keydown.enter.prevent="save" />
        <Button variant="outline" :disabled="!storageKey" @click="save">保存当前查询</Button>
      </div>
      <p v-if="message" role="status" class="text-xs text-muted-foreground">{{ message }}</p>
      <p v-if="!saved.length" class="text-xs text-muted-foreground">保存后可一键重用筛选条件。仅保存在本机当前浏览器。</p>
      <ul v-else class="divide-y divide-border">
        <li v-for="item in saved" :key="item.name" class="flex items-center justify-between gap-2 py-2">
          <Button variant="link" class="min-w-0 truncate px-0" @click="restore(item)">{{ item.name }}</Button>
          <Button variant="ghost" size="sm" :aria-label="`移除${item.name}`" @click="remove(item.name)">移除</Button>
        </li>
      </ul>
    </div>
  </details>
</template>
