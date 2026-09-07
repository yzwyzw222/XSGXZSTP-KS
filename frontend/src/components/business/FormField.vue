<script setup lang="ts">
import { cn } from '@/lib/utils'

const props = defineProps<{
  /** 控件的 id，用于 label 的显式关联与错误信息的 aria-describedby。 */
  for?: string
  label?: string
  error?: string
  hint?: string
  required?: boolean
  class?: string
}>()
</script>

<template>
  <!--
    表单字段容器：只负责标签、错误文本与关联关系，
    校验规则由调用方的 schema（Zod + vee-validate）唯一提供，
    Element Plus 控件只负责呈现，避免出现两套冲突的校验源。
  -->
  <div :class="cn('grid gap-1.5 text-sm', props.class)">
    <label class="font-medium text-muted-foreground" :for="props.for">
      <slot name="label">{{ label }}</slot><span v-if="required" class="text-destructive" aria-hidden="true"> *</span>
    </label>
    <slot :invalid="Boolean(error)" :described-by="error ? `${props.for}-error` : undefined" />
    <p v-if="error" :id="`${props.for}-error`" role="alert" class="text-xs text-destructive">{{ error }}</p>
    <p v-else-if="hint" class="text-xs text-muted-foreground/80">{{ hint }}</p>
  </div>
</template>
