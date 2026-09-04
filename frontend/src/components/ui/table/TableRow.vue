<script setup lang="ts">
import { cn } from '@/lib/utils'
const props = defineProps<{ class?: string }>()
</script>
<template><tr :class="cn('border-b border-table-border transition-colors hover:bg-table-row-hover data-[state=selected]:bg-accent', props.class)"><slot /></tr></template>
