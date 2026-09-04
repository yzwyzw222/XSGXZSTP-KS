<script setup lang="ts">
import { AlertDialogTitle, type AlertDialogTitleProps } from 'reka-ui'
import { cn } from '@/lib/utils'
const props = defineProps<AlertDialogTitleProps & { class?: string }>()
</script>
<template><AlertDialogTitle :class="cn('text-lg font-semibold', props.class)"><slot /></AlertDialogTitle></template>
