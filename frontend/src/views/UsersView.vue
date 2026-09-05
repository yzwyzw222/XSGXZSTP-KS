<script setup lang="ts">
import type { ColumnDef } from '@tanstack/vue-table'
import { Plus, UserCog } from 'lucide-vue-next'
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import UserProfileFields from '@/components/business/UserProfileFields.vue'
import UserRoleChart from '@/components/business/UserRoleChart.vue'
import AuditLogTable from '@/components/business/AuditLogTable.vue'
import ErrorState from '@/components/business/ErrorState.vue'
import { Skeleton } from '@/components/ui/skeleton'
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from '@/components/ui/sheet'
import { profileForm, validateProfile } from '@/utils/user-profile'
import { getAudits } from '@/services/audits'
import { session } from '@/services/session'

import { ConfirmDialog, DataTable, PageHeader, PanelSection, StatusPill } from '@/components/business'
import { Alert, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { FormItem, FormLabel } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { toast } from '@/components/ui/sonner'
import { ApiError, toErrorMessage } from '@/services/api'
import { userApi } from '@/services/users'
import type { AuditLog, PageResponse, RoleCode, UserAccount, UserStatistics } from '@/types/api'
import { formatDateTime } from '@/utils/format'

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
    toast.success('用户已更新')
    await Promise.all([load(users.value.page), loadStatistics()])
  } catch (error) {
    editError.value = userError(error)
    editConflict.value = error instanceof ApiError && error.code === 'VERSION_CONFLICT'
  } finally { saving.value = false }
}

