import { ref } from 'vue'
import api, { initCsrf } from '../api/client.js'

const user = ref(null)
const isAuthenticated = ref(false)
const initialized = ref(false)

export function useAuth() {
  async function login(username, password) {
    await initCsrf()
    const data = await api.post('/auth/login', { username, password })
    user.value = data
    isAuthenticated.value = true
  }

  async function logout() {
    try {
      await api.post('/auth/logout')
    } finally {
      user.value = null
      isAuthenticated.value = false
    }
  }

  async function fetchMe() {
    try {
      const data = await api.get('/auth/me')
      user.value = data
      isAuthenticated.value = true
    } catch {
      user.value = null
      isAuthenticated.value = false
    } finally {
      initialized.value = true
    }
  }

  return { user, isAuthenticated, initialized, login, logout, fetchMe }
}
