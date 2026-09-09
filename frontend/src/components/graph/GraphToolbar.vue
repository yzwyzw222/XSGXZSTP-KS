<template>
  <div class="graph-toolbar">
    <el-button-group>
      <el-button size="small" @click="$emit('fit')">适应</el-button>
      <el-button size="small" @click="$emit('zoom-in')">放大</el-button>
      <el-button size="small" @click="$emit('zoom-out')">缩小</el-button>
    </el-button-group>
    <el-button size="small" @click="$emit('relayout')">重排</el-button>
  </div>
</template>

<script setup>
defineEmits(['fit', 'zoom-in', 'zoom-out', 'relayout'])
</script>

<style lang="scss" scoped>
.graph-toolbar {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  gap: var(--spacing-xs);
  z-index: 10;
}
</style>
