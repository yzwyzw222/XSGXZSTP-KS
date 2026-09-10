import assert from 'node:assert/strict'
import { mkdtempSync, rmSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import http from 'node:http'
import test from 'node:test'
import { auditTarget, createPlatformAudit } from '../lib/platform-audit.mjs'
import { createGateway } from '../lib/gateway.mjs'
import { safeReturnPath } from '../../portal/src/services/auth.js'

function removeTestDirectory(directory) {
  const resolved = path.resolve(directory)
  assert.equal(path.dirname(resolved), path.resolve(os.tmpdir()))
  assert.match(path.basename(resolved), /^platform-(audit|gateway)-/)
  rmSync(resolved, { recursive: true, force: true })
}

test('平台日志覆盖两系统与认证操作，去除资源值并排除会话轮询', () => {
  for (const system of ['relation', 'crawler']) {
    assert.deepEqual(auditTarget(`/${system}/api/v1/papers/private-value`), { system, resource: '/api/v1/papers/:resource' })
    assert.equal(auditTarget(`/${system}/api/v1/auth/me`), null)
  }
  assert.equal(auditTarget('/__integration/auth/login').system, 'portal')
  assert.equal(auditTarget('/crawler/api/v1/users/42').system, 'portal')
  assert.equal(auditTarget('/__integration/platform/logs'), null)
  assert.equal(auditTarget('/crawler/assets/main.js'), null)
  for (const removed of ['extraction', 'scholar']) {
    assert.equal(auditTarget(`/${removed}/api/v1/papers`), null)
  }
})

test('平台日志持久化、筛选、分页与保留上限，不接受无效时间和分页', t => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'platform-audit-'))
  t.after(() => removeTestDirectory(root))
  const audit = createPlatformAudit(root, 10)
  for (let index = 0; index < 12; index++) audit.record({ system: index % 2 ? 'relation' : 'crawler', username: `tester-${index}`, method: 'GET', resource: '/api/v1/papers', status: index % 2 ? 200 : 503 })
  const reopened = createPlatformAudit(root, 10)
  const page = reopened.query(new URLSearchParams('system=crawler&result=FAILURE&size=2'))
  assert.equal(page.items.length, 2)
  assert.equal(page.items[0].username, 'tester-10')
  assert.ok(page.totalElements <= 5)
  assert.equal(reopened.query(new URLSearchParams('username=tester-11')).totalElements, 1)
  assert.equal(reopened.query(new URLSearchParams('from=2099-01-01T00:00:00Z')).totalElements, 0)
  for (const query of ['page=-1', 'size=0', 'system=unknown', 'system=extraction', 'system=scholar', 'from=broken', 'result=UNKNOWN', 'from=2026-02-01&to=2026-01-01']) {
    assert.throws(() => reopened.query(new URLSearchParams(query)), RangeError)
  }
  assert.ok(reopened.query(new URLSearchParams()).totalElements <= 10)
})

async function listen(t, server) {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  t.after(() => { server.closeAllConnections(); server.close() })
  return `http://127.0.0.1:${server.address().port}`
}

test('管理入口与平台日志执行服务端权限检查，身份故障时拒绝访问', async t => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'platform-gateway-'))
  t.after(() => removeTestDirectory(root))
  let identityStatus = 401
  let permissions = []
  const identity = http.createServer((_req, res) => {
    res.writeHead(identityStatus, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({ username: 'test-user', permissions }))
  })
  await listen(t, identity)
  const config = { revision: 'test', systems: [{ id: 'crawler', status: 'enabled', runtime: { backendPort: identity.address().port, contextPath: '/crawler' } }] }
  const middleware = createGateway({ root, initialConfig: config, readConfig: () => config })
  const gateway = http.createServer((req, res) => middleware(req, res, () => { res.writeHead(404); res.end() }))
  const base = await listen(t, gateway)
  const get = url => fetch(base + url, { redirect: 'manual' })
  assert.equal((await get('/__integration/platform/logs')).status, 401)
  assert.equal((await get('/management/users')).status, 302)
  identityStatus = 200
  assert.equal((await get('/management/users')).status, 403)
  assert.equal((await get('/management/logs')).status, 403)
  assert.equal((await get('/__integration/platform/logs')).status, 403)
  permissions = ['AUDIT_READ']
  assert.equal((await get('/__integration/platform/logs')).status, 200)
  assert.equal((await get('/__integration/platform/logs?size=999')).status, 400)
  assert.equal((await fetch(base + '/__integration/platform/logs', { method: 'POST' })).status, 405)
  const legacy = await get('/crawler/users')
  assert.equal(legacy.status, 302)
  assert.equal(legacy.headers.get('location'), '/?workspace=%2Fmanagement%2Fusers')
  identityStatus = 503
  assert.equal((await get('/__integration/platform/logs')).status, 503)
  const unavailable = await get('/management/logs')
  assert.equal(unavailable.status, 503)
  assert.match(await unavailable.text(), /返回门户/)
})

test('门户回跳只允许实际管理页面，拒绝伪造管理接口与路径逃逸', () => {
  for (const target of ['/management/users', '/management/users/overview', '/management/logs', '/management/audits']) assert.equal(safeReturnPath(target), target)
  for (const target of ['/management/api/users', '/management/unknown', '/management/users/../../evil', '//outside.test', '/management/users%2Foverview']) assert.equal(safeReturnPath(target), '/')
})
