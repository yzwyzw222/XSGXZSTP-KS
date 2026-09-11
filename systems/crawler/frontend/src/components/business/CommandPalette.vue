<script setup lang="ts">
import { ElDialog, ElInput } from 'element-plus'
import { CornerDownLeft, LogOut, Search } from 'lucide-vue-next'
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { navGroups, navItems } from '@/config/nav'
import { cn } from '@/lib/utils'
import { useSessionStore } from '@/stores/session'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ (e: 'update:open', v: boolean): void; (e: 'logout'): void }>()

const router = useRouter()
const route = useRoute()
const sessionStore = useSessionStore()

const query = ref('')
const activeIndex = ref(0)
const searchInput = ref<InstanceType<typeof ElInput> | null>(null)

interface Command {
  id: string
  label: string
  group: string
  icon: unknown
  hint?: string
  run: () => void
}

const commands = computed<Command[]>(() => {
  const perms = sessionStore.permissions
  const nav: Command[] = navItems
    .filter((item) => !item.permission || perms.includes(item.permission))
    .flatMap((item) => [
      {
        id: `nav-${item.to}`,
        label: item.label,
        group: navGroups.find((group) => group.id === item.group)?.label ?? '',
        icon: item.icon,
        run: () => { void router.push(item.preserveAuthor ? { path: item.to, query: { authorId: route.query.authorId } } : item.to) },
      },
      ...(item.children ?? []).filter((child) => child.to !== item.to).map((child) => ({
        id: `nav-${child.to}`,
        label: `${item.label} · ${child.label}`,
        group: navGroups.find((group) => group.id === item.group)?.label ?? '',
        icon: item.icon,
        run: () => { void router.push(child.to) },
      })),
    ])
  const actions: Command[] = [
    { id: 'logout', label: '退出登录', group: '操作', icon: LogOut, run: () => emit('logout') },
  ]
  return [...nav, ...actions]
})

const filtered = computed<Command[]>(() => {
  const keyword = query.value.trim().toLowerCase()
  if (!keyword) return commands.value
  return commands.value.filter((command) => {
    const item = navItems.find((nav) => `nav-${nav.to}` === command.id)
    const keywords = item?.keywords?.join(' ') ?? ''
    return `${command.group} ${command.label} ${command.hint ?? ''} ${keywords}`.toLowerCase().includes(keyword)
  })
})

const grouped = computed(() => {
  const map = new Map<string, Command[]>()
  for (const command of filtered.value) {
    if (!map.has(command.group)) map.set(command.group, [])
    map.get(command.group)!.push(command)
  }
  return [...map.entries()]
})

watch(() => props.open, (open) => {
  if (!open) return
  query.value = ''
  activeIndex.value = 0
})
watch(filtered, () => { activeIndex.value = 0 })

/** 焦点陷阱就绪后再聚焦，避免首次打开时被弹窗默认焦点覆盖。 */
function focusSearch(): void {
  void nextTick(() => searchInput.value?.focus())
}

function close(): void {
  emit('update:open', false)
}

function runCommand(command: Command): void {
  command.run()
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
    const command = filtered.value[activeIndex.value]
    if (command) runCommand(command)
  }
}
</script>

<template>
  <ElDialog
    :model-value="open"
    top="15vh"
    width="min(560px, calc(100vw - 32px))"
    :show-close="false"
    append-to-body
    class="aacv-command-palette"
    header-class="aacv-command-palette__header"
    body-class="aacv-command-palette__body"
    @open-auto-focus="focusSearch"
    @opened="searchInput?.focus()"
    @update:model-value="(value: boolean) => emit('update:open', value)"
  >
    <template #header="{ titleId }">
      <h2 :id="titleId" class="sr-only">搜索页面或执行操作</h2>
      <p class="sr-only">输入模块或页面名称，用方向键选择并按回车打开。</p>
    </template>

    <div @keydown="onKeydown">
      <div class="flex items-center gap-2 border-b border-border px-4">
        <Search class="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
        <ElInput
          ref="searchInput"
          v-model="query"
          placeholder="搜索页面或执行操作…"
          aria-label="命令面板搜索"
          maxlength="64"
          class="aacv-command-palette__input"
        />
        <kbd class="hidden shrink-0 rounded border border-border bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground sm:block">Esc</kbd>
      </div>

      <div class="max-h-80 overflow-y-auto p-2" role="listbox" aria-label="命令列表">
        <p v-if="!filtered.length" class="px-3 py-8 text-center text-sm text-muted-foreground">没有匹配的命令</p>
        <template v-for="[group, items] in grouped" :key="group">
          <p class="px-3 py-1.5 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">{{ group }}</p>
          <button
            v-for="command in items"
            :key="command.id"
            type="button"
            role="option"
            :aria-selected="filtered[activeIndex]?.id === command.id"
            :class="cn(
              'flex w-full items-center gap-3 rounded-md px-3 py-2 text-left text-sm transition-colors',
              filtered[activeIndex]?.id === command.id ? 'bg-accent text-accent-foreground' : 'hover:bg-accent/50',
            )"
            @click="runCommand(command)"
            @mousemove="activeIndex = filtered.findIndex((item) => item.id === command.id)"
          >
            <component :is="command.icon" class="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
            <span class="min-w-0 flex-1 truncate">{{ command.label }}</span>
            <span v-if="command.hint" class="shrink-0 text-xs text-muted-foreground">{{ command.hint }}</span>
            <CornerDownLeft
              v-if="filtered[activeIndex]?.id === command.id"
              class="size-3.5 shrink-0 text-muted-foreground"
              aria-hidden="true"
            />
          </button>
        </template>
      </div>

      <div class="flex items-center justify-between border-t border-border px-4 py-2 text-[10px] text-muted-foreground">
        <span>深蓝科研主题</span>
        <span class="flex items-center gap-2">
          <kbd class="rounded border border-border bg-muted px-1">↑</kbd>
          <kbd class="rounded border border-border bg-muted px-1">↓</kbd> 选择
          <kbd class="rounded border border-border bg-muted px-1">↵</kbd> 执行
        </span>
      </div>
    </div>
  </ElDialog>
</template>
