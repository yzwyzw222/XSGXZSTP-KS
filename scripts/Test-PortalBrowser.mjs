import assert from 'node:assert/strict'
import { mkdirSync } from 'node:fs'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { loadConfig, rootDirectory } from './lib/config.mjs'
import { loginPortalPage, logoutPortal } from './lib/portal-test-session.mjs'

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
  await loginPortalPage(page, base)
  await page.locator('.system-card').last().waitFor()
  await page.locator('img').evaluateAll(images => Promise.all(images.map(image => image.decode())))
  assert.equal(await page.locator('.system-card').count(), config.systems.length)
  assert.equal(await page.locator('.status.maintenance').count(), config.systems.filter(system => system.status === 'maintenance').length)
  assert.equal(await page.locator('.status.enabled').count(), config.systems.filter(system => system.status === 'enabled').length)
  for (const system of config.systems) {
    assert.ok((await page.locator(`.system-card.${system.id} .card-bottom a`).textContent()).includes(system.status === 'enabled' ? '进入平台' : '查看维护说明'))
  }
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
  await page.screenshot({ path: path.join(output, 'portal-desktop.png'), fullPage: true, animations: 'disabled' })
  await page.getByRole('button', { name: '帮助中心', exact: true }).click()
  assert.ok(await page.getByRole('dialog', { name: '帮助中心' }).isVisible())
  await page.getByRole('button', { name: '关闭说明' }).click()
  await page.getByRole('link', { name: '数据资源', exact: true }).click()
  assert.ok(page.url().endsWith('#data-overview'))
  await page.getByRole('searchbox', { name: '搜索系统与研究方向' }).fill('信息采集')
  assert.equal(await page.locator('.system-card').count(), 1)
  assert.ok(await page.locator('.system-card.crawler').isVisible())
  await page.getByRole('searchbox', { name: '搜索系统与研究方向' }).fill('不存在的研究系统')
  assert.equal(await page.locator('.system-card').count(), 0)
  await page.getByRole('button', { name: '查看全部系统', exact: true }).click()
  assert.equal(await page.locator('.system-card').count(), config.systems.length)
  await page.getByLabel('趋势时间范围').selectOption('3')
  assert.ok((await page.locator('.chart-line').getAttribute('aria-label')).includes('2022'))
  assert.ok(!(await page.locator('.chart-line').getAttribute('aria-label')).includes('2020'))
  await page.getByLabel('趋势时间范围').selectOption('5')
  for (const system of config.systems) {
    if (system.status === 'enabled') {
      const response = await page.goto(`${base}/${system.id}/login`)
      assert.equal(response.status(), 200)
      await page.waitForURL(url => url.searchParams.get('workspace')?.startsWith(`/${system.id}/`))
      await page.frameLocator('iframe.platform-workspace').locator('.integration-return').click()
      await page.locator('.system-card').last().waitFor()
      continue
    }
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
  await page.locator('img').evaluateAll(images => Promise.all(images.map(image => image.decode())))
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
  assert.ok(await page.locator('.main-nav').evaluate(element => element.scrollWidth <= element.clientWidth + 1))
  assert.ok(await page.locator('img').evaluateAll(images => images.every(image => image.complete && image.naturalWidth > 0)))
  await page.screenshot({ path: path.join(output, 'portal-mobile.png'), fullPage: true, animations: 'disabled' })
  await page.route('**/integration.json', route => route.fulfill({ status: 503, body: '{}' }))
  await page.reload()
  await page.getByRole('alert').waitFor()
  assert.equal(await page.locator('.card-bottom a').count(), 0)
  await page.unroute('**/integration.json')
  await page.getByRole('button', { name: '重新读取' }).click()
  await page.locator('.system-card').last().waitFor()
  assert.deepEqual(errors, [])
  const maintained = config.systems.find(system => system.status === 'maintenance')
  if (maintained) {
    const withoutJavaScript = await browser.newContext({ javaScriptEnabled: false })
    const maintenance = await withoutJavaScript.newPage()
    await maintenance.goto(`${base}/${maintained.id}/`)
    assert.ok(await maintenance.getByRole('link', { name: '返回门户' }).isVisible())
    await withoutJavaScript.close()
  }
  await logoutPortal(page.request, base)
  process.stdout.write('门户浏览器验收通过：桌面、390px 窄屏、两系统入口与返回、搜索、帮助、配置失败与重试。\n')
} finally { await browser.close() }
