<script setup lang="ts">
import { LogOut, ShieldCheck, UserRound } from 'lucide-vue-next'

import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel,
  DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { session } from '@/services/session'
import { computed } from 'vue'

const props = defineProps<{ loggingOut?: boolean }>()
const emit = defineEmits<{ (e: 'logout'): void }>()

const initials = computed(() => {
  const name = session.user?.username ?? '?'
  return name.slice(0, 2).toUpperCase()
})
const roles = computed(() => session.user?.roles ?? [])
</script>

<template>
  <DropdownMenu>
    <DropdownMenuTrigger as-child>
      <button
        type="button"
        class="flex items-center gap-2 rounded-full pr-1 transition-opacity hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        aria-label="账户菜单"
      >
        <span class="hidden text-right sm:block">
          <span class="block max-w-[140px] truncate text-xs font-medium text-foreground">{{ session.user?.username }}</span>
        </span>
        <Avatar class="size-8 border border-border">
          <AvatarFallback class="bg-primary/15 text-primary"><UserRound class="size-4" /></AvatarFallback>
        </Avatar>
      </button>
    </DropdownMenuTrigger>
    <DropdownMenuContent align="end" class="w-60">
      <DropdownMenuLabel class="flex items-center gap-2">
        <Avatar class="size-8"><AvatarFallback class="bg-primary/15 text-primary">{{ initials }}</AvatarFallback></Avatar>
        <span class="min-w-0">
          <span class="block truncate text-sm font-medium">{{ session.user?.username }}</span>
          <span class="block text-xs text-muted-foreground">已认证会话</span>
        </span>
      </DropdownMenuLabel>
      <DropdownMenuSeparator />
      <div class="flex flex-wrap gap-1 px-2 py-1.5">
        <Badge v-for="role in roles" :key="role" variant="subtle" class="gap-1">
          <ShieldCheck class="size-3" />{{ role }}
        </Badge>
      </div>
      <DropdownMenuSeparator />
      <DropdownMenuItem :disabled="props.loggingOut" @click="emit('logout')">
        <LogOut class="size-4" />
        退出登录
      </DropdownMenuItem>
    </DropdownMenuContent>
  </DropdownMenu>
</template>
