<script setup lang="ts">
import { ElButton, ElDropdown, ElDropdownItem, ElDropdownMenu, ElTag } from 'element-plus'
import { LogOut, ShieldCheck, UserRound } from 'lucide-vue-next'
import { computed } from 'vue'

import { useSessionStore } from '@/stores/session'

const props = defineProps<{ loggingOut?: boolean }>()
const emit = defineEmits<{ (e: 'logout'): void }>()

const sessionStore = useSessionStore()

const initials = computed(() => {
  const name = sessionStore.user?.username ?? '?'
  return name.slice(0, 2).toUpperCase()
})
const roles = computed(() => sessionStore.user?.roles ?? [])
</script>

<template>
  <ElDropdown trigger="click" placement="bottom-end">
    <ElButton
      text
      class="aacv-user-trigger"
      aria-label="账户菜单"
      style="height: 40px; padding: 0 8px 0 4px; border-radius: var(--radius-full)"
    >
      <span class="hidden max-w-[140px] truncate text-xs font-medium text-foreground sm:block">
        {{ sessionStore.username }}
      </span>
      <span
        class="grid size-8 shrink-0 place-items-center rounded-full border border-border bg-primary/15 text-primary"
        aria-hidden="true"
      >
        <UserRound class="size-4" />
      </span>
    </ElButton>
    <template #dropdown>
      <ElDropdownMenu style="width: 240px">
        <div class="flex items-center gap-2 px-3 py-2">
          <span
            class="grid size-8 shrink-0 place-items-center rounded-full bg-primary/15 text-xs font-semibold text-primary"
            aria-hidden="true"
          >{{ initials }}</span>
          <span class="min-w-0">
            <span class="block truncate text-sm font-medium text-foreground">{{ sessionStore.username }}</span>
            <span class="block text-xs text-muted-foreground">已认证会话</span>
          </span>
        </div>
        <div class="flex flex-wrap gap-1 px-3 pb-2">
          <ElTag v-for="role in roles" :key="role" size="small" type="info" effect="plain">
            <ShieldCheck class="mr-1 inline size-3 align-[-2px]" aria-hidden="true" />{{ role }}
          </ElTag>
        </div>
        <ElDropdownItem divided :disabled="props.loggingOut" @click="emit('logout')">
          <span class="flex items-center gap-2">
            <LogOut class="size-4" aria-hidden="true" />
            退出登录
          </span>
        </ElDropdownItem>
      </ElDropdownMenu>
    </template>
  </ElDropdown>
</template>

<style scoped>
/* 触发器需要容纳用户名与头像，因此覆盖 Element Plus 文本按钮的默认内边距。 */
.aacv-user-trigger {
  --el-button-hover-bg-color: hsl(var(--accent));
  --el-button-hover-text-color: hsl(var(--foreground));
}
</style>
