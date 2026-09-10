import assert from 'node:assert/strict'
import { mkdirSync } from 'node:fs'
import path from 'node:path'
import { chromium } from '../systems/crawler/frontend/node_modules/playwright/index.mjs'
import { loadConfig, rootDirectory } from './lib/config.mjs'
import { loginPortal, loginPortalPage, logoutPortal } from './lib/portal-test-session.mjs'

const config = loadConfig()
const base = `http://127.0.0.1:${config.portalPort}`
const output = path.join(rootDirectory, '.local/unified-login/screenshots')
mkdirSync(output, { recursive: true })
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const page = await context.newPage()
const errors = []
page.on('pageerror', error => errors.push(error.message))

try {
  await page.goto(`${base}/login`)
  await page.getByRole('heading', { name: '登录学术智能平台' }).waitFor()
  assert.ok(await page.getByRole('button', { name: '登录并进入平台' }).isDisabled(), '空表单不能提交')
  for (const [name, width, height] of [['desktop', 1440, 1000], ['mobile', 390, 844], ['narrow', 320, 760]]) {
    await page.setViewportSize({ width, height })
    await page.waitForLoadState('networkidle')
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `${width}px 无横向溢出`)
    assert.ok(await page.getByLabel('账号', { exact: true }).isVisible())
    assert.ok(await page.getByLabel('密码', { exact: true }).isVisible())
    await page.screenshot({ path: path.join(output, `login-${name}.png`), fullPage: true, animations: 'disabled' })
  }
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.getByLabel('账号', { exact: true }).fill('invalid-test-user')
  await page.getByLabel('密码', { exact: true }).fill('invalid-test-input')
  await page.getByRole('button', { name: '显示密码', exact: true }).click()
  assert.equal(await page.getByLabel('密码', { exact: true }).getAttribute('type'), 'text')
  await page.getByRole('button', { name: '隐藏密码', exact: true }).click()
  await page.getByRole('button', { name: '登录并进入平台', exact: true }).click()
  await page.getByRole('alert').waitFor()
  assert.match(await page.getByRole('alert').textContent(), /账号或密码不正确/)
  assert.equal(new URL(page.url()).pathname, '/login')

  const destination = '/relation/relations/overview?source=login-test'
  await page.goto(`${base}${destination}`)
  await page.waitForURL(url => url.pathname === '/login')
  assert.equal(new URL(page.url()).searchParams.get('redirect'), destination)
  await loginPortalPage(page, base, false)
  await page.frameLocator('iframe.platform-workspace').locator('.integration-return').waitFor()
  assert.equal(new URL(page.url()).searchParams.get('workspace'), destination, '登录后在门户容器内准确返回原深链接')
  await logoutPortal(page.request, base)

  for (const [id, route] of [['relation', 'relations/overview'], ['crawler', '']]) {
    await loginPortal(context.request, base)
    await page.goto(`${base}/${id}/${route}`)
    await page.locator('.integration-return').waitFor()
    await page.waitForLoadState('networkidle')
    if (id === 'crawler') {
      await page.getByRole('button', { name: '账户菜单', exact: true }).click()
      await page.getByRole('menuitem', { name: '退出登录' }).click()
    } else {
      await page.getByRole('button', { name: '退出登录', exact: true }).click()
    }
    await page.waitForURL(url => url.pathname === '/login')
    await page.getByRole('heading', { name: '登录学术智能平台' }).waitFor()
    for (const system of config.systems) {
      assert.ok([401, 403].includes((await page.request.get(`${base}/${system.id}/api/v1/auth/me`)).status()), `${id} 退出后 ${system.id} 失效`)
    }
    process.stdout.write(`${id}：子系统退出返回统一登录，两系统会话同时失效。\n`)
  }
  await page.goto(`${base}/login?redirect=${encodeURIComponent('//example.com')}`)
  await loginPortalPage(page, base, false)
  await page.locator('.system-card').last().waitFor()
  assert.equal(new URL(page.url()).pathname, '/', '恶意回跳落到门户')
  await page.getByLabel('账号菜单').click()
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await page.getByRole('heading', { name: '登录学术智能平台' }).waitFor()
  assert.deepEqual(errors, [], '登录与退出没有浏览器脚本异常')
  process.stdout.write('统一登录浏览器验收通过：三种宽度、错误密码、密码可见性、深链接回跳、两系统统一退出与外站回跳拦截。\n')
} finally {
  try { await logoutPortal(context.request, base) }
  catch { process.stderr.write('浏览器验收会话清理未完成，请检查统一认证服务。\n') }
  await context.close()
  await browser.close()
}
