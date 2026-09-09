<script setup lang="ts">
import { cn } from '@/lib/utils'
const props = defineProps<{
  title: string
  description?: string
  class?: string
  divided?: boolean
}>()
</script>
<template>
  <header
    :class="cn('page-header', props.divided && 'page-header--divided', props.class)"
  >
    <div class="min-w-0 space-y-1.5">
      <h1 class="page-header__title">{{ title }}</h1>
      <p v-if="description" class="page-header__description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions || $slots.stamp" class="page-header__tools">
      <div v-if="$slots.stamp"><slot name="stamp" /></div>
      <div v-if="$slots.actions" class="flex flex-wrap items-center gap-2"><slot name="actions" /></div>
    </div>
  </header>
</template>
