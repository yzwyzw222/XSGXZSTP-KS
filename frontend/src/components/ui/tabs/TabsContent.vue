<script setup lang="ts">
import { cn } from '@/lib/utils'
import { TabsContent, type TabsContentProps } from 'reka-ui'
import { computed } from 'vue'
const props = defineProps<TabsContentProps & { class?: string }>()
const delegated = computed(() => {
  const { class: _c, ...rest } = props
  return rest
})
</script>

<template>
  <TabsContent
    v-bind="delegated"
    :class="cn('mt-3 ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring', props.class)"
  >
    <slot />
  </TabsContent>
</template>
