<script setup lang="ts">
import { ElSkeleton, ElSkeletonItem } from 'element-plus'

import { cn } from '@/lib/utils'

const props = withDefaults(defineProps<{
  variant?: 'table' | 'cards' | 'metrics' | 'text'
  rows?: number
  class?: string
}>(), { variant: 'table', rows: 5 })
</script>

<template>
  <!-- aria-busy + 可感知文本：加载状态不只依赖动画 -->
  <div :class="cn('w-full', props.class)" aria-busy="true" aria-label="正在加载">
    <ElSkeleton animated>
      <template #template>
        <div v-if="variant === 'metrics'" class="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <ElSkeletonItem v-for="i in 4" :key="i" variant="rect" style="height: 96px; border-radius: var(--radius-lg)" />
        </div>
        <div v-else-if="variant === 'cards'" class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <ElSkeletonItem
            v-for="i in rows"
            :key="i"
            variant="rect"
            style="height: 128px; border-radius: var(--radius-lg)"
          />
        </div>
        <div v-else-if="variant === 'text'" class="space-y-2">
          <ElSkeletonItem
            v-for="i in rows"
            :key="i"
            variant="text"
            :style="{ width: i % 2 ? '100%' : '66%' }"
          />
        </div>
        <div v-else class="space-y-2">
          <ElSkeletonItem variant="rect" style="width: 100%; height: 36px; border-radius: var(--radius-md)" />
          <ElSkeletonItem
            v-for="i in rows"
            :key="i"
            variant="rect"
            style="width: 100%; height: 44px; border-radius: var(--radius-md)"
          />
        </div>
      </template>
    </ElSkeleton>
  </div>
</template>
