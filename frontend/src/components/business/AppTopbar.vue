<script setup lang="ts">
import { Bell, Menu, Search } from 'lucide-vue-next'

import Breadcrumb from '@/components/business/Breadcrumb.vue'
import ThemeToggle from '@/components/business/ThemeToggle.vue'
import UserMenu from '@/components/business/UserMenu.vue'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'

defineProps<{ loggingOut?: boolean; alertCount?: number }>()
const emit = defineEmits<{
  (e: 'open-sidebar'): void
  (e: 'open-palette'): void
  (e: 'logout'): void
}>()
</script>

<template>
  <header
    class="sticky top-0 z-30 flex h-16 items-center gap-2 border-b border-border bg-background/85 px-3 backdrop-blur-md sm:px-5"
  >
    <Button variant="ghost" size="icon" class="lg:hidden" aria-label="打开导航菜单" @click="emit('open-sidebar')">
      <Menu class="size-5" />
    </Button>

    <div class="min-w-0 flex-1">
      <Breadcrumb />
    </div>

    <div class="flex items-center gap-1 sm:gap-2">
      <Button
        variant="outline"
        size="sm"
        class="hidden h-9 w-56 justify-start gap-2 text-muted-foreground md:flex"
        @click="emit('open-palette')"
      >
        <Search class="size-4" />
        <span class="text-xs">搜索或跳转…</span>
        <kbd class="ml-auto rounded border border-border bg-muted px-1.5 py-0.5 text-[10px]">Ctrl K</kbd>
      </Button>
      <Button variant="ghost" size="icon" class="md:hidden" aria-label="搜索或跳转" @click="emit('open-palette')">
        <Search class="size-5" />
      </Button>

      <Tooltip>
        <TooltipTrigger as-child>
          <Button variant="ghost" size="icon" class="relative" aria-label="系统通知">
            <Bell class="size-5" />
            <span
              v-if="alertCount && alertCount > 0"
              class="absolute right-1.5 top-1.5 size-2 rounded-full bg-destructive ring-2 ring-background"
              aria-hidden="true"
            />
          </Button>
        </TooltipTrigger>
        <TooltipContent>系统通知</TooltipContent>
      </Tooltip>

      <ThemeToggle />

      <span class="mx-1 hidden h-6 w-px bg-border sm:block" aria-hidden="true" />

      <UserMenu :logging-out="loggingOut" @logout="emit('logout')" />
    </div>
  </header>
</template>
