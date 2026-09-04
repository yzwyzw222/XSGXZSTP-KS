<script setup lang="ts">
import { cn } from '@/lib/utils'
const props = defineProps<{ class?: string }>()
</script>
<template><thead :class="cn('[&_tr]:border-b bg-table-header-bg', props.class)"><slot /></thead></template>
