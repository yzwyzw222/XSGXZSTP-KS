import assert from 'node:assert/strict'
import http from 'node:http'
import test from 'node:test'
import { loadConfig, rootDirectory } from '../lib/config.mjs'
import { createGateway, maintenanceHtml, proxyOptions } from '../lib/gateway.mjs'

async function listen(server) {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  return `http://127.0.0.1:${server.address().port}`
}

function request(base, route, method = 'GET') {
  return new Promise((resolve, reject) => {
    const req = http.request(`${base}${route}`, { method }, res => {
      const chunks = []
      res.on('data', chunk => chunks.push(chunk))
      res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body: Buffer.concat(chunks).toString() }))
    })
    req.on('error', reject)
    req.end()
  })
}

test('维护页面、深链接、API 各方法和规范路径不会访问下游', async t => {
  const config = loadConfig()
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
      assert.ok(result.body.includes(system.name) && result.body.includes('返回门户') && result.body.includes('href="/"'))
    }
    for (const suffix of ['/api', '/api/', '/api/v1/auth/login?target=%2F', '/API/v1/unknown']) {
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
  for (const route of ['/%', '/relation/%00', '/relation/%5Capi', '//relation/']) {
    assert.equal((await request(url, route)).status, 400)
  }
  assert.equal((await request(url, '/__integration/health')).status, 200)
  assert.equal(JSON.parse((await request(url, '/integration.json')).body).systems.length, 3)
  current = { ...config, revision: 'changed' }
  assert.equal((await request(url, '/integration.json')).status, 503)
  assert.equal(JSON.parse((await request(url, '/relation/api/v1/test')).body).code, 'CONFIG_CHANGED')
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

test('已启用系统代理保留查询参数、来源及认证 Cookie，并仅去掉所属系统前缀', () => {
  const config = loadConfig()
  const enabled = config.systems[0]
  enabled.status = 'enabled'
  enabled.runtime = { backendPort: 18081, frontendPort: 5174 }
  const proxies = proxyOptions(config, true)
  const api = proxies['^/relation/[aA][pP][iI](?:/|$)']
  assert.equal(api.target, 'http://127.0.0.1:18081')
  assert.equal(api.rewrite('/relation/api/v1/download?a=1'), '/api/v1/download?a=1')
  assert.equal(api.changeOrigin, false)
  assert.equal(proxies['^/relation/'].ws, true)
  assert.ok(!('cookiePathRewrite' in api))
  assert.equal(Object.keys(proxies).length, 2)
})
