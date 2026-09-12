import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { loadConfig, publicConfig, workspacePath } from './config.mjs'
import { handlePortalAuth, identityCookies, portalCookies } from './auth.mjs'
import { safeReturnPath } from './return-path.mjs'

const escapeHtml = value => String(value).replace(/[&<>"']/g, character =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[character])

export function maintenanceHtml(system, message = system.message) {
  return `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>维护中 · ${escapeHtml(system.name)}</title><style>body{margin:0;background:#f7f7f0;color:#203f36;font-family:Microsoft YaHei,sans-serif;line-height:1.8}main{max-width:680px;margin:12vh auto;padding:32px}small{letter-spacing:.15em;color:#687568}h1{font-size:clamp(24px,5vw,36px);font-weight:500;margin:22px 0}p{color:#687568}a{display:inline-block;margin-top:28px;color:#153e36;text-underline-offset:7px}a:focus-visible{outline:3px solid #886636;outline-offset:6px}.status{display:inline-block;margin:28px 0 0;border:1px solid #d7c9ad;padding:2px 12px;color:#79613a;font-size:13px}hr{border:0;border-top:1px solid #dce1d4;margin:30px 0}</style><main><small>学术成果信息采集及可视化系统</small><br><span class="status">维护中</span><h1>${escapeHtml(system.name)}</h1><p>${escapeHtml(message)}</p><hr><p>该系统尚未开放业务访问。完成接入验证后，系统将更新开放状态。</p><a href="/">← 返回系统</a></main></html>`
}

function send(request, response, status, type, body) {
  response.writeHead(status, {
    'Content-Type': `${type}; charset=utf-8`,
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
    'Content-Length': Buffer.byteLength(body),
    ...(status === 503 ? { 'Retry-After': '300' } : {}),
  })
  response.end(request.method === 'HEAD' ? undefined : body)
}

const unavailable = (system, code) => JSON.stringify({ status: 503, code,
  system: system?.id ?? null, message: '系统暂不可用，请返回系统查看接入状态。' })

/** 此中间件先于代理和 SPA 回退，配置失效时默认拒绝所有业务入口。 */
export function createGateway({ root, initialConfig, development = false, readConfig = () => loadConfig(root) }) {
  return async (request, response, next) => {
    let url
    let pathname
    try {
      if (!request.url?.startsWith('/') || request.url.startsWith('//') || /\\|%2f|%5c|%00/i.test(request.url.split('?')[0])) throw new Error('invalid')
      url = new URL(request.url, 'http://127.0.0.1')
      pathname = decodeURIComponent(url.pathname)
      url.pathname = pathname
    } catch {
      return send(request, response, 400, 'application/json', JSON.stringify({ status: 400, code: 'INVALID_PATH' }))
    }
    let config
    let configurationFailure = false
    try {
      config = readConfig()
      configurationFailure = config.revision !== initialConfig.revision
    } catch {
      config = initialConfig
      configurationFailure = true
    }
    if (pathname === '/__integration/health') {
      return send(request, response, configurationFailure ? 503 : 200, 'application/json',
        JSON.stringify({ status: configurationFailure ? 'CONFIG_CHANGED' : 'UP', revision: config.revision }))
    }
    if (pathname === '/integration.json') {
      if (configurationFailure) return send(request, response, 503, 'application/json', unavailable(null, 'CONFIG_CHANGED'))
      return send(request, response, 200, 'application/json', JSON.stringify(publicConfig(config)))
    }
    const management = pathname.match(/^\/management\/(users(?:\/overview)?|logs|audits)$/)
    if (management) {
      const destination = management[1] === 'audits' ? 'logs' : management[1]
      response.writeHead(302, { Location: '/crawler/' + destination + url.search, 'Cache-Control': 'no-store' })
      return response.end()
    }
    if (pathname === '/' || pathname === '/index.html' || pathname === '/login') {
      if (configurationFailure) return send(request, response, 503, 'application/json', unavailable(null, 'CONFIG_CHANGED'))
      const target = safeReturnPath(url.searchParams.get(pathname === '/login' ? 'redirect' : 'workspace'))
      const destination = pathname === '/login' ? '/crawler/login?redirect=' + encodeURIComponent(target.startsWith('/crawler/') ? target.slice(8) : '/')
        : target === '/' ? '/crawler/' : target
      response.writeHead(302, { Location: destination, 'Cache-Control': 'no-store' })
      return response.end()
    }
    if (pathname.startsWith('/__integration/auth/')) {
      if (configurationFailure) return send(request, response, 503, 'application/json', unavailable(null, 'CONFIG_CHANGED'))
      return handlePortalAuth(request, response, config)
    }
    const system = config.systems.find(entry => pathname === `/${entry.id}` || pathname.startsWith(`/${entry.id}/`))
    if (system) {
      const prefix = `/${system.id}`
      const api = new RegExp(`^${prefix}/(?:api|actuator)(?:/|$)`, 'i').test(pathname)
      if (pathname === prefix) {
        response.writeHead(308, { Location: `${prefix}/${url.search}`, 'Cache-Control': 'no-store' })
        return response.end()
      }
      if (configurationFailure || system.status !== 'enabled') {
        if (api || !['GET', 'HEAD'].includes(request.method)) {
          return send(request, response, 503, 'application/json', unavailable(system, configurationFailure ? 'CONFIG_CHANGED' : 'SYSTEM_MAINTENANCE'))
        }
        return send(request, response, 503, 'text/html', maintenanceHtml(system,
          configurationFailure ? '接入配置已变更或无效，请维护者检查配置并重新启动系统。' : system.message))
      }
      const auth = pathname.match(/^\/crawler\/api\/v1\/auth\/(login|logout)\/?$/i)
      if (auth) {
        request.url = '/__integration/auth/' + auth[1].toLowerCase()
        return handlePortalAuth(request, response, config)
      }
      request.url = `${url.pathname}${url.search}`
      if (api || development) return next()
      try {
        const dist = workspacePath(root, system.runtime.dist, `systems/${system.id}`)
        const relative = pathname.slice(prefix.length + 1)
        const asset = relative ? workspacePath(dist, relative) : path.join(dist, 'index.html')
        const file = existsSync(asset) && path.extname(asset) ? asset :
          (path.extname(relative) ? null : path.join(dist, 'index.html'))
        if (!file || !existsSync(file)) return send(request, response, 404, 'application/json', '{"status":404}')
        const mime = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json',
          '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.webp': 'image/webp', '.ico': 'image/x-icon',
          '.woff': 'font/woff', '.woff2': 'font/woff2', '.pdf': 'application/pdf' }[path.extname(file)] ?? 'application/octet-stream'
        return send(request, response, 200, mime, readFileSync(file))
      } catch {
        return send(request, response, 503, 'application/json', unavailable(system, 'ASSET_UNAVAILABLE'))
      }
    }
    if (pathname === '/favicon.ico') { response.writeHead(204); return response.end() }
    if (/^\/api(?:\/|$)/i.test(pathname)) return send(request, response, 404, 'application/json', '{"status":404,"code":"UNKNOWN_API"}')
    return send(request, response, 404, 'text/html', '<!doctype html><html lang="zh-CN"><meta charset="utf-8"><h1>页面不存在</h1><a href="/">返回系统</a></html>')
  }
}

export function proxyOptions(config, development) {
  const proxies = {}
  for (const system of config.systems.filter(entry => entry.status === 'enabled')) {
    const prefix = `/${system.id}`
    const onError = proxy => {
      if (system.id === 'crawler') {
        proxy.on('proxyReq', (upstream, request) => {
          upstream.setHeader('Cookie', identityCookies(request.headers.cookie))
        })
        proxy.on('proxyRes', upstream => {
          if (upstream.headers['set-cookie']) upstream.headers['set-cookie'] = portalCookies(upstream.headers['set-cookie'])
        })
      }
      proxy.on('error', (_error, _request, response) => {
      if (response && typeof response.writeHead === 'function' && !response.headersSent) {
        response.writeHead(503, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' })
        response.end(unavailable(system, 'BACKEND_UNAVAILABLE'))
      }
      })
    }
    proxies[`^${prefix}/(?:[aA][pP][iI]|actuator)(?:/|$)`] = {
      target: `http://127.0.0.1:${system.runtime.backendPort}`, changeOrigin: false,
      rewrite: requestPath => system.runtime.contextPath ? requestPath : requestPath.slice(prefix.length), timeout: 15000, proxyTimeout: 15000, configure: onError,
    }
    if (development) proxies[`^${prefix}/`] = {
      target: `http://127.0.0.1:${system.runtime.frontendPort}`, changeOrigin: false,
      ws: true, timeout: 15000, proxyTimeout: 15000, configure: onError,
    }
  }
  return proxies
}
