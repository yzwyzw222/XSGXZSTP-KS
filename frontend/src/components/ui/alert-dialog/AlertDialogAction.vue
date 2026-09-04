<script setup lang="ts">
import { AlertDialogAction, type AlertDialogActionProps } from 'reka-ui'
import { cn } from '@/lib/utils'
import { buttonVariants } from '@/components/ui/button'
const props = defineProps<AlertDialogActionProps & { class?: string }>()
</script>
<template><AlertDialogAction :class="cn(buttonVariants(), props.class)"><slot /></AlertDialogAction></template>
