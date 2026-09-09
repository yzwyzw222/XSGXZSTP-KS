<script setup lang="ts">
import { ElBreadcrumb, ElBreadcrumbItem } from 'element-plus'
import { useMediaQuery } from '@vueuse/core'
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

const route = useRoute()
const narrow = useMediaQuery('(max-width: 639px)')

interface Crumb { title: string; to: string }

const crumbs = computed<Crumb[]>(() => {
  const list: Crumb[] = [{ title: '工作台', to: '/' }]
  const matched = route.matched.filter((r) => typeof r.meta?.title === 'string' && r.meta.title !== '工作台')
  for (const record of matched) {
    // 用当前完整路径作为最后一项，父级用其自身 path
    const path = record === matched[matched.length - 1] ? route.path : record.path
    const title = record.meta.title as string
    if (list[list.length - 1]?.title === title) continue
    list.push({ title, to: path })
  }
  return list
})
const visibleCrumbs = computed(() => narrow.value ? crumbs.value.slice(-1) : crumbs.value)
</script>

<template>
  <ElBreadcrumb
    separator="/"
    class="aacv-breadcrumb"
    style="min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;"
  >
    <ElBreadcrumbItem v-for="(crumb, index) in visibleCrumbs" :key="`${crumb.title}-${index}`">
      <span v-if="index === visibleCrumbs.length - 1" aria-current="page" class="font-medium text-foreground">
        {{ crumb.title }}
      </span>
      <RouterLink v-else :to="crumb.to" class="transition-colors hover:text-primary">
        {{ crumb.title }}
      </RouterLink>
    </ElBreadcrumbItem>
  </ElBreadcrumb>
</template>

<style scoped>
/* 窄屏只呈现当前页面，完整层级由导航抽屉提供，避免面包屑挤成多行。 */
.aacv-breadcrumb {
  display: flex;
  --el-breadcrumb-font-size: var(--font-size-base);
  --el-breadcrumb-inner-color: hsl(var(--muted-foreground));
  --el-breadcrumb-inner-color-hover: hsl(var(--primary));
  --el-breadcrumb-inner-font-weight: var(--font-normal);
  --el-breadcrumb-separator-color: hsl(var(--muted-foreground) / 0.6);
}
</style>
