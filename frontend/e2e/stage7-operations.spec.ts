import { expect, test } from '@playwright/test'
import { fixture, time } from './fixtures/workbench'

test('旧运维入口统一进入日志，保留事件详情与登录筛选', async ({ page }) => {
  await fixture(page)
  const categories: string[] = []
  const writes: string[] = []
  page.on('request', request => {
    if (request.method() !== 'GET') writes.push(request.url())
  })
  await page.route('**/api/v1/operations/audits**', route => {
    const category = new URL(route.request().url()).searchParams.get('category') ?? ''
    categories.push(category)
    return route.fulfill({ json: {
      items: [{ id: 17, actorUserId: 1, username: 'research-demo', action: category === 'LOGIN' ? 'LOGIN_SUCCEEDED' : 'ALERT_ACKNOWLEDGED', result: 'SUCCESS', targetType: 'ALERT_EVENT', targetId: '9', summary: { reason: '已核对采集记录' }, traceId: 'synthetic-log-17', createdAt: time }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    } })
  })
  await page.goto('/operations/audits')
  await expect(page).toHaveURL(/\/logs$/)
  await expect(page.getByRole('cell', { name: 'research-demo', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '查看日志 17 详情' }).click()
  await expect(page.getByRole('dialog', { name: '日志详情' })).toContainText('synthetic-log-17')
  await expect(page.getByRole('dialog', { name: '日志详情' })).toContainText('已核对采集记录')
  await page.keyboard.press('Escape')
  await page.getByRole('tab', { name: '登录日志' }).click()
  await expect(page.getByRole('cell', { name: '登录成功', exact: true })).toBeVisible()
  expect(categories).toEqual(['OPERATION', 'LOGIN'])
  expect(writes).toEqual([])
  await expect(page.getByRole('button', { name: /启动对账|全量重建|重放|系统通知/ })).toHaveCount(0)
})

test('只有运维读取权限不能绕过日志权限', async ({ page }) => {
  await fixture(page)
  const audits: string[] = []
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: {
    id: 1, username: 'permission-test', roles: ['RESEARCHER'], permissions: ['OPERATIONS_READ'],
  } }))
  page.on('request', request => { if (request.url().includes('/api/v1/operations/audits')) audits.push(request.url()) })
  await page.goto('/operations')
  await expect(page).toHaveURL(/\/forbidden$/)
  await expect(page.getByRole('heading', { name: '当前账号无权访问' })).toBeVisible()
  expect(audits).toEqual([])
})
