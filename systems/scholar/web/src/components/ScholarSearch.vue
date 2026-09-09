<template>
  <div class="scholar-search">
    <el-autocomplete
      v-model="keyword"
      :fetch-suggestions="querySearch"
      :placeholder="placeholder"
      clearable
      class="search-input"
      value-key="name"
      @select="onSelect"
      @clear="onClear"
    >
      <template #default="{ item }">
        <div class="suggestion">
          <span>{{ item.name }}</span>
          <span class="suggestion-meta">{{ item.paperCount }} 篇论文<template v-if="item.institution"> · {{ item.institution }}</template></span>
        </div>
      </template>
    </el-autocomplete>
    <el-tag v-if="selected" closable type="primary" class="scope-tag" @close="onClear">
      当前范围：{{ selected.name }}
    </el-tag>
    <span v-else class="scope-tag muted-tag">当前范围：全部数据</span>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { searchScholars } from '../api/scholar'

const props = defineProps({
  placeholder: { type: String, default: '按作者姓名过滤（可选）' },
})

const emit = defineEmits(['select', 'clear'])

const keyword = ref('')
const selected = ref(null)

async function querySearch(query, cb) {
  try {
    const list = await searchScholars(query || '')
    cb(list.map((s) => ({ ...s, value: s.name })))
  } catch {
    cb([])
  }
}

function onSelect(item) {
  selected.value = item
  keyword.value = item.name
  emit('select', item)
}

function onClear() {
  if (!selected.value && !keyword.value) return
  selected.value = null
  keyword.value = ''
  emit('clear')
}
</script>

<style scoped>
.scholar-search {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.search-input {
  width: 340px;
}

.suggestion {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.suggestion-meta {
  color: var(--muted);
  font-size: 12px;
}

.muted-tag {
  color: var(--muted);
  background: transparent;
  border: 1px dashed #1e3a5e;
}
</style>
