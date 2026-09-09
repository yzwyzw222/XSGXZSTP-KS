<script setup lang="ts">
import { ElEmpty } from 'element-plus'
import { Inbox } from 'lucide-vue-next'
import type { Component } from 'vue'

import { cn } from '@/lib/utils'

const props = defineProps<{ title: string; description?: string; icon?: Component; class?: string }>()
</script>

<template>
  <ElEmpty :class="cn('aacv-empty', props.class)" :image-size="32">
    <template #image>
      <component :is="icon || Inbox" class="size-8 text-muted-foreground" :stroke-width="1.5" aria-hidden="true" />
    </template>
    <template #description>
      <strong class="block text-sm font-medium text-foreground">{{ title }}</strong>
      <p v-if="description" class="mx-auto mt-1 max-w-md text-sm text-muted-foreground">{{ description }}</p>
    </template>
    <div v-if="$slots.action" class="mt-1"><slot name="action" /></div>
  </ElEmpty>
</template>
