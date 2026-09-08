<script setup lang="ts">
import { ElButton, ElOption, ElSelect } from 'element-plus'
import { reactive } from 'vue'

import GraphEntityPicker from '@/components/business/GraphEntityPicker.vue'
import { FilterField } from '@/components/business'
import type { GraphPathQuery } from '@/components/business/types'
import type { GraphNodeType } from '@/types/api'

const emit = defineEmits<{ (e: 'submit', query: GraphPathQuery): void }>()

// 配置隐藏时触发器宽度归零；固定枚举浮层宽度，避免其尺寸监听反复更新布局。
const selectPopperStyle = { width: '160px' }

const nodeTypes: Array<{ value: GraphNodeType; label: string }> = [
  { value: 'ACHIEVEMENT', label: '成果' },
  { value: 'AUTHOR', label: '作者' },
  { value: 'INSTITUTION', label: '机构' },
  { value: 'VENUE', label: '期刊/载体' },
  { value: 'TOPIC', label: '主题' },
]

const form = reactive<GraphPathQuery>({
  sourceType: 'AUTHOR',
  sourceId: '',
  targetType: 'TOPIC',
  targetId: '',
  maxHops: '6',
})
</script>

<template>
  <div class="space-y-4">
    <div class="grid gap-4 lg:grid-cols-2">
      <div class="space-y-3 rounded-lg border border-border bg-muted/20 p-4">
        <h2 class="text-sm font-medium">起点节点</h2>
        <div class="grid gap-3 sm:grid-cols-[minmax(0,1fr)_minmax(0,1.6fr)] sm:items-start">
          <FilterField label="起点类型">
            <ElSelect v-model="form.sourceType" :popper-style="selectPopperStyle" placeholder="起点类型">
              <ElOption
                v-for="item in nodeTypes"
                :key="item.value"
                :value="item.value"
                :label="item.label"
              />
            </ElSelect>
          </FilterField>
          <GraphEntityPicker v-model="form.sourceId" :type="form.sourceType" label="起点" />
        </div>
      </div>
      <div class="space-y-3 rounded-lg border border-border bg-muted/20 p-4">
        <h2 class="text-sm font-medium">终点节点</h2>
        <div class="grid gap-3 sm:grid-cols-[minmax(0,1fr)_minmax(0,1.6fr)] sm:items-start">
          <FilterField label="终点类型">
            <ElSelect v-model="form.targetType" :popper-style="selectPopperStyle" placeholder="终点类型">
              <ElOption
                v-for="item in nodeTypes"
                :key="item.value"
                :value="item.value"
                :label="item.label"
              />
            </ElSelect>
          </FilterField>
          <GraphEntityPicker v-model="form.targetId" :type="form.targetType" label="终点" />
        </div>
      </div>
    </div>
    <div class="flex flex-wrap items-end justify-between gap-3 border-t border-border pt-4">
      <FilterField label="最大跳数" class="w-40">
        <!-- 后端硬限制：最短路径最多 6 跳 -->
        <ElSelect v-model="form.maxHops" :popper-style="selectPopperStyle" placeholder="选择跳数">
          <ElOption v-for="hops in 6" :key="hops" :value="String(hops)" :label="`${hops} 跳`" />
        </ElSelect>
      </FilterField>
      <ElButton type="primary" @click="emit('submit', { ...form })">查询路径</ElButton>
    </div>
  </div>
</template>
