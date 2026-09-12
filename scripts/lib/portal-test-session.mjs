import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

/** 验收只在内存中读取本机已有账号，凭据不进入输出、截图或测试报告。 */
function adminPassword() {
  return JSON.parse(readFileSync(new URL('../../.local/integration-runtime/crawler/credentials.json', import.meta.url), 'utf8')).admin
}

export async function portalCsrf(client, base) {
  const response = await client.get(`${base}/__integration/auth/csrf`)
  assert.equal(response.status(), 200, '系统页面 CSRF 获取成功')
  const csrf = await response.json()
  assert.ok(typeof csrf.token === 'string' && csrf.token.length > 0, 'CSRF 不得为空')
  return { Origin: base, 'X-CSRF-TOKEN': csrf.token }
}

export async function loginPortal(client, base) {
  const headers = await portalCsrf(client, base)
  let response
  try {
    response = await client.post(`${base}/__integration/auth/login`, {
      headers, data: { username: 'admin', password: adminPassword() },
    })
  } catch { throw new Error('系统页面登录请求未完成；为保护凭据不输出请求详情。') }
  assert.equal(response.status(), 200, '系统页面登录成功')
}

export async function logoutPortal(client, base) {
  const response = await client.post(`${base}/__integration/auth/logout`, { headers: await portalCsrf(client, base) })
  assert.equal(response.status(), 204, '系统页面退出成功')
}

export async function loginPortalPage(page, base, navigate = true) {
  if (navigate) await page.goto(`${base}/login`)
  await page.getByLabel('用户名', { exact: true }).fill('admin')
  try { await page.getByLabel('密码', { exact: true }).fill(adminPassword()) }
  catch { throw new Error('密码输入未完成；为保护凭据不输出浏览器调用详情。') }
  await page.getByRole('button', { name: '进入工作台', exact: true }).click()
  await page.waitForURL(url => url.pathname !== '/login' && url.pathname !== '/crawler/login')
}
