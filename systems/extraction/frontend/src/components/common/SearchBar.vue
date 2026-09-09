<template>
  <div class="search-bar">
    <el-input
      :model-value="modelValue"
      :placeholder="placeholder"
      clearable
      @update:model-value="$emit('update:modelValue', $event)"
      @keyup.enter="$emit('search')"
    >
      <template #append>
        <el-button @click="$emit('search')">
          <span>搜索</span>
        </el-button>
      </template>
    </el-input>
  </div>
</template>

<script setup>
defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '请输入搜索关键词...' }
})

defineEmits(['update:modelValue', 'search'])
</script>

<style lang="scss" scoped>
.search-bar {
  max-width: 600px;
}
</style>
