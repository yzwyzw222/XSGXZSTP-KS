import { expect, test } from '@playwright/test'
import { achievement, fixture, time } from './fixtures/workbench'

test('大屏业务模块入口往返，保留成果独立详情地址', async ({ page }) => {
  test.setTimeout(60_000)
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await fixture(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/dashboard')
  for (const [name, path] of [
    ['成果目录', '/catalog'], ['实体编目', '/catalog/authors'], ['知识图谱', '/graph'],
    ['统计分析', '/analytics'], ['作者导入', '/author-import'], ['日志管理', '/logs'], ['账号管理', '/users'],
  ]) {
    await page.getByRole('navigation', { name: '大屏模块导航' }).getByRole('link', { name, exact: true }).click()
    await expect(page).toHaveURL(new RegExp(`${path}$`))
    await expect(page.getByRole('navigation', { name: '模块导航', exact: true }).getByRole('link', { name, exact: true })).toHaveAttribute('aria-current', 'page')
    await page.getByRole('navigation', { name: '模块导航', exact: true }).getByRole('link', { name: '可视化大屏', exact: true }).click()
    await expect(page.locator('.dashboard-kpis')).toContainText('1,286')
  }
  expect(errors).toEqual([])
})

for (const role of ['RESEARCHER', 'DATA_OPERATOR'] as const) {
  test(`首页在发请求之前过滤 ${role} 的受限接口`, async ({ page }) => {
    await fixture(page)
    const permissions = role === 'RESEARCHER'
      ? ['CATALOG_READ', 'GRAPH_READ', 'ANALYTICS_READ']
      : ['CATALOG_READ', 'AUTHOR_IMPORT']
    await page.route('**/api/v1/auth/me', route => route.fulfill({ json: { id: 7, username: 'permission-check', roles: [role], permissions } }))
    const requests: string[] = []
    page.on('request', request => requests.push(new URL(request.url()).pathname))
    await page.goto('/dashboard')
    await expect(page.locator('.dashboard-panel[aria-busy="true"]')).toHaveCount(0)
    const navigation = page.getByRole('navigation', { name: '大屏模块导航' })
    await expect(navigation.getByRole('link', { name: '账号管理' })).toHaveCount(0)
    await expect(navigation.getByRole('link', { name: '日志管理' })).toHaveCount(0)
    expect(requests.filter(path => path.endsWith('/operations/audits'))).toEqual([])
    if (role === 'RESEARCHER') {
      expect(requests.filter(path => path.startsWith('/api/v1/author-import'))).toEqual([])
      await expect(navigation.getByRole('link')).toHaveCount(5)
    } else {
      expect(requests.filter(path => path.startsWith('/api/v1/analytics'))).toEqual([])
      await expect(navigation.getByRole('link')).toHaveCount(4)
    }
    await expect(page.getByText('当前账号无此模块权限').first()).toBeVisible()
  })
}

for (const width of [1440, 390]) {
  test(`成果快速预览、关闭后焦点返回与完整详情 ${width}`, async ({ page }) => {
    await fixture(page)
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/catalog')
    const trigger = page.getByRole('button', { name: `预览成果：${achievement.title}`, exact: true })
    await trigger.click()
    const preview = width >= 1280 ? page.getByRole('region', { name: '成果快速预览' }) : page.getByRole('dialog', { name: '成果快速预览' })
    await expect(preview.getByRole('heading', { name: achievement.title })).toBeVisible()
    await expect(page).toHaveURL(/\/catalog$/)
    await page.screenshot({ path: `../.local/research-redesign/catalog-preview-${width}.png`, animations: 'disabled' })
    if (width >= 1280) await preview.getByRole('button', { name: '关闭预览' }).click()
    else await page.keyboard.press('Escape')
    await expect(trigger).toBeFocused()
    await trigger.click()
    await preview.getByRole('link', { name: '查看完整详情与字段溯源' }).click()
    await expect(page).toHaveURL(/\/catalog\/achievements\/42$/)
    await expect(page.getByRole('heading', { level: 1, name: achievement.title })).toBeVisible()
  })
}

test('首页局部刷新失败保留旧统计，同时显示错误原因', async ({ page }) => {
  await fixture(page)
  await page.goto('/dashboard')
  await expect(page.locator('.dashboard-kpis')).toContainText('1,286')
  await page.route('**/api/v1/analytics/overview*', route => route.fulfill({ status: 503, json: { detail: '统计服务暂不可用' } }))
  await page.getByRole('button', { name: '刷新仪表盘' }).click()
  await expect(page.getByText('更新失败，保留上次结果。统计服务暂不可用').first()).toBeVisible()
  await expect(page.locator('.dashboard-kpis')).toContainText('1,286')
  await expect(page.getByRole('img', { name: '工作台成果发表趋势折线图' })).toBeVisible()
  await page.screenshot({ path: '../.local/research-redesign/dashboard-partial-error.png', animations: 'disabled' })
})

test('保存全系统深蓝主题的浏览器预览证据', async ({ page }) => {
  test.setTimeout(90_000)
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
  await fixture(page)
  await page.setViewportSize({ width: 1920, height: 1080 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.route('**/api/v1/graph/overview*', route => route.fulfill({ json: {
    nodes: [
      { id: 'AUTHOR:1', businessId: '1', type: 'AUTHOR', label: '研究人员甲', properties: {} },
      { id: 'AUTHOR:2', businessId: '2', type: 'AUTHOR', label: '研究人员乙', properties: {} },
      ...Array.from({ length: 5 }, (_, i) => ({ id: `ACHIEVEMENT:${i + 1}`, businessId: String(i + 1), type: 'ACHIEVEMENT', label: `开放学术数据与知识关联研究 ${i + 1}`, properties: { publicationYear: 2026 } })),
    ],
    edges: [
      ...Array.from({ length: 5 }, (_, i) => ({ id: `created-${i}`, type: 'AUTHORED', source: 'AUTHOR:1', target: `ACHIEVEMENT:${i + 1}`, properties: {} })),
      ...Array.from({ length: 3 }, (_, i) => ({ id: `shared-${i}`, type: 'AUTHORED', source: 'AUTHOR:2', target: `ACHIEVEMENT:${i + 1}`, properties: {} })),
      { id: 'cooperation', type: 'COAUTHORED', source: 'AUTHOR:1', target: 'AUTHOR:2', properties: { sharedWorkIds: ['ACHIEVEMENT:1', 'ACHIEVEMENT:2', 'ACHIEVEMENT:3'], sharedWorkCount: 3 } },
    ], rootNodeId: 'AUTHOR:1', truncated: false, appliedLimits: { depth: 2, nodeLimit: 300, maxHops: 0 }, syncedAt: time, projectionLagSeconds: 0, traceId: 'preview-graph',
  } }))
  await page.route('**/api/v1/analytics/collaboration*', route => route.fulfill({ json: {
    authors: Array.from({ length: 5 }, (_, i) => ({ leftId: i + 1, leftLabel: `研究人员 ${i + 1}`, rightId: i + 8, rightLabel: `研究人员 ${i + 8}`, sharedAchievementCount: 18 - i * 2 })),
    organizations: Array.from({ length: 12 }, (_, i) => ({ leftId: i % 5 + 1, leftLabel: `研究机构 ${i % 5 + 1}`, rightId: i % 7 + 6, rightLabel: `研究机构 ${i % 7 + 6}`, sharedAchievementCount: 26 - i })),
    scope: { source: 'MYSQL', filters: {} }, updatedAt: time,
  } }))
  await page.route('**/api/v1/analytics/distributions*', route => route.fulfill({ json: {
    achievementTypes: [{ key: 'article', label: '期刊论文', achievementCount: 1028 }, { key: 'conference', label: '会议论文', achievementCount: 168 }, { key: 'other', label: '其他成果', achievementCount: 90 }],
    sources: [{ key: 'OPENALEX', label: 'OpenAlex', achievementCount: 800 }, { key: 'CROSSREF', label: 'Crossref', achievementCount: 620 }],
    topics: ['人工智能', '材料科学', '生物医学', '计算机科学', '环境科学', '其他主题'].map((label, i) => ({ key: String(i + 1), label, achievementCount: 328 - i * 36 })),
    organizations: [{ key: '1', label: '开放科学研究院', achievementCount: 128 }], scope: { source: 'MYSQL', filters: {} }, updatedAt: time,
  } }))
  await page.route('**/api/v1/author-import', route => route.fulfill({ json: Array.from({ length: 3 }, (_, i) => ({ id: i + 1, authorId: i + 1, scholarName: `测试学者 ${i + 1}`, fileName: '论文.csv', importMode: 'AUTHOR', importedCount: 5, linkedCount: 0, skippedCount: 0, createdAt: time })) }))
  await page.route('**/api/v1/operations/audits*', route => route.fulfill({ json: {
    items: ['CRAWL_TASK_CREATED', 'EXPORT_SUCCEEDED', 'SOURCE_UPDATED', 'LOGIN_SUCCEEDED'].map((action, i) => ({ id: i + 1, actorUserId: 1, action, targetType: 'CRAWL_TASK', targetId: String(i + 1), result: 'SUCCESS', traceId: 'preview-evidence', summary: {}, category: 'OPERATION', createdAt: time })), page: 0, size: 20, totalElements: 4, totalPages: 1,
  } }))
  for (const [path, name] of [
    ['/dashboard', 'dashboard'], ['/catalog', 'catalog'], ['/catalog/achievements/42', 'achievement-detail'], ['/catalog/authors', 'entities'],
    ['/graph', 'graph'], ['/analytics', 'analytics'], ['/author-import', 'author-import'], ['/users', 'users'], ['/logs', 'logs'],
  ]) {
    await page.goto(path!)
    await expect(page.locator('#main-content').getByRole('heading', { level: 1 })).toBeVisible()
    await expect(page.locator('#main-content [aria-busy="true"]')).toHaveCount(0)
    await expect(page.locator('.el-skeleton')).toHaveCount(0)
    if (name === 'dashboard') {
      await expect(page.locator('.dashboard-ranking li').last()).toBeInViewport({ ratio: 1 })
      await expect(page.locator('.dashboard-task-list li').last()).toBeInViewport({ ratio: 1 })
    }
    if (name === 'analytics') {
      await expect(page.locator('.analytics-content--summary > .panel-section .panel-section__title')).toHaveText(['年度成果趋势', '成果类型分布', '字段覆盖摘要', '作者合作排行'])
      await expect(page.getByRole('img', { name: '统计成果类型分布环形图' })).toBeVisible()
    }
    if (name === 'graph') await expect(page.getByRole('img', { name: '知识图谱，共7个节点和9条关系' })).toBeVisible()
    await page.screenshot({ path: `../.local/research-redesign/${name}-1920.png`, animations: 'disabled' })
    if (name === 'users' || name === 'logs') {
      const trigger = name === 'logs'
        ? page.getByRole('button', { name: /查看日志 .* 详情/ }).first()
        : page.getByRole('button', { name: '编辑', exact: true }).first()
      await trigger.click()
      await expect(page.getByRole('dialog')).toBeVisible()
      await page.screenshot({ path: `../.local/research-redesign/${name}-detail-1920.png`, animations: 'disabled' })
      await page.keyboard.press('Escape')
      await expect(page.getByRole('dialog')).toHaveCount(0)
    }
  }
  expect(errors).toEqual([])
})
