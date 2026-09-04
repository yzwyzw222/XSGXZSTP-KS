<script setup lang="ts">
import { cn } from '@/lib/utils'
import { AvatarRoot } from 'reka-ui'
const props = defineProps<{ class?: string }>()
</script>
<template>
  <AvatarRoot :class="cn('relative flex size-9 shrink-0 overflow-hidden rounded-full', props.class)"><slot /></AvatarRoot>
</template>
