import { ref } from 'vue'
import api, { initCsrf } from '../api/client.js'
import { integrated, logoutFromPortal } from '../services/portal-auth.js'

const user = ref(null)
const isAuthenticated = ref(false)
const initialized = ref(false)
const unavailable = ref(false)

export function useAuth() {
  async function login(username, password) {
    await initCsrf()
    const data = await api.post('/auth/login', { username, password })
    user.value = data
    isAuthenticated.value = true
  }

  async function logout() {
    if (integrated) await logoutFromPortal()
    else await api.post('/auth/logout')
    user.value = null
    isAuthenticated.value = false
  }

  async function fetchMe() {
    unavailable.value = false
    try {
      const data = await api.get('/auth/me')
      user.value = data
      isAuthenticated.value = true
    } catch (error) {
      unavailable.value = error.status !== 401
      user.value = null
      isAuthenticated.value = false
    } finally {
      initialized.value = true
    }
  }

  return { user, isAuthenticated, initialized, unavailable, login, logout, fetchMe }
}
