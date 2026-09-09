<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import { ElButton, ElDrawer } from 'element-plus'
import { X } from 'lucide-vue-next'
import { nextTick, watch } from 'vue'
import PanelSection from './PanelSection.vue'

const props = defineProps<{ open: boolean; title: string }>()
const emit = defineEmits<{ (event: 'update:open', value: boolean): void }>()
const wide = useMediaQuery('(min-width: 1280px)')
let trigger: HTMLElement | null = null
watch(() => props.open, async open => {
  if (open) trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
  else if (wide.value) {
    await nextTick()
    if (trigger?.isConnected) trigger.focus()
  }
})
</script>
<template>
  <div class="split-workspace" :class="{ 'split-workspace--open': open && wide }">
    <div class="split-workspace__main"><slot /></div>
    <PanelSection v-if="wide && open" :title="title" class="split-workspace__detail" :aria-label="title">
      <template #actions><ElButton text circle aria-label="关闭预览" @click="emit('update:open', false)"><X :size="18" /></ElButton></template>
      <slot name="detail" />
    </PanelSection>
    <ElDrawer v-if="!wide" :model-value="open" :title="title" size="min(540px, 100vw)" class="aacv-drawer" @update:model-value="emit('update:open', $event)"><slot name="detail" /></ElDrawer>
  </div>
</template>
<style>
.split-workspace { display: grid; grid-template-columns: minmax(0, 1fr); flex: 1; min-height: 0; min-width: 0; gap: 12px; overflow: hidden; }
.split-workspace--open { grid-template-columns: minmax(0, 1fr) minmax(340px, 30%); }
.split-workspace__main { display: flex; flex-direction: column; min-width: 0; min-height: 0; overflow: hidden; }
.split-workspace__main > .panel-section { flex: 1; display: flex; flex-direction: column; min-height: 0; overflow: hidden; }
.split-workspace__main > .panel-section > .panel-section__body { display: flex; flex-direction: column; flex: 1; min-height: 0; overflow: auto; }
.split-workspace__detail { display: flex; flex-direction: column; min-height: 0; min-width: 0; overflow: hidden; }
.split-workspace__detail > .panel-section__body { flex: 1; min-height: 0; overflow: auto; }
</style>
