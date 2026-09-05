import type { UserProfile } from '@/types/api'

export const profileFields = [
  { key: 'realName', label: '姓名', limit: 64, type: 'text' },
  { key: 'email', label: '邮箱', limit: 254, type: 'email' },
  { key: 'phone', label: '联系电话', limit: 32, type: 'tel' },
  { key: 'organization', label: '所属单位', limit: 128, type: 'text' },
  { key: 'department', label: '部门/院系', limit: 128, type: 'text' },
  { key: 'remark', label: '备注', limit: 500, type: 'text' },
] as const

export type UserProfileForm = Record<typeof profileFields[number]['key'], string>

/** 把旧账号未提供的资料转换为表单空值，不伪造个人信息。 */
export function profileForm(user: UserProfile = {}): UserProfileForm {
  return Object.fromEntries(profileFields.map(({ key }) => [key, user[key] ?? ''])) as UserProfileForm
}

/** 新建页面要求姓名；旧账号允许资料暂缺，前后端共同限制输入。 */
export function validateProfile(profile: UserProfileForm, requireName = false): string {
  if (requireName && !profile.realName.trim()) return '请输入姓名'
  for (const field of profileFields) {
    const value = profile[field.key].trim()
    if (value.length > field.limit) return `${field.label}不能超过 ${field.limit} 个字符`
    const invalidControl = field.key === 'remark' ? /[\x00-\x08\x0b\x0c\x0e-\x1f\x7f-\x9f]/ : /[\x00-\x1f\x7f-\x9f]/
    if (invalidControl.test(value)) return `${field.label}包含无效字符`
  }
  if (profile.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(profile.email.trim())) return '邮箱格式无效'
  if (profile.phone.trim() && (!/^\+?[0-9() .-]+$/.test(profile.phone.trim()) || !/[0-9]/.test(profile.phone))) return '联系电话格式无效'
  return ''
}
