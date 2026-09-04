<script setup lang="ts">
import { watch } from 'vue'
import { RouterView, useRouter } from 'vue-router'

import { session } from '@/services/session'

const router = useRouter()

watch(
  () => session.expired,
  (expired) => {
    if (expired && router.currentRoute.value.name !== 'session-expired') {
      void router.replace({ name: 'session-expired' })
    }
  },
)
</script>

<template>
  <RouterView />
</template>
