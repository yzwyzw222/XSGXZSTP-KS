import { expect, test, type Page } from '@playwright/test'

const admin = { id: 1, username: 'admin-demo', roles: ['ADMIN'], permissions: ['USER_LIST', 'USER_CREATE', 'USER_UPDATE', 'USER_ROLE_CHANGE', 'USER_ENABLE', 'USER_DISABLE', 'USER_PASSWORD_RESET', 'AUDIT_READ'] }
const account = { id: 2, username: 'research-demo', roles: ['RESEARCHER'], status: 'ACTIVE', version: 0, realName: '林研究员', organization: '开放科学研究院', email: null, phone: null, department: '数据研究部', remark: null, createdAt: '2026-09-05T00:00:00Z', updatedAt: '2026-09-05T00:00:00Z', credentialsChangedAt: '2026-09-05T00:00:00Z' }

async function mockManagement(page: Page, failStatistics = false) {
  const users = [{ ...account, id: 1, username: admin.username, realName: '系统管理员', roles: ['ADMIN'] }, { ...account }]
  const requests: Array<{ method: string; path: string; body: any }> = []
  let conflict = false
  let creationGate: Promise<void> | undefined
  let releaseCreation: (() => void) | undefined
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    const method = request.method()
    const body = method === 'GET' ? null : request.postDataJSON()
    requests.push({ method, path, body })
    let result: unknown
    let status = 200
    if (path === '/api/v1/auth/me') result = admin
    else if (path === '/api/v1/auth/csrf') result = { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'account-e2e-csrf' }
    else if (path === '/api/v1/users/statistics') {
      if (failStatistics) { status = 503; result = { detail: '统计暂不可用' } }
      else result = { totalUsers: users.length, admin: 1, dataOperator: users.filter((user) => user.roles.includes('DATA_OPERATOR')).length,
        researcher: users.filter((user) => !user.roles.includes('ADMIN') && !user.roles.includes('DATA_OPERATOR')).length }
    } else if (path === '/api/v1/users' && method === 'GET') result = { items: users, page: 0, size: 20, totalElements: users.length, totalPages: 1 }
    else if (path === '/api/v1/users' && method === 'POST') {
      await creationGate
      const user = { ...account, ...body, id: users.length + 1 }
      users.push(user); result = user; status = 201
    } else if (path === '/api/v1/users/2' && method === 'PUT') {
      if (conflict) { status = 409; result = { errorCode: 'VERSION_CONFLICT', detail: '用户数据已更新' } }
      else { Object.assign(users[1]!, body, { version: users[1]!.version + 1 }); result = users[1] }
    } else if (path === '/api/v1/operations/audits') {
      const pageNumber = Number(url.searchParams.get('page') ?? 0)
      const size = Number(url.searchParams.get('size') ?? 20)
      const filtered = Boolean(url.searchParams.get('username') || url.searchParams.get('result'))
      const total = filtered ? 1 : 23
      const isLogin = url.searchParams.get('category') === 'LOGIN'
      const logs = Array.from({ length: Math.min(size, Math.max(0, total - pageNumber * size)) }, (_, index) => ({
        id: pageNumber * size + index + 1, actorUserId: 2, username: 'research-demo', category: isLogin ? 'LOGIN' : 'OPERATION',
        action: isLogin ? (filtered ? 'LOGIN_FAILED' : 'LOGIN_SUCCEEDED') : 'USER_UPDATED', result: filtered ? 'FAILURE' : 'SUCCESS',
        clientIp: '127.0.0.1', userAgent: 'Mozilla/5.0 Chrome/130.0 Edg/130.0', traceId: `test-trace-${index + 1}`,
        targetType: 'USER_ACCOUNT', targetId: '2', summary: {}, createdAt: `2026-09-05T00:${String(30 - index).padStart(2, '0')}:00Z`,
      }))
      result = { items: logs, page: pageNumber, size, totalElements: total, totalPages: Math.ceil(total / size) }
    } else { status = 404; result = { detail: '未配置的测试接口' } }
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(result) })
  })
  return { requests, conflict: () => { conflict = true },
    holdCreation: () => { creationGate = new Promise((resolve) => { releaseCreation = resolve }) },
    releaseCreation: () => releaseCreation?.() }
}

