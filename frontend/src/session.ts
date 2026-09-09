/**
 * 会话状态模块：CSRF Token 与当前登录用户的【内存级】存储。
 * 安全约定（规范要求）：
 *  - Session ID 由浏览器 HttpOnly Cookie 托管，前端代码接触不到、也不读取；
 *  - CSRF Token 只保存在模块私有变量中，绝不写入 localStorage / sessionStorage；
 *  - 收到 401 或用户退出时调用 clearSession() 一次性清空。
 */
export interface SessionUser {
  id: number
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
}

// 模块级私有状态，仅当前页面会话生命周期内有效（刷新后由 /auth/me 重新恢复）
let csrfToken: string | null = null
let currentUser: SessionUser | null = null

/** 供 http.ts 在非安全方法（POST/PUT/DELETE）上携带 X-CSRF-TOKEN 请求头 */
export function getCsrfToken(): string | null {
  return csrfToken
}

/** 后端每次响应下发的 CSRF Header 由 http.ts 回调更新到这里 */
export function setCsrfToken(token: string | null) {
  csrfToken = token
}

/** 当前登录用户（由登录流程或 /auth/me 恢复后写入） */
export function getCurrentUser(): SessionUser | null {
  return currentUser
}

export function setCurrentUser(user: SessionUser | null) {
  currentUser = user
}

/** 401 或主动退出时清空全部会话状态 */
export function clearSession() {
  csrfToken = null
  currentUser = null
}

/** 角色判断：只用于前端导航/按钮的交互提示；真正的权限边界由后端强制校验 */
export function hasRole(role: string): boolean {
  return currentUser?.roles.includes(role) ?? false
}

export function hasPermission(permission: string): boolean {
  return currentUser?.permissions.includes(permission) ?? false
}
