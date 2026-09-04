<script setup lang="ts">
import { cn } from '@/lib/utils'
const props = defineProps<{ class?: string }>()
</script>
<template><div :class="cn('mt-auto flex flex-col gap-2 p-5 sm:flex-row sm:justify-end', props.class)"><slot /></div></template>
