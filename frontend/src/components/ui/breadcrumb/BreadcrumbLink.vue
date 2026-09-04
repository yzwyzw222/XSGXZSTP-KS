<script setup lang="ts">
import { cn } from '@/lib/utils'
import { Primitive, type PrimitiveProps } from 'reka-ui'
const props = withDefaults(defineProps<PrimitiveProps & { class?: string }>(), { as: 'a' })
</script>
<template><Primitive :as="props.as" :as-child="props.asChild" :class="cn('hover:text-foreground transition-colors', props.class)"><slot /></Primitive></template>
