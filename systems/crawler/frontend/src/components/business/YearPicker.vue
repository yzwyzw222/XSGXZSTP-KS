<script setup lang="ts">
import { ElButton, ElPopover } from 'element-plus'
import { CalendarDays, ChevronDown, ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'

import { cn } from '@/lib/utils'

const props = withDefaults(defineProps<{
  modelValue?: number
  placeholder?: string
  ariaLabel?: string
  class?: string
  min?: number
  max?: number
  compact?: boolean
}>(), { placeholder: '不限年份', min: 1000, max: 9999 })

const emit = defineEmits<{ (e: 'update:modelValue', value: number | undefined): void }>()

const pageSize = 12
const open = ref(false)
const currentYear = new Date().getFullYear()

function pageStartOf(year: number): number {
  return Math.floor(year / pageSize) * pageSize
}

const minPage = computed(() => pageStartOf(props.min))
const maxPage = computed(() => pageStartOf(props.max))
const pageStart = ref(pageStartOf(props.modelValue ?? currentYear))
const pageYears = computed(() => Array.from({ length: pageSize }, (_, index) => pageStart.value + index))
const pageLabel = computed(() => `${pageStart.value} – ${pageStart.value + pageSize - 1}`)

/** 每次打开面板都回到已选年份所在页，避免用户从远处翻页找回来。 */
watch(open, (value) => {
  if (value) pageStart.value = pageStartOf(props.modelValue ?? currentYear)
})

function shiftPage(delta: number): void {
  pageStart.value = Math.min(Math.max(pageStart.value + delta * pageSize, minPage.value), maxPage.value)
}

function pick(year: number): void {
  emit('update:modelValue', year)
  open.value = false
}

function clear(): void {
  emit('update:modelValue', undefined)
  open.value = false
}
</script>

<template>
  <!-- 年份筛选是数字条件而不是日期，因此不套用日期控件，避免引入时区语义。 -->
  <ElPopover
    v-model:visible="open"
    trigger="click"
    placement="bottom-start"
    :width="256"
    popper-class="aacv-year-popper"
  >
    <template #reference>
      <ElButton
        plain
        :size="compact ? 'small' : 'default'"
        :class="cn(compact ? 'aacv-year-compact' : 'w-full justify-between font-normal', !modelValue && 'aacv-year-empty', props.class)"
        :aria-label="ariaLabel ?? `选择${placeholder}`"
      >
        <span class="flex min-w-0 items-center gap-2">
          <CalendarDays class="size-4 shrink-0 opacity-60" aria-hidden="true" />
          <span v-if="!compact || modelValue" class="truncate tabular-nums">{{ modelValue ?? placeholder }}</span>
        </span>
        <ChevronDown v-if="!compact" class="size-4 shrink-0 opacity-50" aria-hidden="true" />
      </ElButton>
    </template>

    <div class="p-1">
      <div class="flex items-center justify-between">
        <ElButton text circle aria-label="向前翻12年" :disabled="pageStart <= minPage" @click="shiftPage(-1)">
          <ChevronLeft class="size-4" aria-hidden="true" />
        </ElButton>
        <span class="text-sm font-medium tabular-nums">{{ pageLabel }}</span>
        <ElButton text circle aria-label="向后翻12年" :disabled="pageStart >= maxPage" @click="shiftPage(1)">
          <ChevronRight class="size-4" aria-hidden="true" />
        </ElButton>
      </div>
      <div class="mt-2 grid grid-cols-4 gap-1">
        <ElButton
          v-for="year in pageYears"
          :key="year"
          text
          size="small"
          class="h-8 p-0 tabular-nums"
          :type="year === modelValue ? 'primary' : 'default'"
          :disabled="year < min || year > max"
          :aria-pressed="year === modelValue"
          @click="pick(year)"
        >
          {{ year }}
        </ElButton>
      </div>
      <div class="mt-2 flex items-center justify-between border-t border-border pt-2">
        <ElButton text size="small" class="h-7 px-2 text-xs" @click="pick(currentYear)">今年</ElButton>
        <ElButton
          text
          size="small"
          class="h-7 px-2 text-xs"
          :disabled="modelValue === undefined"
          @click="clear"
        >
          清除
        </ElButton>
      </div>
    </div>
  </ElPopover>
</template>

<style scoped>
/* 未选择年份时以次要文本色提示"占位而非已选值"，状态不只依赖颜色。 */
.aacv-year-empty {
  --el-button-text-color: hsl(var(--muted-foreground));
}
.aacv-year-compact { padding-inline: 6px; }
</style>
