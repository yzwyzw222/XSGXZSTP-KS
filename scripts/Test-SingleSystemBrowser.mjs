import assert from 'node:assert/strict'
import http from 'node:http'
import path from 'node:path'
import { mkdirSync } from 'node:fs'
import { preview } from '../systems/crawler/frontend/node_modules/vite/dist/node/index.js'
import { chromium } from '../systems/crawler/frontend/node_modules/playwright/index.mjs'
import { expect } from '../systems/crawler/frontend/node_modules/@playwright/test/index.mjs'
import { createGateway, proxyOptions } from './lib/gateway.mjs'
import { loadConfig, rootDirectory } from './lib/config.mjs'

// 使用独立模拟后端和构建产物验证完整入口，不读取本机账号或连接业务数据库。
const output = path.join(rootDirectory, '.local/single-system-review-20260912/browser')
mkdirSync(output, { recursive: true })
let authenticated = false
let permissions = ['CATALOG_READ', 'USER_LIST', 'AUDIT_READ']
const emptyPage = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
const backend = http.createServer(async (request, response) => {
  const url = new URL(request.url, 'http://localhost')
  const route = url.pathname.replace(/^\/crawler\/api\/v1/, '')
  const send = (status, data) => { response.writeHead(status, { 'Content-Type': 'application/json' }); response.end(JSON.stringify(data)) }
  if (route === '/auth/csrf') {
    response.setHeader('Set-Cookie', 'CRAWLER_SESSION=browser-fixture; Path=/crawler; HttpOnly; SameSite=Lax')
    return send(200, { token: 'fixture-csrf', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' })
  }
  if (route === '/auth/login') {
    const chunks = []
    for await (const chunk of request) chunks.push(chunk)
    const body = JSON.parse(Buffer.concat(chunks).toString())
    if (request.headers['x-csrf-token'] !== 'fixture-csrf') return send(403, { detail: '校验失败' })
    if (body.username !== '界面验收用户' || body.password !== 'synthetic-test-input') return send(401, { detail: '账号或密码不正确' })
    authenticated = true
  }
  if (route === '/auth/logout') {
    authenticated = false
    response.writeHead(204)
    return response.end()
  }
  if (!authenticated || request.headers.cookie !== 'CRAWLER_SESSION=browser-fixture') return send(401, { detail: '请先登录' })
  if (route === '/auth/me' || route === '/auth/login') return send(200, { id: 1, username: '界面验收用户', roles: ['ADMIN'], permissions })
  if (route === '/catalog/master-theses' || route === '/catalog/doctoral-theses') {
    return send(200, { ...emptyPage, items: [{ id: 102, displayName: route.includes('master') ? '测试硕士论文' : '测试博士论文', entityType: 'MASTER_THESIS', achievementCount: 1, advisors: ['导师甲', '导师乙'] }], totalElements: 1, totalPages: 1 })
  }
  if (route === '/catalog/patents') return send(200, emptyPage)
  if (route === '/users/roles') return send(200, [])
  if (route.startsWith('/users') && !permissions.includes('USER_LIST')) return send(403, { detail: '无权访问' })
  return send(200, emptyPage)
})
await new Promise(resolve => backend.listen(0, '127.0.0.1', resolve))
const config = loadConfig()
config.systems[0].runtime.backendPort = backend.address().port
const root = path.join(rootDirectory, 'systems/crawler/frontend')
let server
let browser
try {
  server = await preview({ root, configFile: false, logLevel: 'error',
    preview: { host: '127.0.0.1', port: 0, strictPort: true, proxy: proxyOptions(config, false) },
    plugins: [{ name: 'single-system-test-gateway', configurePreviewServer(server) {
      server.middlewares.use(createGateway({ root: rootDirectory, initialConfig: config, readConfig: () => config,
        audit: { observe: (_request, _response, _target, identity) => { identity.catch(() => {}) }, query: () => ({ ...emptyPage, retentionLimit: 10000 }) } }))
    } }],
  })
  const base = `http://127.0.0.1:${server.httpServer.address().port}`
  browser = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(base)
  await expect(page.getByRole('heading', { name: '登录系统', exact: true })).toBeVisible()
  assert.equal(new URL(page.url()).pathname, '/crawler/login')
  await page.screenshot({ path: path.join(output, 'login-desktop.png'), fullPage: true })
  for (const width of [390, 320]) {
    await page.setViewportSize({ width, height: 844 })
    await expect(page.getByLabel('用户名', { exact: true })).toBeVisible()
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `${width}px 登录页不溢出`)
  }
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto(base + '/crawler/catalog/master-theses')
  await expect(page).toHaveURL(/\/crawler\/login\?redirect=/)
  await page.getByLabel('用户名', { exact: true }).fill('错误测试用户')
  await page.getByLabel('密码', { exact: true }).fill('synthetic-test-input')
  await page.getByRole('button', { name: '进入工作台', exact: true }).click()
  await expect(page.getByText('账号或者密码错误', { exact: true })).toBeVisible()
  await page.getByLabel('用户名', { exact: true }).fill('界面验收用户')
  await page.getByRole('button', { name: '进入工作台', exact: true }).click()
  await expect(page).toHaveURL(base + '/crawler/catalog/master-theses')
  for (const collection of ['master-theses', 'doctoral-theses']) {
    await page.goto(base + '/crawler/catalog/' + collection)
    await expect(page.getByRole('columnheader', { name: '导师', exact: true })).toBeVisible()
    await expect(page.getByRole('cell', { name: '导师甲、导师乙', exact: true })).toBeVisible()
    await expect(page.locator('iframe')).toHaveCount(0)
    await expect(page.getByText('统一门户', { exact: true })).toHaveCount(0)
    await page.reload()
    await expect(page.getByRole('cell', { name: '导师甲、导师乙', exact: true })).toBeVisible()
    await page.screenshot({ path: path.join(output, collection + '.png'), fullPage: true })
  }
  await page.getByRole('button', { name: '全屏', exact: true }).click()
  await expect.poll(() => page.evaluate(() => Boolean(document.fullscreenElement))).toBe(true)
  await page.getByRole('link', { name: '指导硕论编目', exact: true }).click()
  assert.ok(await page.evaluate(() => Boolean(document.fullscreenElement)))
  await page.getByRole('button', { name: '退出全屏', exact: true }).click()
  await page.goto(base + '/crawler/catalog/patents')
  await expect(page.getByRole('columnheader', { name: '导师', exact: true })).toHaveCount(0)
  await page.goto(base + '/management/users')
  await expect(page).toHaveURL(base + '/crawler/users')
  await expect(page.getByRole('heading', { name: '用户管理', exact: true })).toBeVisible()
  await page.goto(base + '/management/logs')
  await expect(page).toHaveURL(base + '/crawler/logs')
  await expect(page.getByRole('heading', { name: '日志管理', exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: '请求日志', exact: true })).toHaveCount(0)
  await expect(page.getByRole('tab')).toHaveCount(2)
  await page.goto(base + '/crawler/request-logs?category=LOGIN')
  await expect(page).toHaveURL(base + '/crawler/logs?category=LOGIN')
  await expect(page.getByRole('tab', { name: '登录日志' })).toHaveAttribute('aria-selected', 'true')
  permissions = ['CATALOG_READ']
  await page.goto(base + '/crawler/users')
  await expect(page).toHaveURL(base + '/crawler/forbidden')
  await page.goto(base + '/crawler/request-logs')
  await expect(page).toHaveURL(base + '/crawler/forbidden')
  assert.equal((await page.request.get(base + '/__integration/platform/logs')).status(), 404)
  for (const retired of ['/relation/', '/relation/admin', '/relation/api/v1/papers', '/portal/', '/assets/portal/']) {
    assert.equal((await page.request.get(base + retired)).status(), 404)
  }
  await page.goto(base + '/crawler/catalog/master-theses')
  await page.getByRole('button', { name: '账户菜单', exact: true }).click()
  await page.getByRole('menuitem', { name: '退出登录' }).click()
  await expect(page.getByRole('heading', { name: '登录系统', exact: true })).toBeVisible()
  assert.equal((await page.request.get(base + '/crawler/api/v1/auth/me')).status(), 401)
  assert.deepEqual(errors, [])
  console.log('单系统生产页面验证通过：根入口、登录失败/成功与深链接回跳、会话刷新、硕博导师、非论文列、账号/日志权限、全屏、退出、退役路径及窄屏登录。数据均为模拟数据。')
} finally {
  if (browser) await browser.close()
  if (server) await new Promise(resolve => server.httpServer.close(resolve))
  backend.closeAllConnections()
  await new Promise(resolve => backend.close(resolve))
}
