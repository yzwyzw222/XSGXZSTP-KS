<script setup lang="ts">
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
const props = withDefaults(defineProps<{ variant?: 'table' | 'cards' | 'metrics' | 'text'; rows?: number; class?: string }>(), { variant: 'table', rows: 5 })
</script>
<template>
  <div :class="cn('w-full', props.class)" aria-busy="true" aria-label="正在加载">
    <template v-if="variant === 'metrics'">
      <div class="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Skeleton v-for="i in 4" :key="i" class="h-24 rounded-xl" />
      </div>
    </template>
    <template v-else-if="variant === 'cards'">
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <Skeleton v-for="i in rows" :key="i" class="h-32 rounded-xl" />
      </div>
    </template>
    <template v-else-if="variant === 'text'">
      <div class="space-y-2">
        <Skeleton v-for="i in rows" :key="i" class="h-4" :class="i % 2 ? 'w-full' : 'w-2/3'" />
      </div>
    </template>
    <template v-else>
      <div class="space-y-2">
        <Skeleton class="h-9 w-full rounded-md" />
        <Skeleton v-for="i in rows" :key="i" class="h-11 w-full rounded-md" />
      </div>
    </template>
  </div>
</template>
