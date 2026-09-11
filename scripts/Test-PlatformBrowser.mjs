import assert from 'node:assert/strict'
import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { chromium } from '../systems/crawler/frontend/node_modules/playwright/index.mjs'
import { loginPortalPage, logoutPortal } from './lib/portal-test-session.mjs'

const base = 'http://127.0.0.1:18000'
const output = new URL('../.local/platform-browser/', import.meta.url)
mkdirSync(output, { recursive: true })
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const page = await context.newPage()
const errors = []
page.on('pageerror', error => errors.push(error.message))
try {
  await loginPortalPage(page, base)
  await page.locator('.system-card').first().waitFor()
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 1000 })
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `门户 ${width}px 无横向溢出`)
    await page.screenshot({ path: fileURLToPath(new URL(`portal-${width}.png`, output)), fullPage: true, animations: 'disabled' })
  }
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.getByRole('button', { name: '全屏', exact: true }).click()
  await page.waitForFunction(() => Boolean(document.fullscreenElement))
  for (const system of ['crawler', 'relation']) {
    await page.locator(`.system-card.${system} .entry-button`).click()
    const workspace = page.frameLocator('iframe.platform-workspace')
    const back = workspace.locator('.integration-return')
    await back.waitFor()
    const bounds = await back.boundingBox()
    assert.ok(bounds.y < 110 && bounds.x < 420, `${system} 返回入口位于顶部左侧`)
    assert.equal(await page.evaluate(() => Boolean(document.fullscreenElement)), true, `${system} 进入后保持全屏`)
    assert.equal(await workspace.getByRole('button', { name: '进入全屏', exact: true }).count(), 0)
    if (system === 'crawler') {
      assert.equal(await workspace.locator('a[href="/crawler/users"], a[href="/crawler/logs"]').count(), 0)
      await workspace.getByRole('link', { name: '作者导入', exact: true }).first().click()
      await workspace.getByRole('heading', { name: '作者导入', exact: true }).waitFor()
      assert.equal(await page.evaluate(() => Boolean(document.fullscreenElement)), true)
    }
    const child = page.frames().find(frame => frame.url().startsWith(`${base}/${system}/`))
    await child.waitForLoadState('networkidle')
    assert.deepEqual(await workspace.locator('.el-message--error:visible').allTextContents(), [], `${system} 页面数据加载成功`)
    await page.screenshot({ path: fileURLToPath(new URL(`${system}.png`, output)), animations: 'disabled' })
    await back.click()
    await page.locator('.system-card').first().waitFor()
    assert.equal(await page.locator('iframe').count(), 0, `${system} 返回时释放子页面`)
    assert.equal(await page.evaluate(() => Boolean(document.fullscreenElement)), true)
  }
  await page.getByRole('link', { name: '用户管理', exact: true }).click()
  let workspace = page.frameLocator('iframe.platform-workspace')
  await workspace.getByRole('heading', { name: '用户管理', exact: true }).waitFor()
  await workspace.getByRole('button', { name: '新增用户', exact: true }).waitFor()
  await page.frames().find(frame => frame.url().startsWith(`${base}/management/`)).waitForLoadState('networkidle')
  assert.equal((await page.request.get(base + '/crawler/api/v1/users')).status(), 200)
  await page.screenshot({ path: fileURLToPath(new URL('users.png', output)), fullPage: true })
  await workspace.getByRole('link', { name: '账号概况', exact: true }).click()
  await workspace.getByRole('link', { name: '查看全部', exact: true }).click()
  await page.waitForURL(url => url.searchParams.get('workspace') === '/management/audits?category=LOGIN')
  await workspace.getByRole('heading', { name: '日志管理', exact: true }).waitFor()
  await workspace.locator('.integration-return').click()
  await page.getByRole('link', { name: '日志管理', exact: true }).click()
  workspace = page.frameLocator('iframe.platform-workspace')
  await workspace.getByRole('heading', { name: '平台日志管理' }).waitFor()
  await workspace.locator('tbody tr').first().waitFor()
  const response = await page.request.get(base + '/__integration/platform/logs?size=100')
  assert.equal(response.status(), 200)
  const logs = await response.json()
  for (const system of ['crawler', 'relation']) assert.ok(logs.items.some(entry => entry.system === system), `${system} 请求进入平台日志`)
  await page.screenshot({ path: fileURLToPath(new URL('logs.png', output)), fullPage: true })
  await workspace.getByRole('link', { name: '登录与采集后台审计' }).click()
  await workspace.getByRole('heading', { name: '日志管理', exact: true }).waitFor()
  await workspace.locator('.integration-return').click()
  await page.getByRole('button', { name: '退出全屏', exact: true }).click()
  await page.waitForFunction(() => !document.fullscreenElement)
  await page.locator('.system-card.crawler .entry-button').click()
  workspace = page.frameLocator('iframe.platform-workspace')
  await workspace.getByRole('link', { name: '作者导入', exact: true }).first().click()
  await page.waitForURL(url => url.searchParams.get('workspace') === '/crawler/author-import')
  await page.reload()
  workspace = page.frameLocator('iframe.platform-workspace')
  await workspace.getByRole('heading', { name: '作者导入', exact: true }).waitFor()
  // 使用合成文件并只拦截预览请求，不写入真实学者资料。
  const failureMessage = '信息表仍有问题，请核对后重新导出。'
  await page.route('**/crawler/api/v1/author-import/preview', route => route.fulfill({ status: 400,
    contentType: 'application/problem+json', json: { detail: failureMessage, errorCode: 'INVALID_ARGUMENT' } }))
  await workspace.getByRole('textbox', { name: '学者名称检索' }).fill('界面验收学者')
  await workspace.getByLabel('选择信息表').setInputFiles({ name: '界面验收.csv', mimeType: 'text/csv', buffer: Buffer.from('Title,Author\n验证论文,界面验收学者') })
  await workspace.getByRole('button', { name: '解析并预览', exact: true }).click()
  await workspace.getByText(failureMessage, { exact: true }).waitFor()
  assert.equal(await workspace.getByRole('button', { name: /确认导入/ }).count(), 0)
  await page.screenshot({ path: fileURLToPath(new URL('author-import-error-fixture.png', output)), fullPage: true, animations: 'disabled' })
  assert.deepEqual(errors, [], '所有受影响页面无浏览器脚本异常')
  console.log('门户响应式、两系统左上返回、持续全屏、统一用户与日志管理、后台审计均通过。')
} finally {
  await logoutPortal(context.request, base).catch(() => { process.stderr.write('验收会话清理未完成，请检查统一认证服务。\n') })
  await browser.close()
}
