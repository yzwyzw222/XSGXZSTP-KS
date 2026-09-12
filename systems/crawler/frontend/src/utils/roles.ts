import type { RoleCode } from '@/types/api'

export const roleOptions: Array<{ value: RoleCode; label: string }> = [
  { value: 'ADMIN', label: '管理员' },
  { value: 'DATA_OPERATOR', label: '数据运营人员' },
  { value: 'RESEARCHER', label: '科研用户' },
]

export function roleLabel(role: string): string {
  return roleOptions.find(item => item.value === role)?.label ?? role
}
