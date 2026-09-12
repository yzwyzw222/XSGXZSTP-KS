import assert from 'node:assert/strict'
import http from 'node:http'
import test from 'node:test'
import { loadConfig, rootDirectory } from '../lib/config.mjs'
import { createGateway, maintenanceHtml, proxyOptions } from '../lib/gateway.mjs'

function maintenanceConfig() {
  const config = loadConfig()
  config.systems.forEach(system => { system.status = 'maintenance'; system.runtime = null })
  return config
}

async function listen(server) {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  return `http://127.0.0.1:${server.address().port}`
}

function request(base, route, method = 'GET', headers = {}) {
  return new Promise((resolve, reject) => {
    const req = http.request(`${base}${route}`, { method, headers }, res => {
      const chunks = []
      res.on('data', chunk => chunks.push(chunk))
      res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body: Buffer.concat(chunks).toString() }))
    })
    req.on('error', reject)
    req.end()
  })
}

test('维护页面、深链接、API 各方法和规范路径不会访问下游', async t => {
  const config = maintenanceConfig()
  let upstreamRequests = 0
  const upstream = http.createServer((_req, res) => { upstreamRequests++; res.end('unexpected backend') })
  const upstreamUrl = await listen(upstream)
  t.after(() => { upstream.closeAllConnections(); upstream.close() })
  let current = config
  const middleware = createGateway({ root: rootDirectory, initialConfig: config, readConfig: () => {
    if (current instanceof Error) throw current
    return current
  } })
  const gateway = http.createServer((req, res) => middleware(req, res, () => {
    http.get(upstreamUrl, response => response.pipe(res))
  }))
  const url = await listen(gateway)
  t.after(() => { gateway.closeAllConnections(); gateway.close() })
  for (const system of config.systems) {
    for (const route of [`/${system.id}/`, `/${system.id}/login`, `/${system.id}/deep/link`, `/${system.id}/assets/missing.js`]) {
      const result = await request(url, route)
      assert.equal(result.status, 503)
      assert.match(result.headers['content-type'], /text\/html/)
      assert.ok(result.body.includes(system.name) && result.body.includes('返回系统') && result.body.includes('href="/"'))
    }
    for (const suffix of ['/api', '/api/', '/api/v1/auth/login?target=%2F', '/API/v1/unknown', '/actuator/health']) {
      for (const method of ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS', 'HEAD']) {
        const result = await request(url, `/${system.id}${suffix}`, method)
        assert.equal(result.status, 503)
        assert.match(result.headers['content-type'], /application\/json/)
        if (method !== 'HEAD') assert.equal(JSON.parse(result.body).code, 'SYSTEM_MAINTENANCE')
        else assert.equal(result.body, '')
      }
    }
    const redirect = await request(url, `/${system.id}?from=portal`, 'POST')
    assert.equal(redirect.status, 308)
    assert.equal(redirect.headers.location, `/${system.id}/?from=portal`)
  }
  assert.equal(upstreamRequests, 0)
  assert.deepEqual(proxyOptions(config, true), {})
  assert.deepEqual(proxyOptions(config, false), {})
  assert.equal((await request(url, '/relation-other/')).status, 404)
  assert.equal((await request(url, '/api/v1/missing')).status, 404)
  for (const route of ['/%', '/crawler/%00', '/crawler/%5Capi', '//crawler/']) {
    assert.equal((await request(url, route)).status, 400)
  }
  assert.equal((await request(url, '/__integration/health')).status, 200)
  assert.equal(JSON.parse((await request(url, '/integration.json')).body).systems.length, 1)
  current = { ...config, revision: 'changed' }
  assert.equal((await request(url, '/integration.json')).status, 503)
  assert.equal(JSON.parse((await request(url, '/crawler/api/v1/test')).body).code, 'CONFIG_CHANGED')
  current = new Error('invalid config')
  assert.equal((await request(url, '/__integration/health')).status, 503)
  assert.equal((await request(url, '/crawler/')).status, 503)
  assert.equal(upstreamRequests, 0)
})

test('维护说明中的 HTML 被转义', () => {
  const html = maintenanceHtml({ name: '<script>alert(1)</script>', message: '" onclick="bad' })
  assert.ok(!html.includes('<script>'))
  assert.ok(html.includes('&lt;script&gt;') && html.includes('&quot;'))
})

test('根入口进入成果系统，旧管理书签兼容，移除系统及静态资源均不可访问', async t => {
  const config = loadConfig()
  const middleware = createGateway({ root: rootDirectory, initialConfig: config, development: true,
    readConfig: () => config })
  const gateway = http.createServer((req, res) => middleware(req, res, () => { res.end('next') }))
  const base = await listen(gateway)
  t.after(() => { gateway.closeAllConnections(); gateway.close() })
  assert.equal((await request(base, '/')).headers.location, '/crawler/')
  assert.equal((await request(base, '/login')).headers.location, '/crawler/login?redirect=%2F')
  assert.equal((await request(base, '/login?redirect=%2Fcrawler%2Fcatalog%2Fmaster-theses')).headers.location, '/crawler/login?redirect=%2Fcatalog%2Fmaster-theses')
  assert.equal((await request(base, '/?workspace=%2Fmanagement%2Fusers')).headers.location, '/management/users')
  assert.equal((await request(base, '/management/users')).headers.location, '/crawler/users')
  assert.equal((await request(base, '/management/logs')).headers.location, '/crawler/logs')
  assert.equal((await request(base, '/management/audits?category=LOGIN')).headers.location, '/crawler/logs?category=LOGIN')
  for (const path of ['/crawler/login', '/crawler/session-expired', '/crawler/catalog/master-theses', '/crawler/users']) {
    assert.equal((await request(base, path)).body, 'next')
  }
  for (const action of ['login', 'logout']) {
    assert.equal((await request(base, '/crawler/api/v1/auth/' + action, 'POST')).status, 403)
  }
  for (const removed of ['extraction', 'scholar', 'relation', 'portal']) {
    for (const suffix of ['', '/', '/admin', '/api/v1/papers', '/actuator/health', '/assets/app.js']) {
      for (const method of ['GET', 'POST', 'HEAD']) assert.equal((await request(base, '/' + removed + suffix, method)).status, 404)
    }
    assert.ok(Object.keys(proxyOptions(config, true)).every(route => !route.includes(removed)))
  }
})

test('已启用系统保留查询参数及认证 Cookie，并按后端上下文配置处理前缀', () => {
  const config = maintenanceConfig()
  const enabled = config.systems[0]
  enabled.status = 'enabled'
  enabled.runtime = { backendPort: 18081, frontendPort: 5174 }
  const proxies = proxyOptions(config, true)
  const api = proxies['^/crawler/(?:[aA][pP][iI]|actuator)(?:/|$)']
  assert.equal(api.target, 'http://127.0.0.1:18081')
  assert.equal(api.rewrite('/crawler/api/v1/download?a=1'), '/api/v1/download?a=1')
  assert.equal(api.changeOrigin, false)
  assert.equal(proxies['^/crawler/'].ws, true)
  assert.ok(!('cookiePathRewrite' in api))
  assert.equal(Object.keys(proxies).length, 2)
  enabled.runtime.contextPath = '/relation'
  const contextual = proxyOptions(config, false)['^/crawler/(?:[aA][pP][iI]|actuator)(?:/|$)']
  assert.equal(contextual.rewrite('/crawler/api/v1/download?a=1'), '/crawler/api/v1/download?a=1')
})
