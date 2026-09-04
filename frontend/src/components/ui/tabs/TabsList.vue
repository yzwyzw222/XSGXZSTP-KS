<script setup lang="ts">
import { cn } from '@/lib/utils'
import { TabsList, type TabsListProps } from 'reka-ui'
const props = defineProps<TabsListProps & { class?: string }>()
</script>
<template>
  <TabsList :class="cn('inline-flex items-center justify-center rounded-lg bg-muted p-1 text-muted-foreground gap-1', props.class)"><slot /></TabsList>
</template>
