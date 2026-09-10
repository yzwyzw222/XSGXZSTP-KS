import assert from 'node:assert/strict'
import http from 'node:http'
import test from 'node:test'
import { handlePortalAuth, identityCookies, portalCookies, readPortalSession } from '../lib/auth.mjs'
import { safeReturnPath } from '../../portal/src/services/auth.js'

test('统一 Cookie 隔离旧会话，拒绝重复值和异常字符', () => {
  assert.equal(readPortalSession('PORTAL_SESSION=valid-session'), 'valid-session')
  for (const cookie of ['CRAWLER_SESSION=legacy', 'PORTAL_SESSION=a; PORTAL_SESSION=b', 'PORTAL_SESSION=bad token', '']) {
    assert.equal(readPortalSession(cookie), null)
  }
  assert.equal(identityCookies('PORTAL_SESSION=valid-session; CRAWLER_SESSION=legacy'), 'CRAWLER_SESSION=valid-session')
  assert.deepEqual(portalCookies(['CRAWLER_SESSION=test; Path=/crawler; HttpOnly; SameSite=Lax', 'OTHER=ignored']),
    ['PORTAL_SESSION=test; Path=/; HttpOnly; SameSite=Lax'])
})

test('回跳允许业务深链接，拒绝外站、路径逃逸、认证接口与登录循环', () => {
  assert.equal(safeReturnPath('/relation/relations/overview?year=2025#papers'), '/relation/relations/overview?year=2025#papers')
  assert.equal(safeReturnPath('/crawler/graph?query=%E5%AD%A6%E6%9C%AF'), '/crawler/graph?query=%E5%AD%A6%E6%9C%AF')
  for (const removed of ['/extraction/', '/extraction/papers?id=1', '/scholar/', '/scholar/dashboard']) {
    assert.equal(safeReturnPath(removed), '/', '已删除系统不能作为登录回跳或工作区地址')
  }
  for (const path of [null, '//evil.test', 'https://evil.test', '/crawler/../../evil', '/crawler/%2f/evil',
    '/crawler/\\evil', '/crawler/login', '/relation/register', '/scholar/api/v1/auth/logout', '/login', '/other/']) {
    assert.equal(safeReturnPath(path), '/', String(path))
  }
})

async function listen(t, server) {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  t.after(() => { server.closeAllConnections(); server.close() })
  return `http://127.0.0.1:${server.address().port}`
}

test('认证网关保留 CSRF、轮换会话，关闭非法请求并明确报告服务故障', async t => {
  let status = 200
  let calls = 0
  let receivedCookie
  const upstream = http.createServer((req, res) => {
    calls++
    receivedCookie = req.headers.cookie
    const csrfValid = req.headers['x-csrf-token'] === 'test-csrf'
    const result = req.method === 'POST' && !csrfValid ? 403 : status
    res.writeHead(result, { 'Content-Type': 'application/json', 'Set-Cookie': 'CRAWLER_SESSION=rotated; Path=/crawler; HttpOnly' })
    res.end(JSON.stringify({ username: 'test-user', roles: ['RESEARCHER'], token: 'test-csrf' }))
  })
  await listen(t, upstream)
  const config = { systems: [{ id: 'crawler', status: 'enabled', runtime: { backendPort: upstream.address().port, contextPath: '/crawler' } }] }
  const gateway = http.createServer((req, res) => handlePortalAuth(req, res, config))
  const base = await listen(t, gateway)
  const send = (path, options = {}) => fetch(`${base}/__integration/auth/${path}`, options)
  const post = { method: 'POST', headers: { Origin: base, 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'test-csrf',
    Cookie: 'PORTAL_SESSION=portal-session; CRAWLER_SESSION=legacy' }, body: JSON.stringify({ username: 'test-user', password: 'test-input' }) }
  assert.equal((await send('login', post)).status, 200)
  assert.equal(receivedCookie, 'CRAWLER_SESSION=portal-session')
  assert.match((await send('csrf')).headers.get('set-cookie'), /^PORTAL_SESSION=rotated;.*Path=\/; HttpOnly; SameSite=Lax/)
  assert.equal((await send('login', { ...post, headers: { ...post.headers, 'X-CSRF-TOKEN': '' } })).status, 403)
  const beforeInvalid = calls
  assert.equal((await send('logout')).status, 405)
  assert.equal((await send('unknown')).status, 404)
  assert.equal((await send('login', { ...post, headers: { ...post.headers, Origin: 'https://evil.test' } })).status, 403)
  assert.equal((await send('login', { ...post, body: '{broken' })).status, 400)
  assert.equal((await send('login', { ...post, body: '{"username":"","password":""}' })).status, 400)
  assert.equal((await send('login', { ...post, body: 'x'.repeat(4200) })).status, 413)
  assert.equal(calls, beforeInvalid)
  status = 401
  const invalid = await send('login', post)
  assert.equal(invalid.status, 401)
  assert.match((await invalid.json()).detail, /账号或密码不正确/)
  const logout = await send('logout', post)
  assert.equal(logout.status, 204)
  assert.match(logout.headers.get('set-cookie'), /Max-Age=0/)
  status = 503
  assert.equal((await send('me')).status, 503)
  upstream.closeAllConnections()
  await new Promise(resolve => upstream.close(resolve))
  assert.equal((await send('me')).status, 503)
})
