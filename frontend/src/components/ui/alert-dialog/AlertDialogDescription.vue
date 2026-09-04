<script setup lang="ts">
import { AlertDialogDescription, type AlertDialogDescriptionProps } from 'reka-ui'
import { cn } from '@/lib/utils'
const props = defineProps<AlertDialogDescriptionProps & { class?: string }>()
</script>
<template><AlertDialogDescription :class="cn('text-sm text-muted-foreground', props.class)"><slot /></AlertDialogDescription></template>
