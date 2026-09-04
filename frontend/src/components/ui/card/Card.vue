<script setup lang="ts">
import { cn } from '@/lib/utils'

const props = defineProps<{ class?: string }>()
</script>

<template>
  <div
    data-slot="card"
    :class="cn('bg-card text-card-foreground flex flex-col gap-4 rounded-xl border border-border py-5 shadow-sm', props.class)"
  >
    <slot />
  </div>
</template>
