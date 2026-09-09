<template>
  <!--
    权限管理页（AdminUsersView）：管理员后台，分四个标签页
    ─────────────────────────────────────────────────────────────────
    安全约束：
      - 前端：仅 ADMIN 角色用户能看到侧栏入口（LayoutView 中控制）
      - 后端：/api/v1/admin/** 路径被 SecurityConfig 强制校验 ADMIN 角色
      - 非 ADMIN 直接访问 URL → 后端返回 403 → 前端显示错误提示
    四个标签页（el-tabs + lazy，切到才渲染，避免一次加载全部数据）：
      1. 用户管理：用户列表（分页 + 搜索）+ 创建用户 + 修改角色 + 修改状态 + 删除
      2. 数据导入：上传 JSON 文件批量导入 + 在线爬取 OpenAlex（DataImportPanel 组件）
      3. 数据记录：五个实体（论文/作者/机构/关键词/渠道）的增删改查（复用 DataView 组件）
      4. 实体抽取：LLM 抽取论文实体与关系（ExtractionPanel 组件）
  -->
  <div class="admin-view">
    <el-tabs v-model="adminTab" class="admin-tabs">
      <!-- ===== 标签页 1：用户管理 ===== -->
      <el-tab-pane label="用户管理" name="users">
        <div class="toolbar">
          <el-input v-model="query.keyword" placeholder="搜索用户名/姓名…" clearable style="width: 240px"
            @keyup.enter="loadUsers(0)" @clear="loadUsers(0)" />
          <el-button type="primary" @click="openCreateDialog">+ 创建用户</el-button>
        </div>

        <!-- 403 无权限提示 -->
        <div v-if="forbidden" class="forbidden-hint">
          <p>403 — 您没有管理员权限，无法访问此页面</p>
        </div>

        <el-table v-else :data="userList" v-loading="loading" stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="username" label="用户名" width="160" />
          <el-table-column prop="displayName" label="显示名" width="160" />
          <el-table-column label="角色" width="200">
            <template #default="{ row }">
              <el-tag v-for="r in row.roles" :key="r" :type="r === 'ADMIN' ? 'danger' : 'primary'" class="role-tag">
                {{ r }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openRolesDialog(row)">角色</el-button>
              <el-button link type="warning" @click="toggleStatus(row)">
                {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
              </el-button>
              <el-button link type="danger" @click="deleteUser(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination v-if="!forbidden" v-model:current-page="page" :page-size="query.size" :total="total"
          layout="total, prev, pager, next" class="pager" @current-change="loadUsers" />
      </el-tab-pane>

      <!-- ===== 标签页 2：数据导入（懒加载，切到才挂载） ===== -->
      <el-tab-pane label="数据导入" name="import" lazy>
        <DataImportPanel />
      </el-tab-pane>

      <!-- ===== 标签页 3：数据记录（懒加载，复用 DataView 的 CRUD） ===== -->
      <el-tab-pane label="数据记录" name="records" lazy>
        <DataView />
      </el-tab-pane>

      <!-- ===== 标签页 4：实体抽取（懒加载，LLM 抽取论文实体与关系） ===== -->
      <el-tab-pane label="实体抽取" name="extraction" lazy>
        <ExtractionPanel />
      </el-tab-pane>
    </el-tabs>

    <!-- 创建用户弹窗 -->
    <el-dialog v-model="createVisible" title="创建用户" width="460px" destroy-on-close>
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="用户名" required>
          <el-input v-model="createForm.username" placeholder="字母/数字/下划线，3~64 位" />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input v-model="createForm.password" type="password" placeholder="6~64 位" show-password />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="createForm.displayName" placeholder="可选" />
        </el-form-item>
        <el-form-item label="角色">
          <el-checkbox-group v-model="createForm.roles">
            <el-checkbox label="ADMIN">ADMIN</el-checkbox>
            <el-checkbox label="ANALYST">ANALYST</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createUser">创建</el-button>
      </template>
    </el-dialog>

    <!-- 修改角色弹窗 -->
    <el-dialog v-model="rolesVisible" title="修改角色" width="400px" destroy-on-close>
      <p class="muted">为用户「{{ rolesTarget?.username }}」分配角色（整体替换）</p>
      <el-checkbox-group v-model="rolesForm">
        <el-checkbox label="ADMIN">ADMIN</el-checkbox>
        <el-checkbox label="ANALYST">ANALYST</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="rolesVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRoles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 权限管理脚本：三个标签页（用户管理 / 数据导入 / 数据记录）
 * ─────────────────────────────────────────
 * 所有操作调用 adminApi（后端强制校验 ADMIN 角色）
 * 403 处理：显示无权限提示而非跳转（让用户知道被拒绝了）
 * 数据导入面板与数据记录（DataView）拆成独立组件，本页只做标签页容器
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, type UserAdmin } from '../api'
import { ApiError } from '../api/http'
import DataImportPanel from './DataImportPanel.vue'
import DataView from './DataView.vue'
import ExtractionPanel from './ExtractionPanel.vue'

// 当前激活的标签页（默认停在「用户管理」）
const adminTab = ref('users')

// ====================================================================
// 用户列表
// ====================================================================
const userList = ref<UserAdmin[]>([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const query = reactive({ keyword: '', size: 20 })
const forbidden = ref(false)

async function loadUsers(pageNum?: number) {
  if (pageNum !== undefined) page.value = pageNum
  loading.value = true
  forbidden.value = false
  try {
    const res = await adminApi.users({ keyword: query.keyword || undefined, page: page.value - 1, size: query.size })
    userList.value = res.items
    total.value = res.totalElements
  } catch (err) {
    if (err instanceof ApiError && err.status === 403) {
      forbidden.value = true
    } else {
      ElMessage.error('加载用户列表失败')
    }
  } finally {
    loading.value = false
  }
}

// ====================================================================
// 创建用户
// ====================================================================
const createVisible = ref(false)
const saving = ref(false)
const createForm = reactive({ username: '', password: '', displayName: '', roles: ['ANALYST'] })

function openCreateDialog() {
  Object.assign(createForm, { username: '', password: '', displayName: '', roles: ['ANALYST'] })
  createVisible.value = true
}

async function createUser() {
  if (!createForm.username.trim() || !createForm.password) {
    ElMessage.warning('用户名和密码不能为空')
    return
  }
  saving.value = true
  try {
    await adminApi.createUser({
      username: createForm.username,
      password: createForm.password,
      displayName: createForm.displayName || undefined,
      roles: createForm.roles
    })
    ElMessage.success('创建成功')
    createVisible.value = false
    loadUsers()
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? (err.problem?.detail ?? err.message) : '创建失败')
  } finally {
    saving.value = false
  }
}

// ====================================================================
// 修改角色
// ====================================================================
const rolesVisible = ref(false)
const rolesTarget = ref<UserAdmin | null>(null)
const rolesForm = ref<string[]>([])

function openRolesDialog(row: UserAdmin) {
  rolesTarget.value = row
  rolesForm.value = [...row.roles]
  rolesVisible.value = true
}

async function saveRoles() {
  if (!rolesTarget.value) return
  saving.value = true
  try {
    await adminApi.updateRoles(rolesTarget.value.id, rolesForm.value)
    ElMessage.success('角色已更新')
    rolesVisible.value = false
    loadUsers()
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? (err.problem?.detail ?? err.message) : '更新失败')
  } finally {
    saving.value = false
  }
}

// ====================================================================
// 切换状态（ACTIVE ↔ DISABLED）
// ====================================================================
async function toggleStatus(row: UserAdmin) {
  const newStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const action = newStatus === 'ACTIVE' ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确认${action}用户「${row.username}」？`, '状态变更', { type: 'warning' })
    await adminApi.updateStatus(row.id, newStatus)
    ElMessage.success(`已${action}`)
    loadUsers()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err instanceof ApiError ? (err.problem?.detail ?? err.message) : '操作失败')
    }
  }
}

// ====================================================================
// 删除用户
// ====================================================================
async function deleteUser(row: UserAdmin) {
  try {
    await ElMessageBox.confirm(`确认删除用户「${row.username}」？此操作不可恢复`, '删除确认', { type: 'warning' })
    await adminApi.remove(row.id)
    ElMessage.success('已删除')
    loadUsers()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err instanceof ApiError ? (err.problem?.detail ?? err.message) : '删除失败')
    }
  }
}

onMounted(() => {
  loadUsers(0)
})
</script>

<style scoped>
.admin-view {
  padding: var(--space-4);
}

/* 内嵌的 DataView 根节点自带 padding（作为独立页面时用的），
   放进标签页后由 .admin-view 统一留白，这里归零避免双重内边距 */
:deep(.data-view) {
  padding: 0;
}

.toolbar {
  display: flex;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  align-items: center;
}

.pager {
  margin-top: var(--space-4);
  justify-content: flex-end;
}

.role-tag {
  margin-right: var(--space-1);
}

.muted {
  color: var(--muted);
  font-size: 13px;
  margin-bottom: var(--space-3);
}

.forbidden-hint {
  text-align: center;
  padding: var(--space-6);
  color: var(--danger);
}
</style>
