<script setup lang="ts">
import type { ColumnDef } from '@tanstack/vue-table'
import { Plus, UserCog } from 'lucide-vue-next'
import { onMounted, reactive, ref } from 'vue'

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
const confirmToggle = ref<UserAccount | null>(null)
const createForm = reactive({ username: '', password: '', roles: ['RESEARCHER'] as RoleCode[] })
const roleForm = ref<RoleCode[]>([])
const newPassword = ref('')

const columns: ColumnDef<UserAccount, any>[] = [
  { accessorKey: 'username', header: '用户名', enableSorting: false },
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
    toast.success('用户已创建')
    await load()
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    saving.value = false
  }
}

async function applyToggle(): Promise<void> {
  const user = confirmToggle.value
  if (!user) return
  try {
    await userApi.setEnabled(user, user.status !== 'ACTIVE')
    toast.success('用户状态已更新')
    confirmToggle.value = null
    await load(users.value.page)
  } catch (error) {
    confirmToggle.value = null
    errorMessage.value = toErrorMessage(error)
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
    toast.success('用户角色已更新')
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
    toast.success('密码已重置')
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
    <PageHeader
      title="用户管理"
      description="创建内部账号、维护角色与启用状态，并在必要时重置登录凭据。"
    >
      <template #actions>
        <Button @click="createVisible = true"><Plus class="size-4" />新增用户</Button>
      </template>
    </PageHeader>

    <Alert v-if="errorMessage" variant="destructive"><AlertTitle>{{ errorMessage }}</AlertTitle></Alert>

    <PanelSection title="系统账号" :subtitle="`共 ${users.totalElements} 个`">
      <template #actions><UserCog class="size-4 text-muted-foreground" aria-hidden="true" /></template>
      <DataTable
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
          <div class="flex flex-wrap items-center gap-1">
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
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>新增用户</DialogTitle>
          <DialogDescription>创建内部账号并分配角色。</DialogDescription>
        </DialogHeader>
        <form class="grid gap-4" novalidate @submit.prevent="createUser">
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
          <Alert variant="warning"><AlertTitle>初始密码至少 12 个字符，请通过安全渠道交付给用户。</AlertTitle></Alert>
          <DialogFooter>
            <Button type="button" variant="outline" @click="createVisible = false">取消</Button>
            <Button type="submit" :loading="saving">创建用户</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>

    <!-- 调整角色 -->
    <Dialog v-model:open="rolesVisible">
      <DialogContent class="sm:max-w-md">
        <DialogHeader><DialogTitle>调整角色 · {{ selected?.username }}</DialogTitle></DialogHeader>
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
      title="账号状态确认"
      :description="confirmToggle ? `确认${confirmToggle.status === 'ACTIVE' ? '停用' : '启用'}用户 ${confirmToggle.username}？` : ''"
      :confirm-text="confirmToggle?.status === 'ACTIVE' ? '停用' : '启用'"
      :destructive="confirmToggle?.status === 'ACTIVE'"
      @update:open="(v) => { if (!v) confirmToggle = null }"
      @confirm="applyToggle"
    />
  </section>
</template>
