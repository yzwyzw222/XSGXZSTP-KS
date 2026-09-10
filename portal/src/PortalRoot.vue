<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import App from './App.vue'
import PortalLogin from './components/PortalLogin.vue'
import { getCurrentUser, login, logout, safeReturnPath } from './services/auth'

const user = ref(null)
const checking = ref(true)
const pending = ref(false)
const workspace = ref('')
const frame = ref(null)
const fullscreen = ref(false)
const fullscreenError = ref('')
const error = ref(new URLSearchParams(window.location.search).has('unavailable') ? '暂时无法访问该子系统，请稍后从系统目录重试。' : '')
const returnPath = safeReturnPath(new URLSearchParams(window.location.search).get('redirect') || new URLSearchParams(window.location.search).get('workspace'))

/** 门户保留顶层文档，子系统切换不会终止浏览器全屏会话。 */
function openWorkspace(target) {
  workspace.value = safeReturnPath(target)
  if (workspace.value === '/') workspace.value = ''
  window.history.pushState(null, '', workspace.value ? `/?workspace=${encodeURIComponent(workspace.value)}` : '/')
}

function syncFullscreen() { fullscreen.value = Boolean(document.fullscreenElement) }
async function toggleFullscreen() {
  fullscreenError.value = ''
  try {
    if (document.fullscreenElement) await document.exitFullscreen()
    else if (document.fullscreenEnabled) await document.documentElement.requestFullscreen()
    else fullscreenError.value = '当前浏览器不支持全屏显示。'
  } catch { fullscreenError.value = '未能切换全屏，请重试。' }
}

/** 仅接管同源子页面的返回入口；业务页面中的普通链接仍由各自路由处理。 */
function frameLoaded() {
  const child = frame.value?.contentWindow
  if (!child || child.location.origin !== window.location.origin) return
  child.document.addEventListener('click', event => {
    if (event.target.closest?.('.integration-return')) { event.preventDefault(); openWorkspace('/') }
  })
}
function restoreWorkspace() {
  const target = safeReturnPath(new URLSearchParams(window.location.search).get('workspace'))
  workspace.value = target === '/' ? '' : target
}
function receiveMessage(event) {
  if (event.origin !== window.location.origin || event.source !== frame.value?.contentWindow) return
  if (event.data?.type === 'portal-navigate') openWorkspace(event.data.path)
  if (event.data?.type === 'portal-location') {
    const target = safeReturnPath(event.data.path)
    if (target !== '/') window.history.replaceState(null, '', `/?workspace=${encodeURIComponent(target)}`)
  }
  if (event.data?.type === 'portal-session-ended') { workspace.value = ''; showLogin() }
}

function showLogin() {
  user.value = null
  workspace.value = ''
  window.history.replaceState(null, '', `/login${returnPath !== '/' ? `?redirect=${encodeURIComponent(returnPath)}` : ''}`)
  document.title = '统一登录 · 学术智能平台'
}

function enterPortal(current) {
  user.value = current
  if (returnPath !== '/') openWorkspace(returnPath)
  else window.history.replaceState(null, '', '/')
  document.title = '学术智能平台统一入口'
}

/** 初次进入先确认会话，避免登录页与已登录门户交替闪烁。 */
onMounted(async () => {
  if (window.parent !== window) {
    window.parent.postMessage({ type: window.location.pathname === '/login' ? 'portal-session-ended' : 'portal-navigate', path: returnPath }, window.location.origin)
    return
  }
  document.addEventListener('fullscreenchange', syncFullscreen)
  window.addEventListener('popstate', restoreWorkspace)
  window.addEventListener('message', receiveMessage)
  syncFullscreen()
  try {
    const current = await getCurrentUser()
    if (current) enterPortal(current)
    else showLogin()
  } catch (failure) {
    error.value = failure.message
    showLogin()
  } finally { checking.value = false }
})
onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', syncFullscreen)
  window.removeEventListener('popstate', restoreWorkspace)
  window.removeEventListener('message', receiveMessage)
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
  <iframe v-else-if="user && workspace" ref="frame" :src="workspace" class="platform-workspace" title="学术智能平台工作区" @load="frameLoaded" />
  <App v-else-if="user" :user="user" :sign-out-pending="pending" :sign-out-error="error" :fullscreen="fullscreen" :fullscreen-error="fullscreenError" @sign-out="signOut" @open-workspace="openWorkspace" @toggle-fullscreen="toggleFullscreen" />
  <PortalLogin v-else :pending="pending" :error="error" @submit="signIn" />
</template>

<style scoped>
.session-loading { display: grid; place-items: center; color: var(--muted); }
.platform-workspace { display: block; width: 100%; height: 100dvh; border: 0; background: #06162c; }
</style>
