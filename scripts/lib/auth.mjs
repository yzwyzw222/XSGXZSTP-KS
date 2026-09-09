const identityCookie = 'CRAWLER_SESSION'
export const portalCookie = 'PORTAL_SESSION'
const endpoint = '/__integration/auth/'

/** 仅接受唯一且格式有效的门户会话，旧子系统 Cookie 不能作为统一身份。 */
export function readPortalSession(cookie = '') {
  const values = cookie.split(';').map(part => part.trim()).filter(part => part.startsWith(`${portalCookie}=`))
  if (values.length !== 1) return null
  const value = values[0].slice(portalCookie.length + 1)
  return /^[A-Za-z0-9+/=_-]{1,256}$/.test(value) ? value : null
}

export function identityCookies(cookie) {
  const session = readPortalSession(cookie)
  return session ? `${identityCookie}=${session}` : ''
}

/** 只公开统一会话 Cookie，名称和作用域在网关转换，不向浏览器暴露其他系统会话。 */
export function portalCookies(cookies = []) {
  return cookies.filter(cookie => cookie.startsWith(`${identityCookie}=`)).map(cookie => {
    const parts = cookie.replace(`${identityCookie}=`, `${portalCookie}=`).split(';')
      .filter((part, index) => index === 0 || !/^\s*(path|domain|httponly|samesite)(?:=|$)/i.test(part))
    return `${parts.join(';')}; Path=/; HttpOnly; SameSite=Lax`
  })
}

function reply(response, status, message, headers = {}) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff', ...headers })
  response.end(JSON.stringify({ status, detail: message }))
}

/** 统一入口复用 crawler 的会话和 CSRF 校验，不保存或记录用户密码。 */
export async function handlePortalAuth(request, response, config) {
  const action = new URL(request.url, 'http://127.0.0.1').pathname.slice(endpoint.length)
  const method = { csrf: 'GET', me: 'GET', login: 'POST', logout: 'POST' }[action]
  if (!method) return reply(response, 404, '认证接口不存在。')
  if (request.method !== method) return reply(response, 405, '请求方法不支持。', { Allow: method })
  if (method === 'POST' && request.headers.origin !== `http://${request.headers.host}`) {
    return reply(response, 403, '请求来源校验失败，请从统一入口重新操作。')
  }
  const identity = config.systems.find(system => system.id === 'crawler' && system.status === 'enabled')
  if (!identity) return reply(response, 503, '统一认证服务暂不可用，请稍后重试。')
  let body
  if (action === 'login') {
    if (!/^application\/json(?:;|$)/i.test(request.headers['content-type'] ?? '')) {
      return reply(response, 415, '登录请求必须使用 JSON 格式。')
    }
    let size = 0
    const chunks = []
    try {
      for await (const chunk of request) {
        size += chunk.length
        if (size > 4096) { reply(response, 413, '登录请求过大。'); return }
        chunks.push(chunk)
      }
      const data = JSON.parse(Buffer.concat(chunks).toString('utf8'))
      if (typeof data.username !== 'string' || !data.username.trim() || data.username.length > 64 ||
        typeof data.password !== 'string' || !data.password || data.password.length > 128) {
        return reply(response, 400, '请输入有效的账号和密码。')
      }
      body = JSON.stringify({ username: data.username.trim(), password: data.password })
    } catch { return reply(response, 400, '登录请求格式不正确。') }
  }
  try {
    const upstream = await fetch(`http://127.0.0.1:${identity.runtime.backendPort}${identity.runtime.contextPath ?? ''}/api/v1/auth/${action}`, {
      method, redirect: 'error', signal: AbortSignal.timeout(6000),
      headers: { Accept: 'application/json', 'Content-Type': 'application/json',
        Cookie: identityCookies(request.headers.cookie),
        ...(request.headers['x-csrf-token'] ? { 'X-CSRF-TOKEN': request.headers['x-csrf-token'] } : {}),
      }, body,
    })
    const cookies = portalCookies(upstream.headers.getSetCookie())
    const headers = cookies.length ? { 'Set-Cookie': cookies } : {}
    if (action === 'logout' && upstream.status === 401) {
      response.writeHead(204, { 'Cache-Control': 'no-store', 'Set-Cookie': `${portalCookie}=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0` })
      return response.end()
    }
    if (upstream.status === 401) return reply(response, 401, action === 'login' ? '账号或密码不正确，请重新输入。' : '登录已失效，请重新登录。', headers)
    if (upstream.status === 403) return reply(response, 403, '登录校验已过期，请重新操作。', headers)
    if (!upstream.ok) return reply(response, upstream.status >= 500 ? 503 : upstream.status, '认证请求未完成，请稍后重试。', headers)
    const content = upstream.status === 204 ? '' : await upstream.text()
    response.writeHead(upstream.status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store',
      'X-Content-Type-Options': 'nosniff', ...headers,
      ...(action === 'logout' ? { 'Set-Cookie': `${portalCookie}=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0` } : {}),
    })
    response.end(content)
  } catch {
    if (!response.headersSent) reply(response, 503, '无法连接统一认证服务，请稍后重试。')
  }
}
