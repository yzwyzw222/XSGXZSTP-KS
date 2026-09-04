<script setup lang="ts">
import { ChevronRight } from 'lucide-vue-next'
const props = defineProps<{ class?: string }>()
</script>
<template><li role="presentation" aria-hidden="true" :class="props.class"><slot><ChevronRight class="size-3.5" /></slot></li></template>
