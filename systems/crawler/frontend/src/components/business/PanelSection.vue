<script setup lang="ts">
import { cn } from '@/lib/utils'
const props = withDefaults(defineProps<{
  title?: string
  subtitle?: string
  class?: string
  bodyClass?: string
  padded?: boolean
}>(), { padded: true })
</script>
<template>
  <section :class="cn('panel-section', props.class)">
    <header
      v-if="title || $slots.actions || $slots.title"
      class="panel-section__header"
    >
      <div class="min-w-0 max-w-full space-y-0.5">
        <slot name="title">
          <h2 class="panel-section__title">{{ title }}</h2>
        </slot>
        <p v-if="subtitle" class="panel-section__subtitle">{{ subtitle }}</p>
      </div>
      <div v-if="$slots.actions" class="flex max-w-full flex-wrap items-center gap-2"><slot name="actions" /></div>
    </header>
    <div :class="cn('panel-section__body', props.padded && 'panel-section__body--padded', props.bodyClass)"><slot /></div>
  </section>
</template>
