export interface SessionUser {
  id: number
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
}

let csrfToken: string | null = null
let currentUser: SessionUser | null = null

export function getCsrfToken(): string | null {
  return csrfToken
}

export function setCsrfToken(token: string | null) {
  csrfToken = token
}

export function getCurrentUser(): SessionUser | null {
  return currentUser
}

export function setCurrentUser(user: SessionUser | null) {
  currentUser = user
}

export function clearSession() {
  csrfToken = null
  currentUser = null
}

export function hasRole(role: string): boolean {
  return currentUser?.roles.includes(role) ?? false
}

export function hasPermission(permission: string): boolean {
  return currentUser?.permissions.includes(permission) ?? false
}
