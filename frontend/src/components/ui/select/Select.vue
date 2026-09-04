<script setup lang="ts">
import { SelectRoot, type SelectRootEmits, type SelectRootProps, useForwardPropsEmits } from 'reka-ui'
const props = defineProps<SelectRootProps>()
const emit = defineEmits<SelectRootEmits>()
const forwarded = useForwardPropsEmits(props, emit)
</script>
<template><SelectRoot v-bind="forwarded"><slot /></SelectRoot></template>
