<script setup lang="ts">
import { PopoverRoot, type PopoverRootEmits, type PopoverRootProps, useForwardPropsEmits } from 'reka-ui'
const props = defineProps<PopoverRootProps>()
const emit = defineEmits<PopoverRootEmits>()
const forwarded = useForwardPropsEmits(props, emit)
</script>
<template><PopoverRoot v-bind="forwarded"><slot /></PopoverRoot></template>
