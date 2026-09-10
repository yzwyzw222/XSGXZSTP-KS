import assert from 'node:assert/strict'
import { readFileSync, mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { chromium } from '../systems/crawler/frontend/node_modules/playwright/index.mjs'
import { loginPortalPage, logoutPortal } from './lib/portal-test-session.mjs'

const config = JSON.parse(readFileSync(new URL('../deploy/systems.json', import.meta.url), 'utf8'))
const base = `http://127.0.0.1:${config.portalPort}`
const development = process.argv.includes('--development')
const screenshots = new URL('../.local/browser/', import.meta.url)
mkdirSync(screenshots, { recursive: true })
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const destinations = {
  relation: ['relations/overview', 'data'],
  crawler: ['', 'graph'],
}
try {
  const loginPage = await context.newPage()
  await loginPortalPage(loginPage, base)
  await loginPage.close()
  for (const system of config.systems) {
    const page = await context.newPage()
    const errors = []
    let hmrConnected = false
    page.on('websocket', socket => {
      const url = new URL(socket.url())
      if (url.port === String(config.portalPort) && url.pathname.startsWith(`/${system.id}/`)) {
        socket.on('framereceived', frame => {
          if (/"type"\s*:\s*"connected"/.test(String(frame.payload))) hmrConnected = true
        })
      }
    })
    page.on('pageerror', error => errors.push(error.message))
    page.on('response', response => {
      if (response.status() >= 500 && response.url().startsWith(`${base}/${system.id}/`)) {
        errors.push(`${response.status()} ${new URL(response.url()).pathname}`)
      }
    })
    await page.goto(`${base}/${system.id}/login`)
    await page.waitForURL(url => url.searchParams.get('workspace')?.startsWith(`/${system.id}/`))
    for (const destination of destinations[system.id]) {
      const response = await page.goto(`${base}/${system.id}/${destination}`)
      assert.equal(response.status(), 200, `${system.id} 深链接可直接打开`)
      await page.locator('.integration-return').waitFor()
      await page.waitForLoadState('networkidle')
      assert.equal(await page.getByRole('heading', { name: '页面不存在', exact: true }).count(), 0, `${system.id} 必须到达真实业务页`)
      assert.deepEqual(await page.locator('.el-message--error:visible').allTextContents(), [], `${system.id} 不应显示加载失败提示`)
      assert.ok(!page.url().includes('/login'), `${system.id} 刷新后会话保持`)
      assert.equal((await page.request.get(`${base}/${system.id}/api/v1/auth/me`)).status(), 200)
    }
    await page.screenshot({ path: fileURLToPath(new URL(`${system.id}-desktop.png`, screenshots)), fullPage: true, animations: 'disabled' })
    assert.deepEqual(errors, [], `${system.id} 页面脚本或后端响应异常`)
    if (development) assert.ok(hmrConnected, `${system.id} HMR 必须通过统一入口建立连接`)
    await page.locator('.integration-return').click()
    await page.locator('.system-card').last().waitFor()
    assert.equal(await page.locator('.system-card').count(), config.systems.length)
    await page.close()
    process.stdout.write(`${system.id}：统一登录后直接访问、两个业务页面、深链接刷新、会话保持与返回门户通过。\n`)
  }
} finally {
  try { await logoutPortal(context.request, base) }
  catch { process.stderr.write('统一验收会话清理失败，请检查门户状态。\n') }
  await context.close()
  await browser.close()
}
