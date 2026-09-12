import assert from 'node:assert/strict'
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import http from 'node:http'
import test from 'node:test'
import { createGateway } from '../lib/gateway.mjs'
import { safeReturnPath } from '../lib/return-path.mjs'

async function listen(t, server) {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  t.after(() => { server.closeAllConnections(); server.close() })
  return `http://127.0.0.1:${server.address().port}`
}

test('移除请求日志 API 和自动记录，保留历史文件及业务审计代理', async t => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'platform-gateway-'))
  t.after(() => {
    assert.equal(path.dirname(path.resolve(root)), path.resolve(os.tmpdir()))
    assert.match(path.basename(root), /^platform-gateway-/)
    rmSync(root, { recursive: true, force: true })
  })
  const file = path.join(root, '.local/integration/platform-audit.jsonl')
  mkdirSync(path.dirname(file), { recursive: true })
  const historical = '{"id":"historical-test-record"}\n'
  writeFileSync(file, historical)
  let identityRequests = 0
  const identity = http.createServer((_req, res) => {
    identityRequests++
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({ username: 'test-user', permissions: ['AUDIT_READ'] }))
  })
  await listen(t, identity)
  const config = { revision: 'test', systems: [{ id: 'crawler', status: 'enabled', runtime: { backendPort: identity.address().port, contextPath: '/crawler' } }] }
  const middleware = createGateway({ root, initialConfig: config, development: true, readConfig: () => config })
  const gateway = http.createServer((req, res) => middleware(req, res, () => {
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({ path: req.url }))
  }))
  const base = await listen(t, gateway)
  for (const method of ['GET', 'POST', 'HEAD']) {
    assert.equal((await fetch(base + '/__integration/platform/logs', { method })).status, 404)
  }
  for (const route of ['/crawler/api/v1/catalog/achievements', '/crawler/api/v1/operations/audits?category=OPERATION', '/crawler/api/v1/operations/audits?category=LOGIN']) {
    const response = await fetch(base + route)
    assert.equal(response.status, 200)
    assert.equal((await response.json()).path, route)
  }
  for (const legacy of ['logs', 'audits']) {
    const response = await fetch(base + '/management/' + legacy + '?category=LOGIN', { redirect: 'manual' })
    assert.equal(response.status, 302)
    assert.equal(response.headers.get('location'), '/crawler/logs?category=LOGIN')
  }
  assert.equal(identityRequests, 0)
  assert.equal(readFileSync(file, 'utf8'), historical)
  assert.equal(existsSync(file + '.tmp'), false)
})

test('兼容回跳只允许实际管理页面，拒绝伪造管理接口与路径逃逸', () => {
  for (const target of ['/management/users', '/management/users/overview', '/management/logs', '/management/audits']) assert.equal(safeReturnPath(target), target)
  for (const target of ['/management/api/users', '/management/unknown', '/management/users/../../evil', '//outside.test', '/management/users%2Foverview']) assert.equal(safeReturnPath(target), '/')
})
