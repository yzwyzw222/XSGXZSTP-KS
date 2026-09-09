import { expect, test, type Page } from '@playwright/test'

function candidate(id: number) {
  return {
    id, entityType: 'ACHIEVEMENT', leftEntityId: id * 10, rightEntityId: id * 10 + 1,
    matchBasis: 'FINGERPRINT', evidence: { title: '待核对的同名成果' }, status: 'PENDING',
    sourceId: 1, ruleVersion: 1, version: 2, createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z',
  }
}
function comparison(id: number, explicitVersionRelation = false) {
  return {
    candidateId: id, candidateVersion: 2, entityType: 'ACHIEVEMENT', leftEntityId: id * 10, rightEntityId: id * 10 + 1,
    left: { displayName: `原始标题 ${id}`, doi: null, sourceCount: 1 },
    right: { displayName: `人工修正标题 ${id}`, doi: `10.1000/work-${id}`, sourceCount: 2 }, explicitVersionRelation,
  }
}
async function setup(page: Page, manage = true) {
  await page.route('**/api/v1/**', (route) => route.fulfill({ json: { items: [], totalElements: 0, totalPages: 0, page: 0, size: 20 } }))
  await page.route('**/api/v1/auth/me', (route) => route.fulfill({ json: {
    id: 3, username: 'governance-test', roles: ['DATA_OPERATOR'],
    permissions: ['GOVERNANCE_READ', ...(manage ? ['GOVERNANCE_MANAGE'] : [])],
  } }))
  await page.route('**/api/v1/auth/csrf', (route) => route.fulfill({ json: {
    headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'csrf-test-only',
  } }))
  await page.route('**/api/v1/duplicate-candidates?*', (route) => route.fulfill({ json: {
    items: [candidate(12), candidate(13)], totalElements: 2, totalPages: 1, page: 0, size: 20,
  } }))
  for (const id of [12, 13]) {
    await page.route(`**/api/v1/duplicate-candidates/${id}`, (route) => route.fulfill({ json: candidate(id) }))
    await page.route(`**/api/v1/duplicate-candidates/${id}/comparison`, (route) => route.fulfill({ json: comparison(id) }))
  }
  await page.goto('/governance')
}

test('必须明确选择规范实体，成功后不重复展示提交按钮', async ({ page }) => {
  await setup(page)
  let submitted: unknown = null
  await page.route('**/api/v1/duplicate-candidates/12/accept', async (route) => {
    submitted = route.request().postDataJSON()
    await route.fulfill({ json: { id: 90, candidateId: 12, decision: 'ACCEPT', canonicalEntityId: 121, revisionId: 1, version: 0 } })
  })
  await page.getByRole('button', { name: '审阅证据' }).first().click()
  await expect(page.getByRole('table', { name: '候选实体字段对照' })).toBeVisible()
  await expect(page.getByText('人工修正标题 12')).toBeVisible()
  await page.getByRole('textbox', { name: '治理原因', exact: true }).fill('核对来源标识及标题')
  await page.getByRole('button', { name: '接受并合并' }).click()
  await expect(page.getByText('请选择保留的规范实体 ID', { exact: true })).toBeVisible()
  expect(submitted).toBeNull()
  await page.getByRole('combobox', { name: '保留的规范实体 ID' }).click()
  await page.getByRole('option', { name: '保留右侧 #121' }).click()
  await page.screenshot({ path: 'test-results/governance-comparison.png', animations: 'disabled' })
  await page.getByRole('button', { name: '接受并合并' }).click()
  await expect(page.getByText('决定 #90 已记录')).toBeVisible()
  await expect(page.getByRole('button', { name: '接受并合并' })).toHaveCount(0)
  expect(submitted).toEqual({ canonicalEntityId: 121, reason: '核对来源标识及标题', version: 2 })
})

test('关闭旧候选后迟到的响应不能覆盖新候选', async ({ page }) => {
  await setup(page)
  let release!: () => void
  let started!: () => void
  const pending = new Promise<void>((resolve) => { release = resolve })
  const requested = new Promise<void>((resolve) => { started = resolve })
  let finished!: () => void
  const completed = new Promise<void>((resolve) => { finished = resolve })
  await page.route('**/api/v1/duplicate-candidates/12/comparison', async (route) => {
    started()
    await pending
    await route.fulfill({ json: comparison(12) })
    finished()
  })
  await page.getByRole('button', { name: '审阅证据' }).first().click()
  await requested
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.getByRole('button', { name: '审阅证据' }).nth(1).click()
  await expect(page.getByText('原始标题 13')).toBeVisible()
  release()
  await completed
  await expect(page.getByText('原始标题 12')).toHaveCount(0)
  await expect(page.getByRole('columnheader', { name: '左侧 #130' })).toBeVisible()
})

test('版本关系阻止合并，比较失败时不能沿用旧详情提交', async ({ page }) => {
  await setup(page)
  await page.route('**/api/v1/duplicate-candidates/12/comparison', (route) => route.fulfill({ json: comparison(12, true) }))
  await page.getByRole('button', { name: '审阅证据' }).first().click()
  await expect(page.getByRole('button', { name: '接受并合并' })).toBeDisabled()
  await expect(page.getByText('来源明确声明版本关系，应保留独立记录；可拒绝此重复候选。')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.route('**/api/v1/duplicate-candidates/13/comparison', (route) => route.fulfill({ status: 503, json: { message: '比较服务暂不可用' } }))
  await page.getByRole('button', { name: '审阅证据' }).nth(1).click()
  await expect(page.getByRole('dialog').getByRole('alert')).toBeVisible()
  await expect(page.getByRole('button', { name: '接受并合并' })).toHaveCount(0)
  await expect(page.getByText('原始标题 12')).toHaveCount(0)
})

test('只读权限可对照字段但不能提交治理决定', async ({ page }) => {
  await setup(page, false)
  await page.getByRole('button', { name: '审阅证据' }).first().click()
  await expect(page.getByRole('table', { name: '候选实体字段对照' })).toBeVisible()
  await expect(page.getByRole('button', { name: '接受并合并' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '拒绝候选' })).toHaveCount(0)
})
