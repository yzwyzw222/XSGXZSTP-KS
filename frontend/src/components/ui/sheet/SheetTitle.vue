<script setup lang="ts">
import { DialogTitle, type DialogTitleProps } from 'reka-ui'
import { cn } from '@/lib/utils'
const props = defineProps<DialogTitleProps & { class?: string }>()
</script>
<template><DialogTitle :class="cn('text-base font-semibold text-foreground', props.class)"><slot /></DialogTitle></template>
