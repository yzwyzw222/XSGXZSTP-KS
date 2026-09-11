<script setup lang="ts">
import { ElButton, ElPopover } from 'element-plus'
import { CalendarDays } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'
import YearPicker from './YearPicker.vue'

const props = defineProps<{ from?: number; to?: number }>()
const emit = defineEmits<{ (e: 'apply', from: number | undefined, to: number | undefined): void }>()
const open = ref(false)
const fromYear = ref<number>()
const toYear = ref<number>()
const invalid = computed(() => fromYear.value !== undefined && toYear.value !== undefined && fromYear.value > toYear.value)
watch(open, value => { if (value) { fromYear.value = props.from; toYear.value = props.to } })
function apply(): void { if (!invalid.value) { emit('apply', fromYear.value, toYear.value); open.value = false } }
</script>

<template>
  <ElPopover v-model:visible="open" trigger="click" placement="bottom-end" :width="280">
    <template #reference><ElButton size="small" aria-label="按发表年份范围筛选"><CalendarDays :size="14" class="mr-1" />{{ from || to ? `${from ?? '不限'}—${to ?? '不限'}` : '年份' }}</ElButton></template>
    <div class="grid gap-3">
      <label class="text-xs">起始年份<YearPicker v-model="fromYear" aria-label="选择起始年份" /></label>
      <label class="text-xs">结束年份<YearPicker v-model="toYear" aria-label="选择结束年份" /></label>
      <p v-if="invalid" role="alert" class="text-xs text-destructive">起始年份不能晚于结束年份。</p>
      <div class="flex justify-end gap-2"><ElButton size="small" @click="fromYear = undefined; toYear = undefined; apply()">清除年份</ElButton><ElButton size="small" type="primary" :disabled="invalid" @click="apply">应用年份</ElButton></div>
    </div>
  </ElPopover>
</template>
