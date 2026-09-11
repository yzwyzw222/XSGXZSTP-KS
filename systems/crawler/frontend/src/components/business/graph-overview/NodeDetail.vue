<script setup lang="ts">
import { ElButton } from 'element-plus'
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import type { GraphNode } from '@/types/api'
import { parseGraphExtend } from '@/utils/graph-presentation'
import { nodeTarget } from '@/utils/graph'

const props = defineProps<{ node: GraphNode; loading: boolean }>()
const emit = defineEmits<{ explore: [] }>()
const fields = computed(() => Object.entries({ ...props.node.properties, ...parseGraphExtend(props.node.properties.extend_data, props.node.id) }).filter(([key]) => key !== 'extend_data'))
const fieldNames: Record<string, string> = { abstractText: '摘要', achievementType: '成果类型', publicationDate: '发表日期', doi: 'DOI' }
function format(value: unknown): string {
  if (value === undefined || value === null || value === '') return '--'
  return typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value)
}
</script>

<template>
  <div class="grid gap-4 break-words">
    <p>业务ID：{{ node.businessId }}</p>
    <p>完整名称：{{ node.label }}</p>
    <div class="flex flex-wrap gap-2">
      <ElButton type="primary" :disabled="loading" @click="emit('explore')">查看两跳子图</ElButton>
      <ElButton disabled title="当前未提供节点编辑接口">编辑节点</ElButton>
      <ElButton disabled title="当前未提供节点删除接口">删除节点</ElButton>
    </div>
    <RouterLink :to="nodeTarget(node) ?? '/catalog'" class="text-primary">查看目录记录</RouterLink>
    <p class="text-xs text-muted-foreground">节点增删改暂不可用，当前仅支持浏览已同步的业务数据。</p>
    <h3 class="font-medium">扩展字段</h3>
    <dl v-if="fields.length" class="grid gap-3">
      <div v-for="[key, value] in fields" :key="key"><dt class="text-muted-foreground">{{ fieldNames[key] ?? key }}</dt><dd class="whitespace-pre-wrap break-words">{{ format(value) }}</dd></div>
    </dl>
    <p v-else class="text-muted-foreground">--</p>
  </div>
</template>
