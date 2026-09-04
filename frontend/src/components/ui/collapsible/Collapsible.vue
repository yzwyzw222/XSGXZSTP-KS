<script setup lang="ts">
import { CollapsibleRoot, type CollapsibleRootEmits, type CollapsibleRootProps, useForwardPropsEmits } from 'reka-ui'
const props = defineProps<CollapsibleRootProps>()
const emit = defineEmits<CollapsibleRootEmits>()
const forwarded = useForwardPropsEmits(props, emit)
</script>
<template><CollapsibleRoot v-bind="forwarded"><slot /></CollapsibleRoot></template>
