<script setup lang="ts">
import { ElAlert, ElButton, ElCheckbox, ElDialog, ElDrawer, ElInput, ElMessage, ElOption, ElSelect, ElSkeleton, ElTag } from 'element-plus'
import FormField from '@/components/business/FormField.vue'
import type { DataTableColumn } from '@/components/business/types'
import { Plus, UserCog } from 'lucide-vue-next'
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import UserProfileFields from '@/components/business/UserProfileFields.vue'
import UserRoleChart from '@/components/business/UserRoleChart.vue'
import AuditLogTable from '@/components/business/AuditLogTable.vue'
import ErrorState from '@/components/business/ErrorState.vue'
import { profileForm, validateProfile } from '@/utils/user-profile'
import { getAudits } from '@/services/audits'
import { useSessionStore } from '@/stores/session'

import { ConfirmDialog, DataTable, PageHeader, PanelSection, StatusPill } from '@/components/business'
import { ApiError, toErrorMessage } from '@/services/api'
import { userApi } from '@/services/users'
import type { AuditLog, PageResponse, RoleCode, UserAccount, UserStatistics } from '@/types/api'
import { formatDateTime } from '@/utils/format'

const props = withDefaults(defineProps<{ section?: 'accounts' | 'overview' }>(), { section: 'accounts' })
const session = useSessionStore()

