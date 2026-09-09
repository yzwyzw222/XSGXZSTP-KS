<script setup>
defineProps({ name: { type: String, required: true } })
</script>

<template>
  <span class="portal-icon" :style="{ maskImage: `url(/assets/portal/icons/${name}.svg)` }" aria-hidden="true" />
</template>
