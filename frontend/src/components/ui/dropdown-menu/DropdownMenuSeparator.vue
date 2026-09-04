<script setup lang="ts">
import { cn } from '@/lib/utils'
import { DropdownMenuSeparator, type DropdownMenuSeparatorProps } from 'reka-ui'
const props = defineProps<DropdownMenuSeparatorProps & { class?: string }>()
</script>
<template><DropdownMenuSeparator v-bind="props" :class="cn('-mx-1 my-1 h-px bg-border', props.class)" /></template>
