import { reactive } from 'vue'
import { getCurrentUser, logout as apiLogout } from '../api/auth'
import { clearCsrfToken } from '../api/request'
import { integrated, logoutFromPortal } from '../services/portal-auth.js'

const state = reactive({
  user: null,
  roles: [],
  permissions: [],
  loading: false,
  authenticated: false,
  unavailable: false,
})

export function useSession() {
  async function fetchUser() {
    state.unavailable = false
    state.loading = true
    try {
      const user = await getCurrentUser()
      state.user = user
      state.roles = user.roles || []
      state.permissions = user.permissions || []
      state.authenticated = true
    } catch (error) {
      state.unavailable = error.status !== 401
      state.user = null
      state.roles = []
      state.permissions = []
      state.authenticated = false
    } finally {
      state.loading = false
    }
    return state.authenticated
  }

  async function logout() {
    if (integrated) await logoutFromPortal()
    else await apiLogout()
    state.user = null
    state.roles = []
    state.permissions = []
    state.authenticated = false
    clearCsrfToken()
  }

  function hasRole(role) {
    return state.roles.includes(role)
  }

  function hasPermission(perm) {
    return state.permissions.includes(perm)
  }

  return {
    state,
    fetchUser,
    logout,
    hasRole,
    hasPermission,
  }
}
