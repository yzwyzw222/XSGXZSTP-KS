export const integrated = import.meta.env.BASE_URL === '/extraction/'

/** 集成模式始终回到门户登录，并保留当前业务地址；服务故障返回门户供重试。 */
export function redirectToPortal(target = window.location.pathname + window.location.search + window.location.hash, unavailable = false) {
  if (unavailable) { window.location.replace('/?unavailable=1'); return }
  const base = import.meta.env.BASE_URL
  const destination = target.startsWith(base) ? target : base + target.replace(/^\/+/, '')
  const safe = /\/(?:login|register|session-expired)(?:[/?#]|$)/.test(destination) ? base : destination
  window.location.replace(`/login?redirect=${encodeURIComponent(safe)}`)
}

/** 统一退出必须由服务端确认成功，失败时保留错误供用户重试。 */
export async function logoutFromPortal() {
  const csrfResponse = await fetch('/__integration/auth/csrf', {
    credentials: 'same-origin', signal: AbortSignal.timeout(8000),
  })
  if (!csrfResponse.ok) throw new Error('无法获取退出校验，请稍后重试。')
  const csrf = await csrfResponse.json()
  if (typeof csrf.token !== 'string' || !csrf.token) throw new Error('退出校验无效，请刷新后重试。')
  const response = await fetch('/__integration/auth/logout', {
    method: 'POST', credentials: 'same-origin', headers: { 'X-CSRF-TOKEN': csrf.token },
    signal: AbortSignal.timeout(8000),
  })
  if (!response.ok) throw new Error('统一退出未完成，请稍后重试。')
}
