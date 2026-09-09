<script setup>
import { onMounted, ref } from 'vue'
import App from './App.vue'
import PortalLogin from './components/PortalLogin.vue'
import { getCurrentUser, login, logout, safeReturnPath } from './services/auth'

const user = ref(null)
const checking = ref(true)
const pending = ref(false)
const error = ref(new URLSearchParams(window.location.search).has('unavailable') ? '暂时无法访问该子系统，请稍后从系统目录重试。' : '')
const returnPath = safeReturnPath(new URLSearchParams(window.location.search).get('redirect'))

function showLogin() {
  user.value = null
  window.history.replaceState(null, '', `/login${returnPath !== '/' ? `?redirect=${encodeURIComponent(returnPath)}` : ''}`)
  document.title = '统一登录 · 学术智能平台'
}

function enterPortal(current) {
  if (returnPath !== '/') { window.location.replace(returnPath); return }
  user.value = current
  window.history.replaceState(null, '', '/')
  document.title = '学术智能平台统一入口'
}

/** 初次进入先确认会话，避免登录页与已登录门户交替闪烁。 */
onMounted(async () => {
  try {
    const current = await getCurrentUser()
    if (current) enterPortal(current)
    else showLogin()
  } catch (failure) {
    error.value = failure.message
    showLogin()
  } finally { checking.value = false }
})

async function signIn(credentials) {
  if (pending.value) return
  pending.value = true
  error.value = ''
  try { enterPortal(await login(credentials)) }
  catch (failure) { error.value = failure.message }
  finally { pending.value = false }
}

async function signOut() {
  if (pending.value) return
  pending.value = true
  error.value = ''
  try { await logout(); showLogin() }
  catch (failure) { error.value = `退出未完成：${failure.message}` }
  finally { pending.value = false }
}
</script>

<template>
  <div v-if="checking" class="portal-shell session-loading" role="status">正在确认登录状态…</div>
  <App v-else-if="user" :user="user" :sign-out-pending="pending" :sign-out-error="error" @sign-out="signOut" />
  <PortalLogin v-else :pending="pending" :error="error" @submit="signIn" />
</template>

<style scoped>
.session-loading { display: grid; place-items: center; color: var(--muted); }
</style>
