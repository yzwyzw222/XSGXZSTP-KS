<script setup lang="ts">
import { cn } from '@/lib/utils'
import { ChevronDown } from 'lucide-vue-next'
import { SelectIcon, SelectTrigger, type SelectTriggerProps, SelectValue } from 'reka-ui'
import { computed } from 'vue'
const props = withDefaults(defineProps<SelectTriggerProps & { class?: string; placeholder?: string; size?: 'sm' | 'default' }>(), { size: 'default' })
const delegated = computed(() => { const { class: _c, placeholder, size, ...rest } = props; return rest })
</script>
<template>
  <SelectTrigger
    v-bind="delegated"
    :class="cn(
      'flex w-full items-center justify-between whitespace-nowrap rounded-md border border-input bg-input-background px-3 py-2 text-sm shadow-sm ring-offset-background transition-colors',
      'placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring/60 focus:border-ring disabled:cursor-not-allowed disabled:opacity-50',
      'data-[state=open]:ring-2 data-[state=open]:ring-ring/40 [&>span]:truncate text-start',
      size === 'sm' ? 'h-8' : 'h-9',
      props.class,
    )"
  >
    <SelectValue :placeholder="placeholder" />
    <SelectIcon as-child><ChevronDown class="size-4 opacity-50" /></SelectIcon>
  </SelectTrigger>
</template>
