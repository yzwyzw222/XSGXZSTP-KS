<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import {
  Breadcrumb, BreadcrumbItem, BreadcrumbLink, BreadcrumbList, BreadcrumbPage, BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'

const route = useRoute()

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
</script>

<template>
  <Breadcrumb>
    <BreadcrumbList>
      <template v-for="(crumb, index) in crumbs" :key="`${crumb.title}-${index}`">
        <BreadcrumbItem>
          <BreadcrumbPage v-if="index === crumbs.length - 1">{{ crumb.title }}</BreadcrumbPage>
          <BreadcrumbLink v-else as-child>
            <RouterLink :to="crumb.to">{{ crumb.title }}</RouterLink>
          </BreadcrumbLink>
        </BreadcrumbItem>
        <BreadcrumbSeparator v-if="index < crumbs.length - 1" />
      </template>
    </BreadcrumbList>
  </Breadcrumb>
</template>
