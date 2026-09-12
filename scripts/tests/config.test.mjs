import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { loadConfig, publicConfig, startupPlan, validateConfig, workspacePath, rootDirectory } from '../lib/config.mjs'
import { renderNginx } from '../lib/nginx.mjs'

const fixture = () => {
  const config = JSON.parse(readFileSync(new URL('../../deploy/systems.json', import.meta.url), 'utf8'))
  config.systems.forEach(system => { system.status = 'maintenance'; system.runtime = null })
  return config
}
export function enable(system) {
  system.status = 'enabled'
  system.runtime = {
    acceptance: `docs/${system.id}-acceptance.md`, acceptanceSha: system.sourceSha,
    dist: `systems/${system.id}/frontend/dist`, backendPort: 18081, frontendPort: 5174,
    readinessPath: '/actuator/health',
    backend: { executable: 'java', args: ['-jar', 'application.jar'], cwd: `systems/${system.id}/backend` },
    frontend: { executable: 'node', args: ['node_modules/vite/bin/vite.js'], cwd: `systems/${system.id}/frontend` },
  }
  return system
}

test('当前配置包含唯一成果系统；公开配置不泄漏运行命令', () => {
  const config = loadConfig()
  assert.equal(config.systems.length, 1)
  assert.deepEqual(config.systems.map(system => system.id), ['crawler'])
  assert.equal(config.systems[0].name, '学术成果信息采集及可视化系统')
  for (const removed of ['extraction', 'scholar', 'relation']) {
    assert.throws(() => startupPlan(config, removed), /未知启动对象/)
    const invalid = structuredClone(config)
    invalid.systems[0].id = removed
    assert.throws(() => validateConfig(invalid), /接入配置错误/)
  }
  assert.ok(config.systems.every(system => system.name.length > 8))
  assert.ok(publicConfig(config).systems.every(system => !('runtime' in system) && !('sourceSha' in system)))
  assert.equal(startupPlan(config).processes.length, config.systems.filter(system => system.status === 'enabled').length)
})

test('空、缺失、重复、未知和错误状态配置均拒绝启动', () => {
  for (const mutate of [() => null, config => ({ ...config, version: 2 }), config => ({ ...config, portalPort: 0 }),
    config => ({ ...config, systems: [] }), config => { config.systems[0].id = 'unknown'; return config },
    config => { config.systems.push(structuredClone(config.systems[0])); return config },
    config => { config.systems[0].status = 'enabeld'; return config },
    config => { config.systems[0].message = ''; return config },
    config => { config.systems[0].status = 'enabled'; return config }]) {
    assert.throws(() => validateConfig(mutate(fixture())), /接入配置错误/)
  }
})

test('启用要求验收 SHA、隔离端口及直接进程配置', () => {
  const config = fixture()
  enable(config.systems[0])
  assert.equal(validateConfig(config), config)
  assert.equal(startupPlan(config, 'all', 'Demo').processes.length, 1)
  assert.equal(startupPlan(config, 'crawler', 'Development').processes.length, 2)
  assert.equal(startupPlan(config, 'portal').processes.length, 0)
  for (const mutate of [runtime => { runtime.acceptanceSha = '0'.repeat(40) },
    runtime => { runtime.backendPort = 18000 }, runtime => { runtime.frontend.executable = 'cmd' },
    runtime => { runtime.backend.cwd = '../another-project' }, runtime => { runtime.dist = 'elsewhere' },
    runtime => { runtime.contextPath = '/other' }]) {
    const invalid = structuredClone(config)
    mutate(invalid.systems[0].runtime)
    assert.throws(() => validateConfig(invalid), /接入配置错误/)
  }
})

test('路径遍历和绝对路径被拒绝', () => {
  for (const relative of ['../outside', '/outside', 'C:\\outside', 'systems/../outside']) {
    assert.throws(() => workspacePath(rootDirectory, relative, 'systems'))
  }
  assert.throws(() => startupPlan(fixture(), 'unknown'))
  assert.ok(workspacePath(rootDirectory, 'systems/crawler', 'systems/crawler', true).endsWith('crawler'))
  const boundary = fixture()
  enable(boundary.systems[0]).runtime.backend.cwd = 'systems/crawler'
  assert.equal(validateConfig(boundary), boundary)
})

test('维护 Nginx 配置没有上游，API 匹配先于页面，保留查询参数', () => {
  const nginx = renderNginx(fixture(), rootDirectory)
  assert.ok(!nginx.includes('proxy_pass'))
  assert.equal((nginx.match(/SYSTEM_MAINTENANCE/g) ?? []).length, 1)
  for (const id of ['crawler']) {
    assert.ok(nginx.indexOf(`^/${id}/(?:api|actuator)`) < nginx.indexOf(`location /${id}/`))
    assert.ok(nginx.includes(`/${id}/$is_args$args`))
  }
  assert.ok(nginx.includes('listen 127.0.0.1:18000'))
})

test('启用 Nginx 配置保留后端上下文与 Host，Actuator 不回退为页面', () => {
  const config = fixture()
  enable(config.systems[0]).runtime.contextPath = '/crawler'
  const nginx = renderNginx(config, rootDirectory)
  assert.ok(nginx.includes('proxy_pass http://127.0.0.1:18081'))
  assert.ok(nginx.includes('proxy_set_header Host $http_host'))
  assert.ok(!nginx.includes('rewrite ^/crawler'))
  assert.ok(nginx.indexOf('^/crawler/(?:api|actuator)') < nginx.indexOf('location /crawler/'))
})