const columns: ColumnDef<UserAccount, any>[] = [
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
    toast.success('用户已创建')
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
    toast.success('用户状态已更新')
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
    toast.success('用户角色已更新')
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
    toast.success('密码已重置')
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
      title="用户管理"
      description="创建内部账号、维护角色与启用状态，并在必要时重置登录凭据。"
    >
      <template #actions>
        <Button @click="openCreate"><Plus class="size-4" />新增用户</Button>
      </template>
    </PageHeader>

    <Alert v-if="errorMessage" variant="destructive"><AlertTitle>{{ errorMessage }}</AlertTitle></Alert>

    <div class="grid gap-4 xl:grid-cols-3">
      <PanelSection title="用户类型分布" class="xl:col-span-1">
        <template #actions><Button variant="ghost" size="sm" :disabled="statisticsLoading" @click="loadStatistics">刷新</Button></template>
        <ErrorState v-if="statisticsError" :message="statisticsError" retryable @retry="loadStatistics" />
        <Skeleton v-else-if="statisticsLoading && !statistics" class="h-80 w-full" />
        <div v-else-if="statistics" :aria-busy="statisticsLoading" :class="statisticsLoading ? 'opacity-60' : ''"><UserRoleChart :statistics="statistics" /></div>
      </PanelSection>
      <PanelSection title="最近登录日志" subtitle="最近 10 条登录、失败及退出记录" class="min-w-0 xl:col-span-2">
        <template #actions>
          <Button variant="ghost" size="sm" :disabled="logsLoading" @click="loadLogs">刷新</Button>
          <Button as-child variant="outline" size="sm"><RouterLink to="/logs?category=LOGIN">查看全部</RouterLink></Button>
        </template>
        <ErrorState v-if="logsError" :message="logsError" retryable @retry="loadLogs" />
        <AuditLogTable v-else :items="recentLogs" :loading="logsLoading" compact />
      </PanelSection>
    </div>

    <PanelSection title="系统账号" :subtitle="`共 ${users.totalElements} 个`">
      <template #actions><UserCog class="size-4 text-muted-foreground" aria-hidden="true" /></template>
      <DataTable class="[&_table]:min-w-[1120px] [&_td]:whitespace-nowrap"
        :columns="columns" :data="users.items" :loading="loading"
        :page="users.page" :size="users.size" :total="users.totalElements"
        empty-text="暂无用户" :get-row-id="(row) => String(row.id)" @update:page="load"
      >
        <template #cell-roles="{ row }">
          <div class="flex flex-wrap gap-1">
            <Badge v-for="role in row.roles" :key="role" variant="subtle">{{ role }}</Badge>
          </div>
        </template>
        <template #cell-status="{ row }">
          <StatusPill :status="statusMeta(row.status).pill" :label="statusMeta(row.status).label" />
        </template>
        <template #cell-actions="{ row }">
          <div class="flex flex-wrap items-center gap-3">
            <Button variant="link" size="sm" class="h-auto p-0" @click="openEdit(row)">编辑</Button>
            <Button variant="link" size="sm" class="h-auto p-0" @click="openRoles(row)">角色</Button>
            <Button variant="link" size="sm" class="h-auto p-0" @click="openPassword(row)">重置密码</Button>
            <Button
              variant="link" size="sm" class="h-auto p-0"
              :class="row.status === 'ACTIVE' ? 'text-destructive' : 'text-success'"
              @click="confirmToggle = row"
            >{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</Button>
          </div>
        </template>
      </DataTable>
    </PanelSection>

    <!-- 新增用户 -->
    <Dialog v-model:open="createVisible">
      <DialogContent class="max-h-[90dvh] overflow-y-auto sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>新增用户</DialogTitle>
          <DialogDescription>创建内部账号并分配角色。</DialogDescription>
        </DialogHeader>
        <form class="grid gap-4" novalidate @submit.prevent="createUser">
          <Alert v-if="createError" variant="destructive"><AlertTitle>{{ createError }}</AlertTitle></Alert>
          <FormItem><FormLabel for="newUsername">用户名</FormLabel><Input id="newUsername" v-model="createForm.username" :maxlength="64" /></FormItem>
          <FormItem><FormLabel for="newPassword">初始密码</FormLabel><Input id="newPassword" v-model="createForm.password" type="password" :maxlength="128" autocomplete="new-password" /></FormItem>
          <FormItem>
            <FormLabel>角色</FormLabel>
            <div class="flex flex-wrap gap-4">
              <label v-for="role in allRoles" :key="role.value" class="flex items-center gap-2 text-sm">
                <Checkbox
                  :model-value="createForm.roles.includes(role.value)"
                  @update:model-value="createForm.roles = toggleRole(createForm.roles, role.value)"
                />
                {{ role.label }}
              </label>
            </div>
          </FormItem>
          <UserProfileFields v-model="createProfile" prefix="create" require-name :disabled="saving" />
          <Alert variant="warning"><AlertTitle>初始密码至少 12 个字符，请通过安全渠道交付给用户。</AlertTitle></Alert>
          <DialogFooter>
            <Button type="button" variant="outline" @click="createVisible = false">取消</Button>
            <Button type="submit" :loading="saving">创建用户</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>

    <Sheet :open="editVisible" @update:open="(open) => { if (!saving) editVisible = open }">
      <SheetContent class="w-full sm:max-w-xl" @interact-outside="(event) => { if (saving) event.preventDefault() }" @escape-key-down="(event) => { if (saving) event.preventDefault() }">
        <SheetHeader class="border-b p-6">
          <SheetTitle>编辑用户 · {{ editUser?.username }}</SheetTitle>
          <SheetDescription>资料修改保持登录；角色或状态变化将使已有登录失效。</SheetDescription>
        </SheetHeader>
        <form class="flex min-h-0 flex-1 flex-col" novalidate @submit.prevent="saveUser">
          <div class="flex-1 space-y-5 overflow-y-auto px-6 pb-6">
            <Alert v-if="editError" variant="destructive"><AlertTitle>{{ editError }}</AlertTitle></Alert>
            <Button v-if="editConflict" type="button" variant="outline" :disabled="loading" @click="reloadEditedUser">重新加载并替换表单</Button>
            <FormItem><FormLabel for="edit-username">用户名</FormLabel><Input id="edit-username" :model-value="editUser?.username" readonly /></FormItem>
            <UserProfileFields v-model="editProfile" prefix="edit" :disabled="saving" />
            <FormItem><FormLabel>角色</FormLabel>
              <div class="flex flex-wrap gap-4">
                <label v-for="role in allRoles" :key="role.value" class="flex items-center gap-2 text-sm">
                  <Checkbox :model-value="editRoles.includes(role.value)" :disabled="saving || (editUser?.id === session.user?.id && role.value === 'ADMIN')"
                    @update:model-value="editRoles = toggleRole(editRoles, role.value)" />{{ role.label }}
                </label>
              </div>
            </FormItem>
            <FormItem><FormLabel for="edit-status">账号状态</FormLabel>
              <select id="edit-status" v-model="editStatus" class="h-9 w-full rounded-md border border-input bg-background px-3 text-sm" :disabled="saving || editUser?.id === session.user?.id">
                <option value="ACTIVE">启用</option><option value="DISABLED">停用</option>
                <option v-if="editUser?.status === 'PASSWORD_RESET_REQUIRED'" value="PASSWORD_RESET_REQUIRED">待重置密码</option>
              </select>
            </FormItem>
            <p v-if="editUser?.id === session.user?.id" class="text-xs text-muted-foreground">不能停用自己或移除自己的管理员角色。</p>
          </div>
          <div class="flex justify-end gap-2 border-t bg-card p-4">
            <Button type="button" variant="outline" :disabled="saving" @click="editVisible = false">取消</Button>
            <Button type="submit" :loading="saving" :disabled="editConflict">保存修改</Button>
          </div>
        </form>
      </SheetContent>
    </Sheet>

    <!-- 调整角色 -->
    <Dialog v-model:open="rolesVisible">
      <DialogContent class="sm:max-w-md">
        <DialogHeader><DialogTitle>调整角色 · {{ selected?.username }}</DialogTitle></DialogHeader>
        <Alert v-if="rolesError" variant="destructive"><AlertTitle>{{ rolesError }}</AlertTitle></Alert>
        <div class="grid gap-3">
          <label v-for="role in allRoles" :key="role.value" class="flex items-center gap-2 text-sm">
            <Checkbox
              :model-value="roleForm.includes(role.value)"
              @update:model-value="roleForm = toggleRole(roleForm, role.value)"
            />
            {{ role.label }}（{{ role.value }}）
          </label>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="rolesVisible = false">取消</Button>
          <Button :loading="saving" @click="saveRoles">保存角色</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- 重置密码 -->
    <Dialog v-model:open="passwordVisible">
      <DialogContent class="sm:max-w-md">
        <DialogHeader><DialogTitle>重置密码 · {{ selected?.username }}</DialogTitle></DialogHeader>
        <Alert v-if="passwordError" variant="destructive"><AlertTitle>{{ passwordError }}</AlertTitle></Alert>
        <FormItem>
          <FormLabel for="resetPassword">新密码</FormLabel>
          <Input id="resetPassword" v-model="newPassword" type="password" :maxlength="128" autocomplete="new-password" placeholder="输入至少 12 个字符的新密码" />
        </FormItem>
        <DialogFooter>
          <Button variant="outline" @click="passwordVisible = false">取消</Button>
          <Button :loading="saving" @click="resetPassword">确认重置</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

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
