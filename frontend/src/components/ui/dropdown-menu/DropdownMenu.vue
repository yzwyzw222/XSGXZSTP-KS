<script setup lang="ts">
import { DropdownMenuRoot, type DropdownMenuRootEmits, type DropdownMenuRootProps, useForwardPropsEmits } from 'reka-ui'

// 默认非 modal：modal 菜单会锁定 body 滚动并附加滚动条宽度补偿，
// 在存在经典滚动条的浏览器中会造成主内容区横向位移（收窄）。下拉菜单无需焦点陷阱。
const props = withDefaults(defineProps<DropdownMenuRootProps>(), { modal: false })
const emit = defineEmits<DropdownMenuRootEmits>()
const forwarded = useForwardPropsEmits(props, emit)
</script>
<template><DropdownMenuRoot v-bind="forwarded"><slot /></DropdownMenuRoot></template>
