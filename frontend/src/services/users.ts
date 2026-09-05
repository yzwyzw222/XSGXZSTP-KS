import { api } from '@/services/api'
import type { PageResponse, RoleCode, UserAccount, UserProfile, UserStatistics } from '@/types/api'

/** 用户管理请求沿用现有会话、CSRF 和分页契约。 */
export const userApi = {
  page: (page = 0, size = 20, signal?: AbortSignal) =>
    api.get<PageResponse<UserAccount>>(`/api/v1/users?page=${page}&size=${size}`, { signal }),
  statistics: (signal?: AbortSignal) => api.get<UserStatistics>('/api/v1/users/statistics', { signal }),
  create: (username: string, password: string, roles: RoleCode[], profile: UserProfile = {}) =>
    api.post<UserAccount>('/api/v1/users', { username, password, roles, ...profile }),
  update: (user: UserAccount, profile: UserProfile, roles: RoleCode[], status: UserAccount['status']) =>
    api.put<UserAccount>(`/api/v1/users/${user.id}`, { ...profile, roles, status, version: user.version }),
  setEnabled: (user: UserAccount, enabled: boolean) =>
    api.post<UserAccount>(`/api/v1/users/${user.id}/${enabled ? 'enable' : 'disable'}`, { version: user.version }),
  resetPassword: (user: UserAccount, newPassword: string) =>
    api.post<UserAccount>(`/api/v1/users/${user.id}/reset-password`, { version: user.version, newPassword }),
  replaceRoles: (user: UserAccount, roles: RoleCode[]) =>
    api.post<UserAccount>(`/api/v1/users/${user.id}/roles`, { version: user.version, roles }),
}
