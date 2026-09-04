<script setup lang="ts">
import { cn } from '@/lib/utils'
import type { Component } from 'vue'
const props = defineProps<{ title: string; description?: string; icon?: Component; class?: string }>()
</script>
<template>
  <div :class="cn('flex flex-col items-center justify-center gap-3 px-6 py-14 text-center', props.class)">
    <component :is="icon" v-if="icon" class="size-10 text-muted-foreground/50" aria-hidden="true" />
    <strong class="text-base font-semibold text-foreground">{{ title }}</strong>
    <p v-if="description" class="max-w-md text-sm text-muted-foreground">{{ description }}</p>
    <div v-if="$slots.action" class="mt-1"><slot name="action" /></div>
  </div>
</template>