test('新建资料与统一编辑保存，并保留用户名只读和管理员自我保护', async ({ page }) => {
  const state = await mockManagement(page)
  await page.goto('/users')
  await expect(page.getByRole('heading', { name: '用户类型分布' })).toBeVisible()
  await page.getByRole('button', { name: '新增用户' }).click()
  await page.getByLabel('用户名', { exact: true }).fill('new-researcher')
  await page.getByLabel('初始密码').fill('synthetic-test-password')
  await page.getByRole('button', { name: '创建用户', exact: true }).click()
  await expect(page.getByText('请输入姓名')).toBeVisible()
  await page.locator('#create-realName').fill('陈研究员')
  await page.getByLabel('邮箱', { exact: true }).fill('research@example.invalid')
  await page.getByLabel('所属单位').fill('测试科研中心')
  await page.getByRole('button', { name: '创建用户', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.getByRole('cell', { name: '陈研究员', exact: true })).toBeVisible()
  expect(state.requests.find((request) => request.method === 'POST' && request.path === '/api/v1/users')?.body.realName).toBe('陈研究员')
  const row = page.getByRole('row').filter({ has: page.getByRole('cell', { name: 'research-demo', exact: true }) })
  await row.getByRole('button', { name: '编辑', exact: true }).click()
  await expect(page.locator('#edit-username')).toHaveAttribute('readonly', '')
  await page.locator('#edit-realName').fill('林研究员（已更新）')
  await page.getByRole('button', { name: '保存修改' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.getByRole('cell', { name: '林研究员（已更新）' })).toBeVisible()
  expect(state.requests.find((request) => request.method === 'PUT')?.body.version).toBe(0)
  const self = page.getByRole('row').filter({ has: page.getByRole('cell', { name: 'admin-demo', exact: true }) })
  await self.getByRole('button', { name: '编辑', exact: true }).click()
  await expect(page.locator('#edit-status')).toBeDisabled()
  await expect(page.getByRole('dialog').getByRole('checkbox', { name: '管理员', exact: true })).toBeDisabled()
})

test('版本冲突保留资料输入，重新加载是明确操作', async ({ page }) => {
  const state = await mockManagement(page)
  await page.goto('/users')
  await page.getByRole('row').filter({ has: page.getByRole('cell', { name: 'research-demo', exact: true }) }).getByRole('button', { name: '编辑', exact: true }).click()
  await page.locator('#edit-realName').fill('未提交资料')
  state.conflict()
  await page.getByRole('button', { name: '保存修改' }).click()
  await expect(page.locator('#edit-realName')).toHaveValue('未提交资料')
  await expect(page.getByRole('button', { name: '重新加载并替换表单' })).toBeVisible()
  await expect(page.getByRole('button', { name: '保存修改' })).toBeDisabled()
})

test('从最近登录进入全部日志并分页筛选及查看来源详情', async ({ page }) => {
  await mockManagement(page)
  await page.goto('/users')
  await page.getByRole('link', { name: '查看全部', exact: true }).click()
  await expect(page).toHaveURL(/\/logs\?category=LOGIN/)
  await expect(page.getByRole('tab', { name: '登录日志' })).toHaveAttribute('aria-selected', 'true')
  await page.getByRole('button', { name: '下一页' }).click()
  await expect(page.getByText('显示 21–23，共 23 条')).toBeVisible()
  await page.getByLabel('账号', { exact: true }).fill('research-demo')
  await page.getByLabel('结果', { exact: true }).selectOption('FAILURE')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByText('显示 1–1，共 1 条')).toBeVisible()
  await page.getByRole('button', { name: '查看日志 1 详情' }).click()
  await expect(page.getByRole('dialog').getByText('127.0.0.1')).toBeVisible()
  await expect(page.getByRole('dialog').getByText('test-trace-1')).toBeVisible()
  await page.keyboard.press('Escape')
  await page.getByRole('tab', { name: '操作日志' }).click()
  await expect(page).toHaveURL(/category=OPERATION/)
})

test('统计失败不影响列表和登录日志', async ({ page }) => {
  await mockManagement(page, true)
  await page.goto('/users')
  await expect(page.getByText('统计暂不可用')).toBeVisible()
  await expect(page.getByRole('cell', { name: '林研究员', exact: true })).toBeVisible()
  await expect(page.getByRole('cell', { name: '登录成功', exact: true })).toHaveCount(10)
  await expect(page.getByText('账号总数', { exact: true })).toHaveCount(0)
})

test('离开账号页面后忽略迟到的保存响应，不再刷新统计或显示成功通知', async ({ page }) => {
  const state = await mockManagement(page)
  state.holdCreation()
  await page.goto('/users')
  await expect(page.getByRole('cell', { name: '林研究员', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '新增用户' }).click()
  await page.getByLabel('用户名', { exact: true }).fill('late-researcher')
  await page.getByLabel('初始密码').fill('synthetic-test-password')
  await page.locator('#create-realName').fill('迟到响应测试')
  await page.getByRole('button', { name: '创建用户', exact: true }).click()
  await expect.poll(() => state.requests.filter((request) => request.path === '/api/v1/users' && request.method === 'POST').length).toBe(1)
  await page.keyboard.press('Escape')
  await page.getByRole('link', { name: '日志管理', exact: true }).click()
  await expect(page.getByRole('heading', { name: '日志管理', exact: true })).toBeVisible()
  const response = page.waitForResponse((result) => result.request().method() === 'POST' && new URL(result.url()).pathname === '/api/v1/users')
  state.releaseCreation()
  await (await response).finished()
  await page.evaluate(() => new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))))
  await expect(page.getByText('用户已创建', { exact: true })).toHaveCount(0)
  expect(state.requests.filter((request) => request.path === '/api/v1/users/statistics')).toHaveLength(1)
})

test('桌面深浅主题和窄屏布局可用', async ({ page }, testInfo) => {
  await mockManagement(page)
  await page.setViewportSize({ width: 1440, height: 1080 })
  await page.emulateMedia({ colorScheme: 'light', reducedMotion: 'reduce' })
  await page.goto('/users')
  await expect(page.getByRole('cell', { name: '林研究员', exact: true })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('users-light.png'), fullPage: true })
  await page.emulateMedia({ colorScheme: 'dark' })
  await page.screenshot({ path: testInfo.outputPath('users-dark.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.screenshot({ path: testInfo.outputPath('users-mobile.png'), fullPage: true })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.getByRole('button', { name: '新增用户' }).click()
  await expect(page.locator('#create-realName')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
})
