<template>
  <div class="pagination" v-if="totalPages > 1">
    <el-pagination
      :current-page="page + 1"
      :page-size="size"
      :total="totalElements"
      layout="prev, pager, next, total"
      @current-change="onPageChange"
    />
  </div>
</template>

<script setup>
const props = defineProps({
  page: { type: Number, default: 0 },
  size: { type: Number, default: 20 },
  totalElements: { type: Number, default: 0 },
  totalPages: { type: Number, default: 0 }
})

const emit = defineEmits(['update:page'])

function onPageChange(val) {
  emit('update:page', val - 1)
}
</script>

<style lang="scss" scoped>
.pagination {
  display: flex;
  justify-content: center;
  padding: var(--spacing-md) 0;
}
</style>