const allRoles: Array<{ value: RoleCode; label: string }> = [
  { value: 'ADMIN', label: '管理员' },
  { value: 'DATA_OPERATOR', label: '数据运营人员' },
  { value: 'RESEARCHER', label: '科研用户' },
]
const users = ref<PageResponse<UserAccount>>({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const createError = ref('')
const rolesError = ref('')
const passwordError = ref('')
const createVisible = ref(false)
const rolesVisible = ref(false)
const passwordVisible = ref(false)
const selected = ref<UserAccount | null>(null)
const confirmToggle = ref<UserAccount | null>(null)
const createForm = reactive({ username: '', password: '', roles: ['RESEARCHER'] as RoleCode[] })
const roleForm = ref<RoleCode[]>([])
const newPassword = ref('')
const createProfile = ref(profileForm())
const editProfile = ref(profileForm())
const editUser = ref<UserAccount | null>(null)
const editRoles = ref<RoleCode[]>([])
const editStatus = ref<UserAccount['status']>('ACTIVE')
const editVisible = ref(false)
const editError = ref('')
const editConflict = ref(false)
const statistics = ref<UserStatistics | null>(null)
const statisticsError = ref('')
const statisticsLoading = ref(false)
const recentLogs = ref<AuditLog[]>([])
const logsError = ref('')
const logsLoading = ref(false)
let listRequest: AbortController | undefined
let statisticsRequest: AbortController | undefined
let logsRequest: AbortController | undefined
let disposed = false

function userError(error: unknown): string {
  return error instanceof ApiError && error.status === 409 && error.code === 'RESOURCE_CONFLICT'
    ? error.message : toErrorMessage(error)
}

/** 三个区域独立加载，失败不覆盖成零值，过期响应不更新界面。 */
async function loadStatistics(): Promise<void> {
  if (disposed) return
  statisticsRequest?.abort()
  const request = statisticsRequest = new AbortController()
  statisticsLoading.value = true
  statisticsError.value = ''
  try { const data = await userApi.statistics(request.signal); if (!request.signal.aborted) statistics.value = data }
  catch (error) { if (!request.signal.aborted) statisticsError.value = userError(error) }
  finally { if (!request.signal.aborted) statisticsLoading.value = false }
}

async function loadLogs(): Promise<void> {
  if (disposed) return
  logsRequest?.abort()
  const request = logsRequest = new AbortController()
  logsLoading.value = true
  logsError.value = ''
  try { const data = await getAudits({ category: 'LOGIN' }, 0, 10, request.signal); if (!request.signal.aborted) recentLogs.value = data.items ?? [] }
  catch (error) { if (!request.signal.aborted) logsError.value = userError(error) }
  finally { if (!request.signal.aborted) logsLoading.value = false }
}

function openEdit(user: UserAccount): void {
  editUser.value = user
  editProfile.value = profileForm(user)
  editRoles.value = [...user.roles]
  editStatus.value = user.status
  editError.value = ''
  editConflict.value = false
  editVisible.value = true
}

/** 用户确认后重新读取当前页记录，避免用旧版本覆盖并发修改。 */
async function reloadEditedUser(): Promise<void> {
  if (!editUser.value) return
  await load(users.value.page)
  if (disposed) return
  const latest = users.value.items.find((user) => user.id === editUser.value?.id)
  if (!errorMessage.value && latest) openEdit(latest)
}

async function saveUser(): Promise<void> {
  const selectedUser = editUser.value
  if (!selectedUser || saving.value) return
  editError.value = validateProfile(editProfile.value)
  if (!editRoles.value.length) editError.value = '至少选择一个角色'
  if (editError.value) return
  saving.value = true
  try {
    await userApi.update(selectedUser, editProfile.value, editRoles.value, editStatus.value)
    if (disposed) return
    editVisible.value = false
    ElMessage.success('用户已更新')
    await Promise.all([load(users.value.page), loadStatistics()])
  } catch (error) {
    editError.value = userError(error)
    editConflict.value = error instanceof ApiError && error.code === 'VERSION_CONFLICT'
  } finally { saving.value = false }
}

const columns: DataTableColumn<UserAccount>[] = [
  { accessorKey: 'username', header: '用户名', enableSorting: false },
  { id: 'realName', accessorFn: (row) => row.realName || '--', header: '姓名', enableSorting: false },
  { id: 'organization', accessorFn: (row) => row.organization || '--', header: '所属单位', enableSorting: false },
  { id: 'roles', accessorFn: (row) => row.roles.join('，'), header: '角色', enableSorting: false },
  { id: 'status', accessorFn: (row) => row.status, header: '状态', enableSorting: false, meta: { width: '130px' } },
  { id: 'credentialsChangedAt', accessorFn: (row) => formatDateTime(row.credentialsChangedAt), header: '凭据更新时间', enableSorting: false, meta: { width: '170px' } },
  { id: 'updatedAt', accessorFn: (row) => formatDateTime(row.updatedAt), header: '更新时间', enableSorting: false, meta: { width: '170px' } },
  { id: 'actions', header: '操作', enableSorting: false, meta: { width: '230px' } },
]

function statusMeta(status: UserAccount['status']): { pill: string; label: string } {
  if (status === 'ACTIVE') return { pill: 'ACTIVE', label: '启用' }
  if (status === 'PASSWORD_RESET_REQUIRED') return { pill: 'WARNING', label: '待重置密码' }
  return { pill: 'DISABLED', label: '停用' }
}

function toggleRole(list: RoleCode[], role: RoleCode): RoleCode[] {
  return list.includes(role) ? list.filter((item) => item !== role) : [...list, role]
}

async function load(page = 0): Promise<void> {
  if (disposed) return
  listRequest?.abort()
  const request = listRequest = new AbortController()
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await userApi.page(page, users.value.size, request.signal)
    if (!request.signal.aborted) users.value = data
  } catch (error) {
    if (!request.signal.aborted) errorMessage.value = userError(error)
  } finally {
    if (!request.signal.aborted) loading.value = false
  }
}

function openCreate(): void {
  createError.value = ''
  Object.assign(createForm, { username: '', password: '', roles: ['RESEARCHER'] })
  createProfile.value = profileForm()
  createVisible.value = true
}

async function createUser(): Promise<void> {
  if (saving.value) return
  createError.value = validateProfile(createProfile.value, true)
  if (createError.value) return
  if (createForm.username.trim().length < 3 || createForm.roles.length === 0) {
    createError.value = '用户名至少 3 个字符且必须选择角色'
    return
  }
  if (createForm.password.length < 12) {
    createError.value = '密码长度至少为 12 个字符'
    return
  }
  saving.value = true
  try {
    await userApi.create(createForm.username.trim(), createForm.password, createForm.roles, createProfile.value)
    if (disposed) return
    createVisible.value = false
    Object.assign(createForm, { username: '', password: '', roles: ['RESEARCHER'] })
    ElMessage.success('用户已创建')
    await Promise.all([load(), loadStatistics()])
  } catch (error) {
    createError.value = userError(error)
  } finally {
    saving.value = false
  }
}

async function applyToggle(): Promise<void> {
  const user = confirmToggle.value
  if (!user || saving.value) return
  saving.value = true
  try {
    await userApi.setEnabled(user, user.status !== 'ACTIVE')
    if (disposed) return
    ElMessage.success('用户状态已更新')
    confirmToggle.value = null
    await Promise.all([load(users.value.page), loadStatistics()])
  } catch (error) {
    confirmToggle.value = null
    errorMessage.value = userError(error)
  } finally {
    saving.value = false
  }
}

function openRoles(user: UserAccount): void {
  selected.value = user
  roleForm.value = [...user.roles]
  rolesError.value = ''
  rolesVisible.value = true
}

async function saveRoles(): Promise<void> {
  if (saving.value) return
  rolesError.value = ''
  if (!selected.value || roleForm.value.length === 0) {
    rolesError.value = '至少选择一个角色'
    return
  }
  saving.value = true
  try {
    await userApi.replaceRoles(selected.value, roleForm.value)
    if (disposed) return
    rolesVisible.value = false
    ElMessage.success('用户角色已更新')
    await Promise.all([load(users.value.page), loadStatistics()])
  } catch (error) {
    rolesError.value = userError(error)
  } finally {
    saving.value = false
  }
}

function openPassword(user: UserAccount): void {
  selected.value = user
  newPassword.value = ''
  passwordError.value = ''
  passwordVisible.value = true
}

async function resetPassword(): Promise<void> {
  if (saving.value) return
  passwordError.value = ''
  if (!selected.value) return
  if (newPassword.value.length < 12) {
    passwordError.value = '密码长度至少为 12 个字符'
    return
  }
  saving.value = true
  try {
    await userApi.resetPassword(selected.value, newPassword.value)
    if (disposed) return
    passwordVisible.value = false
    ElMessage.success('密码已重置')
    await Promise.all([load(users.value.page), loadStatistics()])
  } catch (error) {
    passwordError.value = userError(error)
  } finally {
    saving.value = false
  }
}

onMounted(() => { void load(); void loadStatistics(); void loadLogs() })
onBeforeUnmount(() => {
  disposed = true
  listRequest?.abort()
  statisticsRequest?.abort()
  logsRequest?.abort()
})
</script>

<template>
  <section class="page-stack">
    <PageHeader
      :title="props.section === 'overview' ? '账号概况' : '用户管理'"
      :description="props.section === 'overview' ? '查看账号角色分布与最近登录记录。' : '创建内部账号、维护角色与启用状态，并在必要时重置登录凭据。'"
    >
      <template #actions>
        <ElButton @click="openCreate" type="primary"><Plus class="size-4" />新增用户</ElButton>
      </template>
    </PageHeader>
    <nav class="workspace-tabs" aria-label="账号管理内容">
      <RouterLink to="/users" :aria-current="props.section === 'accounts' ? 'page' : undefined">系统账号</RouterLink>
      <RouterLink to="/users/overview" :aria-current="props.section === 'overview' ? 'page' : undefined">账号概况</RouterLink>
    </nav>

    <ElAlert v-if="errorMessage" type="error" :closable="false" show-icon><template #title>{{ errorMessage }}</template></ElAlert>

    <div v-if="props.section === 'overview'" class="users-overview workspace-grid grid gap-4 xl:grid-cols-3">
      <PanelSection title="用户类型分布" class="workspace-panel xl:col-span-1">
        <template #actions><ElButton size="small" :disabled="statisticsLoading" @click="loadStatistics" text>刷新</ElButton></template>
        <ErrorState v-if="statisticsError" :message="statisticsError" retryable @retry="loadStatistics" />
        <ElSkeleton animated v-else-if="statisticsLoading && !statistics" class="h-80 w-full" />
        <div v-else-if="statistics" :aria-busy="statisticsLoading" :class="statisticsLoading ? 'opacity-60' : ''"><UserRoleChart :statistics="statistics" /></div>
      </PanelSection>
      <PanelSection title="最近登录日志" subtitle="最近 10 条登录、失败及退出记录" class="workspace-panel min-w-0 xl:col-span-2">
        <template #actions>
          <ElButton size="small" :disabled="logsLoading" @click="loadLogs" text>刷新</ElButton>
          <ElButton as-child size="small" plain><RouterLink to="/logs?category=LOGIN">查看全部</RouterLink></ElButton>
        </template>
        <ErrorState v-if="logsError" :message="logsError" retryable @retry="loadLogs" />
        <AuditLogTable v-else :items="recentLogs" :loading="logsLoading" compact fill />
      </PanelSection>
    </div>

    <PanelSection v-if="props.section === 'accounts'" title="系统账号" :subtitle="`共 ${users.totalElements} 个`" class="workspace-panel" body-class="flex min-h-0 flex-col">
      <template #actions><UserCog class="size-4 text-muted-foreground" aria-hidden="true" /></template>
      <DataTable fill class="[&_table]:min-w-[1120px] [&_td]:whitespace-nowrap"
        :columns="columns" :data="users.items" :loading="loading"
        :page="users.page" :size="users.size" :total="users.totalElements"
        empty-text="暂无用户" :get-row-id="(row) => String(row.id)" @update:page="load"
      >
        <template #cell-roles="{ row }">
          <div class="flex flex-wrap gap-1">
            <ElTag v-for="role in row.roles" :key="role" type="info" size="small">{{ role }}</ElTag>
          </div>
        </template>
        <template #cell-status="{ row }">
          <StatusPill :status="statusMeta(row.status).pill" :label="statusMeta(row.status).label" />
        </template>
        <template #cell-actions="{ row }">
          <div class="flex flex-wrap items-center gap-3">
            <ElButton size="small" class="h-auto p-0" @click="openEdit(row)" link type="primary">编辑</ElButton>
            <ElButton size="small" class="h-auto p-0" @click="openRoles(row)" link type="primary">角色</ElButton>
            <ElButton size="small" class="h-auto p-0" @click="openPassword(row)" link type="primary">重置密码</ElButton>
            <ElButton size="small" class="h-auto p-0"
              :class="row.status === 'ACTIVE' ? 'text-destructive' : 'text-success'"
              @click="confirmToggle = row"
             link type="primary">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</ElButton>
          </div>
        </template>
      </DataTable>
    </PanelSection>

    <!-- 新增用户 -->
    <ElDialog v-model="createVisible" width="min(576px, calc(100vw - 32px))" append-to-body destroy-on-close class="aacv-form-dialog" body-class="aacv-dialog-body">

          <template #header="{ titleId }"><h2 :id="titleId">新增用户</h2></template>
          <p class="mb-4 text-sm text-muted-foreground">创建内部账号并分配角色。</p>

        <form class="grid gap-4" novalidate @submit.prevent="createUser">
          <ElAlert v-if="createError" type="error" :closable="false" show-icon><template #title>{{ createError }}</template></ElAlert>
          <FormField for="newUsername" label="用户名"><ElInput id="newUsername" v-model="createForm.username" :maxlength="64"  /></FormField>
          <FormField for="newPassword" label="初始密码"><ElInput id="newPassword" v-model="createForm.password" type="password" :maxlength="128" autocomplete="new-password"  /></FormField>
          <FormField label="角色">
            <div class="flex flex-wrap gap-4">
              <label v-for="role in allRoles" :key="role.value" class="flex items-center gap-2 text-sm">
                <ElCheckbox
                  :model-value="createForm.roles.includes(role.value)"
                  @update:model-value="createForm.roles = toggleRole(createForm.roles, role.value)"
                />
                {{ role.label }}
              </label>
            </div>
          </FormField>
          <UserProfileFields v-model="createProfile" prefix="create" require-name :disabled="saving" />
          <ElAlert type="warning" :closable="false" show-icon><template #title>初始密码至少 12 个字符，请通过安全渠道交付给用户。</template></ElAlert>
          <div class="mt-4 flex flex-wrap justify-end gap-2">
            <ElButton native-type="button" @click="createVisible = false" plain>取消</ElButton>
            <ElButton native-type="submit" :loading="saving" type="primary">创建用户</ElButton>
          </div>
        </form>
      </ElDialog>

    <ElDrawer v-model="editVisible" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" size="min(576px, 100vw)" append-to-body destroy-on-close class="aacv-drawer">
          <template #header="{ titleId }"><h2 :id="titleId">编辑用户 · {{ editUser?.username }}</h2></template>
          <p class="mb-4 text-sm text-muted-foreground">资料修改保持登录；角色或状态变化将使已有登录失效。</p>

        <form class="flex min-h-0 flex-1 flex-col" novalidate @submit.prevent="saveUser">
          <div class="flex-1 space-y-5 overflow-y-auto px-6 pb-6">
            <ElAlert v-if="editError" type="error" :closable="false" show-icon><template #title>{{ editError }}</template></ElAlert>
            <ElButton v-if="editConflict" native-type="button" :disabled="loading" @click="reloadEditedUser" plain>重新加载并替换表单</ElButton>
            <FormField for="edit-username" label="用户名"><ElInput id="edit-username" :model-value="editUser?.username" readonly  /></FormField>
            <UserProfileFields v-model="editProfile" prefix="edit" :disabled="saving" />
            <FormField label="角色">
              <div class="flex flex-wrap gap-4">
                <label v-for="role in allRoles" :key="role.value" class="flex items-center gap-2 text-sm">
                  <ElCheckbox :model-value="editRoles.includes(role.value)" :disabled="saving || (editUser?.id === session.user?.id && role.value === 'ADMIN')"
                    @update:model-value="editRoles = toggleRole(editRoles, role.value)" />{{ role.label }}
                </label>
              </div>
            </FormField>
            <FormField for="edit-status" label="账号状态">
              <ElSelect id="edit-status" v-model="editStatus" class="w-full" :disabled="saving || editUser?.id === session.user?.id">
                <ElOption value="ACTIVE" label="启用" /><ElOption value="DISABLED" label="停用" />
                <ElOption v-if="editUser?.status === 'PASSWORD_RESET_REQUIRED'" value="PASSWORD_RESET_REQUIRED" label="待重置密码" />
              </ElSelect>
            </FormField>
            <p v-if="editUser?.id === session.user?.id" class="text-xs text-muted-foreground">不能停用自己或移除自己的管理员角色。</p>
          </div>
          <div class="flex justify-end gap-2 border-t bg-card p-4">
            <ElButton native-type="button" :disabled="saving" @click="editVisible = false" plain>取消</ElButton>
            <ElButton native-type="submit" :loading="saving" :disabled="editConflict" type="primary">保存修改</ElButton>
          </div>
        </form>
      </ElDrawer>

    <!-- 调整角色 -->
    <ElDialog v-model="rolesVisible" width="min(448px, calc(100vw - 32px))" append-to-body destroy-on-close class="aacv-form-dialog" body-class="aacv-dialog-body">
        <template #header="{ titleId }"><h2 :id="titleId">调整角色 · {{ selected?.username }}</h2></template>
        <ElAlert v-if="rolesError" type="error" :closable="false" show-icon><template #title>{{ rolesError }}</template></ElAlert>
        <div class="grid gap-3">
          <label v-for="role in allRoles" :key="role.value" class="flex items-center gap-2 text-sm">
            <ElCheckbox
              :model-value="roleForm.includes(role.value)"
              @update:model-value="roleForm = toggleRole(roleForm, role.value)"
            />
            {{ role.label }}（{{ role.value }}）
          </label>
        </div>
        <div class="mt-4 flex flex-wrap justify-end gap-2">
          <ElButton @click="rolesVisible = false" plain>取消</ElButton>
          <ElButton :loading="saving" @click="saveRoles" type="primary">保存角色</ElButton>
        </div>
      </ElDialog>

    <!-- 重置密码 -->
    <ElDialog v-model="passwordVisible" width="min(448px, calc(100vw - 32px))" append-to-body destroy-on-close class="aacv-form-dialog" body-class="aacv-dialog-body">
        <template #header="{ titleId }"><h2 :id="titleId">重置密码 · {{ selected?.username }}</h2></template>
        <ElAlert v-if="passwordError" type="error" :closable="false" show-icon><template #title>{{ passwordError }}</template></ElAlert>
        <FormField for="resetPassword" label="新密码">
          <ElInput id="resetPassword" v-model="newPassword" type="password" :maxlength="128" autocomplete="new-password" placeholder="输入至少 12 个字符的新密码"  />
        </FormField>
        <div class="mt-4 flex flex-wrap justify-end gap-2">
          <ElButton @click="passwordVisible = false" plain>取消</ElButton>
          <ElButton :loading="saving" @click="resetPassword" type="primary">确认重置</ElButton>
        </div>
      </ElDialog>

    <!-- 状态确认 -->
    <ConfirmDialog
      :open="Boolean(confirmToggle)"
      :loading="saving"
      title="账号状态确认"
      :description="confirmToggle ? `确认${confirmToggle.status === 'ACTIVE' ? '停用' : '启用'}用户 ${confirmToggle.username}？` : ''"
      :confirm-text="confirmToggle?.status === 'ACTIVE' ? '停用' : '启用'"
      :destructive="confirmToggle?.status === 'ACTIVE'"
      @update:open="(v) => { if (!v) confirmToggle = null }"
      @confirm="applyToggle"
    />
  </section>
</template>

<style scoped>
.users-overview {
  grid-template-rows: minmax(0, 1fr);
}

@media (max-width: 1279px) {
  .users-overview {
    grid-template-rows: minmax(0, 1fr) minmax(0, 1fr);
  }
}
</style>
