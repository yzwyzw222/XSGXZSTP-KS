const BASE = '/__integration/auth'

/** 回跳限定为门户、两套业务页面和平台管理，拒绝外站、编码分隔符与重复登录地址。 */
export function safeReturnPath(value) {
  if (typeof value !== 'string' || !/^\/(?:$|(?:relation|crawler|management)(?:\/|\?|$))/.test(value) ||
    /[\\\s\u0000-\u001f]|%2f|%5c|%0[0-9a-f]|%25/i.test(value)) return '/'
  const url = new URL(value, 'http://portal.local')
  if (url.origin !== 'http://portal.local' || !/^\/(?:$|(?:relation|crawler)(?:\/|$)|management\/(?:users(?:\/overview)?|logs|audits)$)/.test(url.pathname) ||
    /\/(?:login|register|session-expired)(?:\/|$)/.test(url.pathname) || /\/api(?:\/|$)/i.test(url.pathname)) return '/'
  return `${url.pathname}${url.search}${url.hash}`
}

/** 认证请求仅使用同源 HttpOnly 会话；错误和超时转换为可操作的提示。 */
async function request(action, options = {}) {
  let response
  try {
    response = await fetch(`${BASE}/${action}`, { credentials: 'same-origin',
      ...options, signal: AbortSignal.timeout(8000),
      headers: { Accept: 'application/json', 'Content-Type': 'application/json', ...options.headers },
    })
  } catch { throw new Error('无法连接登录服务，请检查网络后重试。') }
  if (action === 'me' && response.status === 401) return null
  if (response.status === 204) return null
  const data = await response.json().catch(() => null)
  if (!response.ok) throw new Error(data?.detail || '登录服务暂不可用，请稍后重试。')
  if (!data || (action !== 'csrf' && (typeof data.username !== 'string' || !Array.isArray(data.roles)))) {
    throw new Error('登录服务返回了无效的数据，请稍后重试。')
  }
  return data
}

/** 刷新后重新确认服务端身份，前端不持久化认证状态。 */
export const getCurrentUser = () => request('me')

/** 每次认证写操作都重新获取会话对应的 CSRF Token。 */
async function mutate(action, body) {
  const csrf = await request('csrf')
  if (typeof csrf.token !== 'string' || !csrf.token) throw new Error('登录校验未就绪，请重试。')
  return request(action, { method: 'POST', headers: { 'X-CSRF-TOKEN': csrf.token },
    ...(body ? { body: JSON.stringify(body) } : {}),
  })
}

export const login = credentials => mutate('login', credentials)
export const logout = () => mutate('logout')
