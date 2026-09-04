<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElCheckbox,
  ElCheckboxGroup,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElPagination,
  ElTable,
  ElTableColumn,
  ElTag,
  vLoading,
} from 'element-plus'

import { toErrorMessage } from '@/services/api'
import { userApi } from '@/services/business'
import type { PageResponse, RoleCode, UserAccount } from '@/types/api'
import { formatDateTime } from '@/utils/format'

const allRoles: Array<{ value: RoleCode; label: string }> = [
  { value: 'ADMIN', label: '管理员' },
  { value: 'DATA_OPERATOR', label: '数据操作员' },
  { value: 'RESEARCHER', label: '研究人员' },
]
const users = ref<PageResponse<UserAccount>>({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const createVisible = ref(false)
const rolesVisible = ref(false)
const passwordVisible = ref(false)
const selected = ref<UserAccount | null>(null)
const createForm = reactive({ username: '', password: '', roles: ['RESEARCHER'] as RoleCode[] })
const roleForm = ref<RoleCode[]>([])
const newPassword = ref('')

async function load(page = 0): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    users.value = await userApi.page(page, users.value.size)
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    loading.value = false
  }
}

function validatePassword(password: string): boolean {
  if (password.length < 12) {
    errorMessage.value = '密码长度至少为 12 个字符'
    return false
  }
  return true
}

async function createUser(): Promise<void> {
  if (createForm.username.trim().length < 3 || createForm.roles.length === 0 || !validatePassword(createForm.password)) {
    if (!errorMessage.value) errorMessage.value = '用户名至少 3 个字符且必须选择角色'
    return
  }
  saving.value = true
  errorMessage.value = ''
  try {
    await userApi.create(createForm.username.trim(), createForm.password, createForm.roles)
    createVisible.value = false
    Object.assign(createForm, { username: '', password: '', roles: ['RESEARCHER'] })
    ElMessage.success('用户已创建')
    await load()
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    saving.value = false
  }
}

async function toggle(user: UserAccount): Promise<void> {
  try {
    await ElMessageBox.confirm(
      '确认' + (user.status === 'ACTIVE' ? '停用' : '启用') + '用户 ' + user.username + '？',
      '账号状态确认',
      { type: 'warning' },
    )
    await userApi.setEnabled(user, user.status !== 'ACTIVE')
    ElMessage.success('用户状态已更新')
    await load(users.value.page)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') errorMessage.value = toErrorMessage(error)
  }
}

function openRoles(user: UserAccount): void {
  selected.value = user
  roleForm.value = [...user.roles]
  rolesVisible.value = true
}

async function saveRoles(): Promise<void> {
  if (!selected.value || roleForm.value.length === 0) {
    errorMessage.value = '至少选择一个角色'
    return
  }
  saving.value = true
  try {
    await userApi.replaceRoles(selected.value, roleForm.value)
    rolesVisible.value = false
    ElMessage.success('用户角色已更新')
    await load(users.value.page)
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    saving.value = false
  }
}

function openPassword(user: UserAccount): void {
  selected.value = user
  newPassword.value = ''
  passwordVisible.value = true
}

async function resetPassword(): Promise<void> {
  if (!selected.value || !validatePassword(newPassword.value)) return
  saving.value = true
  try {
    await userApi.resetPassword(selected.value, newPassword.value)
    passwordVisible.value = false
    ElMessage.success('密码已重置')
    await load(users.value.page)
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    saving.value = false
  }
}

onMounted(() => load())
</script>

<template>
  <section class="page-stack">
    <header class="page-heading">
      <div>
        <span class="eyebrow">ADMINISTRATION / USERS</span>
        <h1>用户管理</h1>
        <p>创建内部账号、维护角色与启用状态，并在必要时重置登录凭据。</p>
      </div>
      <ElButton type="primary" @click="createVisible = true">新增用户</ElButton>
    </header>
    <ElAlert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
    <div class="content-panel">
      <div class="toolbar"><strong>系统账号</strong><span class="meta-line">共 {{ users.totalElements }} 个</span></div>
      <ElTable v-loading="loading" :data="users.items" empty-text="暂无用户">
        <ElTableColumn prop="username" label="用户名" min-width="160" />
        <ElTableColumn label="角色" min-width="240">
          <template #default="{ row }"><ElTag v-for="role in row.roles" :key="role" size="small" effect="plain">{{ role }}</ElTag></template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="130"><template #default="{ row }"><ElTag :type="row.status === 'ACTIVE' ? 'success' : row.status === 'PASSWORD_RESET_REQUIRED' ? 'warning' : 'info'">{{ row.status === 'ACTIVE' ? '启用' : row.status === 'PASSWORD_RESET_REQUIRED' ? '待重置密码' : '停用' }}</ElTag></template></ElTableColumn>
        <ElTableColumn label="凭据更新时间" width="180"><template #default="{ row }">{{ formatDateTime(row.credentialsChangedAt) }}</template></ElTableColumn>
        <ElTableColumn label="更新时间" width="180"><template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template></ElTableColumn>
        <ElTableColumn label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openRoles(row as UserAccount)">角色</ElButton>
            <ElButton link type="primary" @click="openPassword(row as UserAccount)">重置密码</ElButton>
            <ElButton link :type="row.status === 'ACTIVE' ? 'danger' : 'success'" @click="toggle(row as UserAccount)">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <div v-if="users.totalPages > 1" class="pagination-row">
        <ElPagination :current-page="users.page + 1" :page-size="users.size" :total="users.totalElements" layout="prev, pager, next" @current-change="(page: number) => load(page - 1)" />
      </div>
    </div>

    <ElDialog v-model="createVisible" title="新增用户" width="520px">
      <ElForm label-position="top">
        <ElFormItem label="用户名"><ElInput v-model="createForm.username" maxlength="64" /></ElFormItem>
        <ElFormItem label="初始密码"><ElInput v-model="createForm.password" type="password" maxlength="128" show-password autocomplete="new-password" /></ElFormItem>
        <ElFormItem label="角色">
          <ElCheckboxGroup v-model="createForm.roles">
            <ElCheckbox v-for="role in allRoles" :key="role.value" :value="role.value">{{ role.label }}</ElCheckbox>
          </ElCheckboxGroup>
        </ElFormItem>
      </ElForm>
      <ElAlert title="初始密码至少 12 个字符，请通过安全渠道交付给用户。" type="warning" :closable="false" />
      <template #footer><ElButton @click="createVisible = false">取消</ElButton><ElButton type="primary" :loading="saving" @click="createUser">创建用户</ElButton></template>
    </ElDialog>

    <ElDialog v-model="rolesVisible" :title="'调整角色 · ' + selected?.username" width="500px">
      <ElCheckboxGroup v-model="roleForm" class="role-list">
        <ElCheckbox v-for="role in allRoles" :key="role.value" :value="role.value">{{ role.label }}（{{ role.value }}）</ElCheckbox>
      </ElCheckboxGroup>
      <template #footer><ElButton @click="rolesVisible = false">取消</ElButton><ElButton type="primary" :loading="saving" @click="saveRoles">保存角色</ElButton></template>
    </ElDialog>

    <ElDialog v-model="passwordVisible" :title="'重置密码 · ' + selected?.username" width="500px">
      <ElInput v-model="newPassword" type="password" maxlength="128" show-password autocomplete="new-password" placeholder="输入至少 12 个字符的新密码" />
      <template #footer><ElButton @click="passwordVisible = false">取消</ElButton><ElButton type="primary" :loading="saving" @click="resetPassword">确认重置</ElButton></template>
    </ElDialog>
  </section>
</template>
