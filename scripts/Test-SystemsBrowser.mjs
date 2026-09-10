import assert from 'node:assert/strict'
import { readFileSync, mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { chromium } from '../systems/crawler/frontend/node_modules/playwright/index.mjs'
import { expect } from '../systems/crawler/frontend/node_modules/@playwright/test/index.mjs'
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

/** 接口和节点计数正常仍可能没有绘制；连续检查像素，覆盖动画结束后的空白故障。 */
async function verifyCrawlerGraph(target) {
  const graph = target.getByRole('img', { name: /^知识图谱，共\d+个节点和\d+条关系$/ })
  await expect(graph).toHaveAttribute('aria-busy', 'false')
  const label = await graph.getAttribute('aria-label')
  if (/共0个节点/.test(label)) {
    await expect(target.getByText('暂无已同步的作者和作品', { exact: true })).toBeVisible()
    return
  }
  let paintedSamples = 0
  await expect.poll(async () => {
    const painted = await graph.locator('canvas').evaluate(canvas => {
      if (!canvas.width || !canvas.height) return false
      const pixels = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data
      let count = 0
      for (let index = 3; index < pixels.length; index += 4) if (pixels[index] > 200) count++
      return count > 30
    })
    paintedSamples = painted ? paintedSamples + 1 : 0
    return paintedSamples
  }, { message: '有节点数据时，图谱画布应在动画结束后持续可见', intervals: [200], timeout: 10000 }).toBeGreaterThanOrEqual(3)
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
      if (system.id === 'crawler' && destination === 'graph') await verifyCrawlerGraph(page)
    }
    await page.screenshot({ path: fileURLToPath(new URL(`${system.id}-desktop.png`, screenshots)), fullPage: true, animations: 'disabled' })
    if (development) assert.ok(hmrConnected, `${system.id} HMR 必须通过统一入口建立连接`)
    if (system.id === 'crawler') {
      await page.goto(`${base}/?workspace=${encodeURIComponent('/crawler/graph')}`)
      const workspace = page.frameLocator('iframe.platform-workspace')
      await verifyCrawlerGraph(workspace)
      await workspace.getByRole('button', { name: '刷新图谱', exact: true }).click()
      await workspace.getByRole('button', { name: '适应画布', exact: true }).click()
      await verifyCrawlerGraph(workspace)
      await page.screenshot({ path: fileURLToPath(new URL('crawler-graph-workspace.png', screenshots)), fullPage: true })
      await workspace.locator('.integration-return').click()
    } else await page.locator('.integration-return').click()
    await page.locator('.system-card').last().waitFor()
    assert.equal(await page.locator('.system-card').count(), config.systems.length)
    assert.deepEqual(errors, [], `${system.id} 页面脚本或后端响应异常`)
    await page.close()
    process.stdout.write(`${system.id}：统一登录后直接访问、两个业务页面、深链接刷新、会话保持与返回门户通过。\n`)
    if (system.id === 'crawler') process.stdout.write('crawler：独立入口与门户工作区的图谱实际绘制、刷新和适应画布检查通过。\n')
  }
} finally {
  try { await logoutPortal(context.request, base) }
  catch { process.stderr.write('统一验收会话清理失败，请检查门户状态。\n') }
  await context.close()
  await browser.close()
}
