<script setup lang="ts">
import { cn } from '@/lib/utils'
import CountUpNumber from '@/components/CountUpNumber.vue'
import type { Component } from 'vue'
const props = withDefaults(defineProps<{
  label: string
  value: number | null
  note?: string
  icon?: Component
  tone?: 'blue' | 'cyan' | 'green' | 'violet' | 'amber' | 'rose'
  suffix?: string
  class?: string
}>(), { tone: 'blue' })
</script>
<template>
  <article :class="cn('stat-card', props.class)">
    <div class="stat-card__label">
      <span>{{ label }}</span>
      <component :is="icon" v-if="icon" class="size-4" aria-hidden="true" />
    </div>
        <strong class="stat-card__value">
          <CountUpNumber v-if="value !== null" :value="value" :suffix="suffix" />
          <span v-else aria-label="数据暂不可用">--</span>
        </strong>
        <small v-if="note" class="stat-card__note">{{ note }}</small>
  </article>
</template>
