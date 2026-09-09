import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { request } from '../systems/crawler/frontend/node_modules/playwright/index.mjs'
import { loginPortal, logoutPortal } from './lib/portal-test-session.mjs'

const config = JSON.parse(readFileSync(new URL('../deploy/systems.json', import.meta.url), 'utf8'))
const direct = process.argv.includes('--direct')
const selected = process.argv.find(value => value.startsWith('--system='))?.split('=')[1]
const systems = config.systems.filter(system => system.status === 'enabled' && (!selected || system.id === selected))
assert.ok(systems.length > 0, '未找到待验证系统')
const portal = `http://127.0.0.1:${config.portalPort}`
const base = system => `http://127.0.0.1:${direct ? system.runtime.backendPort : config.portalPort}/${system.id}`
const paths = {
  relation: ['/papers?page=0&size=5', '/authors?page=0&size=5', '/analytics/collaborations'],
  extraction: ['/papers?page=0&size=5', '/authors?name=integration&page=0&size=5&fetchRemote=false'],
  crawler: ['/catalog/achievements?page=0&size=5', '/analytics/overview', '/graph/overview'],
  scholar: ['/papers?page=0&size=5', '/authors?page=0&size=5', '/scholars?page=0&size=5', '/scholar-graph/initial', '/scholar-graph/stats'],
}
const client = await request.newContext({ timeout: 15000 })
let authenticated = false

// 直连仅用于验证后端认证边界，crawler 的内部会话 Cookie 名称由网关转换。
async function headersFor(system) {
  if (!direct || system.id !== 'crawler') return {}
  const state = await client.storageState()
  const cookie = state.cookies.find(value => value.name === 'PORTAL_SESSION')
  return cookie ? { Cookie: `CRAWLER_SESSION=${cookie.value}` } : {}
}

try {
  for (const system of systems) {
    const health = await client.get(`http://127.0.0.1:${direct ? system.runtime.backendPort : config.portalPort}${system.runtime.readinessPath}`)
    assert.equal(health.status(), 200, `${system.id} 就绪探针`)
    assert.equal((await health.json()).status, 'UP', `${system.id} 必要基础服务`)
    assert.ok([401, 403].includes((await client.get(`${base(system)}/api/v1/auth/me`)).status()), `${system.id} 拒绝匿名访问`)
  }
  await loginPortal(client, portal)
  authenticated = true
  const state = await client.storageState()
  assert.ok(state.cookies.some(cookie => cookie.name === 'PORTAL_SESSION' && cookie.path === '/' && cookie.httpOnly), '统一会话必须是根路径 HttpOnly Cookie')
  for (const system of systems) {
    const headers = await headersFor(system)
    const me = await client.get(`${base(system)}/api/v1/auth/me`, { headers })
    assert.equal(me.status(), 200, `${system.id} 无需单独登录`)
    assert.equal((await me.json()).username, 'admin', `${system.id} 身份保持一致`)
    for (const path of paths[system.id]) {
      const response = await client.get(`${base(system)}/api/v1${path}`, { headers })
      assert.equal(response.status(), 200, `${system.id} 业务查询 ${path}`)
      assert.match(response.headers()['content-type'], /json/, `${system.id} 查询必须返回 JSON`)
      await response.json()
    }
    const rejected = await client.post(`${base(system)}/api/v1/integration-nonexistent`, { headers })
    assert.equal(rejected.status(), 403, `${system.id} 拒绝缺少 CSRF 的写请求`)
    const unknown = await client.get(`${base(system)}/api/v1/integration-nonexistent`, { headers })
    assert.equal(unknown.status(), 404, `${system.id} 未知 API 不得回退为页面`)
    if (system.id === 'extraction') {
      assert.equal((await client.get(`${base(system)}/api/v1/authors`, { headers })).status(), 400, '作者查询缺少参数应返回 400')
    }
    if (!direct) {
      assert.equal((await client.post(`${base(system)}/api/v1/auth/login`)).status(), 410, '旧登录接口关闭')
    }
    process.stdout.write(`${system.id}：统一身份、业务查询、CSRF 拒绝和未知接口检查通过。\n`)
  }
  // 保存退出前的 Cookie 只用于重放验证，证明服务端撤销有效，而非仅清除浏览器 Cookie。
  const replay = await request.newContext({ storageState: await client.storageState() })
  try {
    await logoutPortal(client, portal)
    authenticated = false
    for (const system of systems) {
      assert.ok([401, 403].includes((await replay.get(`${portal}/${system.id}/api/v1/auth/me`)).status()), `${system.id} 退出后旧会话不能重放`)
    }
  } finally { await replay.dispose() }
  process.stdout.write(`统一登录接口验收完成：${systems.length} 个系统免重复登录，统一退出后全部失效。\n`)
} finally {
  if (authenticated) {
    try { await logoutPortal(client, portal) }
    catch { process.stderr.write('验收会话清理失败，请检查统一认证服务状态。\n') }
  }
  await client.dispose()
}
