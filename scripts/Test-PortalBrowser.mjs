import assert from 'node:assert/strict'
import { mkdirSync } from 'node:fs'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { loadConfig, rootDirectory } from './lib/config.mjs'

// 可复用已有 Playwright 安装；浏览器工具不属于门户的运行依赖。
const { chromium } = await import(process.argv[2] ? pathToFileURL(path.resolve(process.argv[2])).href : 'playwright')
const output = path.join(rootDirectory, '.local/browser')
mkdirSync(output, { recursive: true })
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const config = loadConfig()
const base = `http://127.0.0.1:${config.portalPort}`
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1080 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(base)
  await page.locator('.system-card').last().waitFor()
  assert.equal(await page.locator('.system-card').count(), 3)
  assert.equal(await page.locator('.status.maintenance').count(), 3)
  assert.equal(await page.locator('.status.enabled').count(), 0)
  assert.equal(await page.locator('.card-bottom a').first().textContent().then(value => value.trim().startsWith('查看维护说明')), true)
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
  await page.screenshot({ path: path.join(output, 'portal-desktop.png'), fullPage: true, animations: 'disabled' })
  await page.getByRole('link', { name: '使用说明', exact: true }).click()
  assert.ok(page.url().endsWith('#about'))
  for (const system of config.systems) {
    const response = await page.goto(`${base}/${system.id}/deep/link`)
    assert.equal(response.status(), 503)
    assert.equal(await page.locator('h1').textContent(), system.name)
    await page.getByRole('link', { name: '返回门户' }).click()
    await page.locator('.system-card').last().waitFor()
    const api = await page.request.post(`${base}/${system.id}/api/v1/auth/login`)
    assert.equal(api.status(), 503)
    assert.equal((await api.json()).code, 'SYSTEM_MAINTENANCE')
  }
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(base)
  await page.locator('.system-card').last().waitFor()
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
  await page.screenshot({ path: path.join(output, 'portal-mobile.png'), fullPage: true, animations: 'disabled' })
  await page.route('**/integration.json', route => route.fulfill({ status: 503, body: '{}' }))
  await page.reload()
  await page.getByRole('alert').waitFor()
  assert.equal(await page.locator('.card-bottom a').count(), 0)
  await page.unroute('**/integration.json')
  await page.getByRole('button', { name: '重新读取' }).click()
  await page.locator('.system-card').last().waitFor()
  assert.deepEqual(errors, [])
  const withoutJavaScript = await browser.newContext({ javaScriptEnabled: false })
  const maintenance = await withoutJavaScript.newPage()
  await maintenance.goto(`${base}/relation/`)
  assert.ok(await maintenance.getByRole('link', { name: '返回门户' }).isVisible())
  await withoutJavaScript.close()
  process.stdout.write('浏览器验收通过：桌面、390px 窄屏、普通导航、三个维护深链接、API 503、配置失败与重试、无 JavaScript 维护页。\n')
} finally { await browser.close() }
