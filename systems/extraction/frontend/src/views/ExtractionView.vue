<template>
  <div class="extraction-view">
    <h2 class="page-title">实体抽取</h2>
    <p class="extraction-view__desc">
      选择待抽取的论文，系统将调用 LLM 从论文摘要中抽取学术实体和关系。
    </p>

    <div class="extraction-view__actions">
      <el-button type="primary" @click="triggerBatch" :loading="triggering">
        批量抽取待处理论文
      </el-button>
      <el-button @click="loadPending">刷新列表</el-button>
    </div>

    <div class="extraction-view__manual">
      <h3>手动指定论文ID</h3>
      <div class="extraction-view__manual-row">
        <el-input v-model="manualPaperId" placeholder="输入论文ID" style="width: 200px" />
        <el-button @click="triggerSingle" :loading="triggeringSingle">抽取</el-button>
      </div>
    </div>

    <div class="extraction-view__status" v-if="statusResult">
      <h3>抽取状态</h3>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="论文ID">{{ statusResult.paperId }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusResult.status }}</el-descriptions-item>
        <el-descriptions-item label="实体数">{{ statusResult.entityCount }}</el-descriptions-item>
        <el-descriptions-item label="关系数">{{ statusResult.relationshipCount }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <div class="extraction-view__pending" v-if="pendingPapers.length">
      <h3>待处理论文 ({{ pendingPapers.length }})</h3>
      <el-table :data="pendingPapers" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="year" label="年份" width="80" />
        <el-table-column prop="extractionStatus" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.extractionStatus)" size="small">
              {{ row.extractionStatus }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { triggerExtraction, getExtractionStatus } from '../api/extraction.js'
import { searchPapers } from '../api/paper.js'
import { ElMessage } from 'element-plus'

const manualPaperId = ref('')
const triggering = ref(false)
const triggeringSingle = ref(false)
const statusResult = ref(null)
const pendingPapers = ref([])

function statusTagType(status) {
  const map = { PENDING: 'warning', IN_PROGRESS: '', COMPLETED: 'success', FAILED: 'danger' }
  return map[status] || ''
}

async function loadPending() {
  try {
    const res = await searchPapers({ extractionStatus: 'PENDING', page: 0, size: 50 })
    pendingPapers.value = res.items || []
  } catch {
    pendingPapers.value = []
  }
}

async function triggerBatch() {
  triggering.value = true
  try {
    await triggerExtraction(null)
    ElMessage.success('批量抽取任务已提交')
    setTimeout(loadPending, 2000)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    triggering.value = false
  }
}

async function triggerSingle() {
  if (!manualPaperId.value) return
  triggeringSingle.value = true
  try {
    await triggerExtraction(manualPaperId.value)
    ElMessage.success('抽取任务已提交')
    const s = await getExtractionStatus(manualPaperId.value)
    statusResult.value = s
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    triggeringSingle.value = false
  }
}

onMounted(loadPending)
</script>

<style lang="scss" scoped>
.extraction-view {
  max-width: 900px;

  &__desc {
    color: var(--text-secondary);
    font-size: 0.875rem;
    margin: 0 0 var(--spacing-md);
  }

  &__actions {
    display: flex;
    gap: var(--spacing-sm);
    margin-bottom: var(--spacing-lg);
  }

  &__manual {
    margin-bottom: var(--spacing-lg);

    h3 {
      color: var(--text-primary);
      font-size: 0.95rem;
      margin: 0 0 var(--spacing-sm);
    }

    &-row {
      display: flex;
      gap: var(--spacing-sm);
    }
  }

  &__status {
    margin-bottom: var(--spacing-lg);

    h3 {
      color: var(--text-primary);
      font-size: 0.95rem;
      margin: 0 0 var(--spacing-sm);
    }
  }

  &__pending {
    h3 {
      color: var(--text-primary);
      font-size: 0.95rem;
      margin: 0 0 var(--spacing-sm);
    }
  }
}

.page-title {
  color: var(--text-primary);
  font-size: 1.25rem;
  margin: 0 0 var(--spacing-sm);
}
</style>
