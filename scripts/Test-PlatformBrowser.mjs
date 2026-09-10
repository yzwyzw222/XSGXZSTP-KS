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
      await workspace.getByRole('link', { name: '采集任务 Collection', exact: false }).first().click()
      await workspace.getByRole('heading', { name: '采集任务', exact: true }).waitFor()
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
  await workspace.getByRole('link', { name: '采集任务 Collection', exact: false }).first().click()
  await page.waitForURL(url => url.searchParams.get('workspace') === '/crawler/crawl')
  await page.reload()
  workspace = page.frameLocator('iframe.platform-workspace')
  await workspace.getByRole('heading', { name: '采集任务', exact: true }).waitFor()
  // 仅在浏览器拦截独立测试编号，不创建真实任务或发起外部采集。
  const failureMessage = '采集执行队列已满或服务正在关闭，请稍后对该任务重新执行。'
  await page.route('**/crawler/api/v1/crawl/runs/999999', route => route.fulfill({ json: {
    id: 999999, taskId: 999999, runNumber: 'platform-test-launch-failure', triggerType: 'MANUAL',
    status: 'FAILED', batchJobExecutionId: null, completionReason: 'BATCH_FAILED',
    readCount: 0, parsedCount: 0, createdCount: 0, updatedCount: 0, duplicateCount: 0, failureCount: 0,
    requestCount: 0, checkpoint: null, startedAt: null, finishedAt: '2026-09-10T00:00:00Z',
  } }))
  await page.route('**/crawler/api/v1/crawl/runs/999999/failures?*', route => route.fulfill({ json: {
    items: [{ id: 999999, runId: 999999, rawRecordId: null, externalRecordId: null, failureStage: 'SYSTEM',
      errorCategory: 'LAUNCH_EXECUTOR_BUSY', safeMessage: failureMessage, retryable: false, attemptCount: 1 }],
    page: 0, size: 20, totalElements: 1, totalPages: 1,
  } }))
  await workspace.getByPlaceholder('运行编号', { exact: true }).fill('999999')
  await workspace.getByRole('button', { name: '查询运行', exact: true }).click()
  await workspace.getByText('采集批次未能启动，尚未发出来源请求。', { exact: false }).waitFor()
  await workspace.getByText(failureMessage, { exact: true }).waitFor()
  assert.equal(await workspace.getByRole('button', { name: '重试失败项', exact: true }).count(), 0)
  await page.screenshot({ path: fileURLToPath(new URL('launch-failure-fixture.png', output)), fullPage: true, animations: 'disabled' })
  assert.deepEqual(errors, [], '所有受影响页面无浏览器脚本异常')
  console.log('门户响应式、两系统左上返回、持续全屏、统一用户与日志管理、后台审计均通过。')
} finally {
  await logoutPortal(context.request, base).catch(() => { process.stderr.write('验收会话清理未完成，请检查统一认证服务。\n') })
  await browser.close()
}
