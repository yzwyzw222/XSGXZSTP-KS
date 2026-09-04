<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { CornerDownLeft, LogOut, Monitor, Moon, Search, Sun } from 'lucide-vue-next'

import { Dialog, DialogContent } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { navItems } from '@/config/nav'
import { useTheme } from '@/composables/useTheme'
import { session } from '@/services/session'
import { cn } from '@/lib/utils'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ (e: 'update:open', v: boolean): void; (e: 'logout'): void }>()

const router = useRouter()
const { theme, setTheme } = useTheme()
const query = ref('')
const activeIndex = ref(0)
const inputEl = ref<HTMLInputElement | null>(null)

interface Command {
  id: string
  label: string
  group: string
  icon: unknown
  hint?: string
  run: () => void
}

const commands = computed<Command[]>(() => {
  const perms = session.user?.permissions ?? []
  const nav: Command[] = navItems
    .filter((item) => !item.permission || perms.includes(item.permission))
    .map((item) => ({
      id: `nav-${item.to}`,
      label: item.label,
      group: '导航',
      icon: item.icon,
      run: () => { void router.push(item.to) },
    }))
  const actions: Command[] = [
    { id: 'theme-light', label: '切换到浅色主题', group: '操作', icon: Sun, run: () => setTheme('light') },
    { id: 'theme-dark', label: '切换到深色主题', group: '操作', icon: Moon, run: () => setTheme('dark') },
    { id: 'theme-auto', label: '主题跟随系统', group: '操作', icon: Monitor, run: () => setTheme('auto') },
    { id: 'logout', label: '退出登录', group: '操作', icon: LogOut, run: () => emit('logout') },
  ]
  return [...nav, ...actions]
})

const filtered = computed<Command[]>(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return commands.value
  return commands.value.filter((cmd) => {
    const item = navItems.find((n) => `nav-${n.to}` === cmd.id)
    const keywords = item?.keywords?.join(' ') ?? ''
    return `${cmd.label} ${cmd.hint ?? ''} ${keywords}`.toLowerCase().includes(q)
  })
})

const grouped = computed(() => {
  const map = new Map<string, Command[]>()
  for (const cmd of filtered.value) {
    if (!map.has(cmd.group)) map.set(cmd.group, [])
    map.get(cmd.group)!.push(cmd)
  }
  return [...map.entries()]
})

watch(() => props.open, async (open) => {
  if (open) {
    query.value = ''
    activeIndex.value = 0
    await nextTick()
    inputEl.value?.focus()
  }
})
watch(filtered, () => { activeIndex.value = 0 })

function close(): void {
  emit('update:open', false)
}
function runCommand(cmd: Command): void {
  cmd.run()
  close()
}
function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    activeIndex.value = (activeIndex.value + 1) % Math.max(1, filtered.value.length)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    activeIndex.value = (activeIndex.value - 1 + filtered.value.length) % Math.max(1, filtered.value.length)
  } else if (event.key === 'Enter') {
    event.preventDefault()
    const cmd = filtered.value[activeIndex.value]
    if (cmd) runCommand(cmd)
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="(v) => emit('update:open', v)">
    <DialogContent class="top-[20%] translate-y-0 gap-0 overflow-hidden p-0 sm:max-w-lg" hide-close>
      <div class="flex items-center gap-2 border-b border-border px-4">
        <Search class="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
        <Input
          ref="inputEl"
          v-model="query"
          class="h-12 border-0 bg-transparent shadow-none focus-visible:ring-0"
          placeholder="搜索页面或执行操作…"
          aria-label="命令面板搜索"
          @keydown="onKeydown"
        />
        <kbd class="hidden shrink-0 rounded border border-border bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground sm:block">Esc</kbd>
      </div>
      <div class="max-h-80 overflow-y-auto p-2" role="listbox" aria-label="命令列表">
        <p v-if="!filtered.length" class="px-3 py-8 text-center text-sm text-muted-foreground">没有匹配的命令</p>
        <template v-for="[group, items] in grouped" :key="group">
          <p class="px-3 py-1.5 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">{{ group }}</p>
          <button
            v-for="cmd in items"
            :key="cmd.id"
            type="button"
            role="option"
            :aria-selected="filtered[activeIndex]?.id === cmd.id"
            :class="cn(
              'flex w-full items-center gap-3 rounded-md px-3 py-2 text-left text-sm transition-colors',
              filtered[activeIndex]?.id === cmd.id ? 'bg-accent text-accent-foreground' : 'hover:bg-accent/50',
            )"
            @click="runCommand(cmd)"
            @mousemove="activeIndex = filtered.findIndex((f) => f.id === cmd.id)"
          >
            <component :is="cmd.icon" class="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
            <span class="min-w-0 flex-1 truncate">{{ cmd.label }}</span>
            <span v-if="cmd.hint" class="shrink-0 text-xs text-muted-foreground">{{ cmd.hint }}</span>
            <CornerDownLeft v-if="filtered[activeIndex]?.id === cmd.id" class="size-3.5 shrink-0 text-muted-foreground" aria-hidden="true" />
          </button>
        </template>
      </div>
      <div class="flex items-center justify-between border-t border-border px-4 py-2 text-[10px] text-muted-foreground">
        <span>{{ theme === 'auto' ? '主题：跟随系统' : `主题：${theme === 'dark' ? '深色' : '浅色'}` }}</span>
        <span class="flex items-center gap-2">
          <kbd class="rounded border border-border bg-muted px-1">↑</kbd>
          <kbd class="rounded border border-border bg-muted px-1">↓</kbd> 选择
          <kbd class="rounded border border-border bg-muted px-1">↵</kbd> 执行
        </span>
      </div>
    </DialogContent>
  </Dialog>
</template>
