import { expect, test } from '@playwright/test'
import { fixture, time } from './fixtures/workbench'

const columns = ['SrcDatabase-来源库', 'Title-题名', 'Author-作者', 'Organ-单位', 'Summary-摘要']
const row = { rowNumber: 2, title: '知识图谱中的学者关系建模', type: 'master-thesis', authors: ['李同学'], organizations: ['示例大学'], keywords: [], abstractText: '研究学者、成果与机构之间的关联。', publicationDate: '2025-01-01', errors: [], warnings: [] }
const preview = { previewKey: 'reviewed-cnki-table', sheets: ['信息表'], headers: columns, mapping: { database: 0, title: 1, authors: 2, organizations: 3, abstract: 4 }, totalRows: 1, validRows: 1, rows: [row], issues: [] }
const batch = { id: 1, authorId: 12, scholarName: '张老师', fileName: '硕士指导.csv', sheetName: '信息表', importMode: 'MASTER_SUPERVISION', totalRows: 1, importedCount: 1, linkedCount: 0, skippedCount: 0, createdAt: time }
const bundle = { previewKey: 'reviewed-cnki-table', scholarName: '张老师', candidates: ['张老师'], organizations: ['示例大学'], messages: ['硕博按同一学者导师姓名筛选导出。'], canConfirm: true, totalRows: 2, validRows: 2,
  files: [{ fileName: batch.fileName, modes: ['MASTER_SUPERVISION'], preview }, { fileName: '本人署名.csv', modes: ['AUTHOR'], preview: { ...preview, rows: [{ ...row, title: '本人期刊论文', type: 'article', authors: ['张老师'] }] } }] }

for (const width of [1440, 390]) {
  test(`知网信息表从预览、确认到导师图谱 ${width}px`, async ({ page }, testInfo) => {
    const errors: string[] = []
    page.on('pageerror', error => errors.push(error.message))
    const requests = await fixture(page)
    await page.setViewportSize({ width, height: 950 })
    let confirmed = false
    await page.route('**/api/v1/author-import', route => route.fulfill({ json: confirmed ? [batch] : [] }))
    await page.route('**/api/v1/author-import/files/preview', route => {
      expect(route.request().headers()['content-type']).toContain('multipart/form-data; boundary=')
      expect(route.request().headers()['x-aacv-csrf']).toBe('synthetic-visual-token')
      expect(route.request().postData()).toContain('"scholarName":""')
      expect(route.request().postData()).not.toContain('"mode":')
      return route.fulfill({ json: bundle })
    })
    await page.route('**/api/v1/author-import/files/confirm', route => {
      expect(route.request().postData()).toContain('reviewed-cnki-table')
      confirmed = true
      return route.fulfill({ json: { authorId: 12, scholarName: '张老师', importedCount: 2, linkedCount: 0, skippedCount: 0, batches: [batch] } })
    })
    await page.route('**/api/v1/graph/authors/12?*', route => route.fulfill({ json: { page: 0, size: 20, totalWorks: 1, graph: {
      nodes: [
        { id: 'AUTHOR:12', businessId: '12', type: 'AUTHOR', label: '张老师', properties: {} },
        { id: 'ACHIEVEMENT:42', businessId: '42', type: 'ACHIEVEMENT', label: row.title, properties: { abstractText: row.abstractText, achievementType: 'master-thesis' } },
      ], edges: [
        { id: 'supervised', source: 'AUTHOR:12', target: 'ACHIEVEMENT:42', type: 'SUPERVISED', properties: {} },
      ], rootNodeId: 'AUTHOR:12', truncated: false, appliedLimits: { depth: 2, nodeLimit: 100, maxHops: 6 }, syncedAt: time, projectionLagSeconds: 0,
    } } }))
    await page.goto('/author-import')
    await expect(page.getByRole('heading', { name: '作者导入', exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: '作者 ORCID 补全' })).toHaveCount(0)
    for (const label of ['数据源', '采集任务', '数据治理', '质量指标']) {
      await expect(page.getByRole('navigation', { name: '模块导航', exact: true }).getByRole('link', { name: label, exact: true })).toHaveCount(0)
    }
    await expect(page.getByRole('textbox', { name: '学者名称检索' })).toHaveCount(0)
    await expect(page.getByRole('textbox', { name: '学者所属机构' })).toHaveCount(0)
    await expect(page.getByRole('combobox', { name: '该表与学者的关系' })).toHaveCount(0)
    await page.getByLabel('选择信息表').setInputFiles([
      { name: batch.fileName, mimeType: 'text/csv', buffer: Buffer.from(`${columns.join(',')}\n硕士学位论文,${row.title},李同学,示例大学,${row.abstractText}`) },
      { name: '本人署名.csv', mimeType: 'text/csv', buffer: Buffer.from(`${columns.join(',')}\n期刊,本人期刊论文,张老师,示例大学,摘要`) },
    ])
    await page.screenshot({ path: testInfo.outputPath(`author-import-form-${width}.png`), animations: 'disabled' })
    await page.getByRole('button', { name: '解析并预览' }).click()
    await expect(page.getByRole('cell', { name: row.title, exact: true })).toBeVisible()
    const confirm = page.getByRole('button', { name: '确认导入 2 条' })
    await expect(confirm).toBeEnabled()
    await confirm.scrollIntoViewIfNeeded()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath(`author-import-preview-${width}.png`), animations: 'disabled' })
    await confirm.click()
    await expect(page.getByRole('heading', { name: '导入已完成' })).toBeVisible()
    await expect(page.getByRole('status')).toContainText('作者内部标识：12')
    await expect(page.getByRole('columnheader', { name: '作者内部标识', exact: true })).toBeVisible()
    await expect(page.getByRole('row').filter({ hasText: batch.fileName })).toContainText('12')
    await expect(confirm).toBeDisabled()
    await page.getByRole('link', { name: '查看 张老师 的知识图谱' }).click()
    await expect(page).toHaveURL(/academic-achievements\?authorId=12/)
    await expect(page.getByRole('img', { name: '张老师的学术成果图谱，2个节点' })).toBeVisible()
    await page.getByRole('button', { name: '内容清单', exact: true }).click()
    await expect(page.getByRole('list', { name: '当前页作品' })).toContainText('指导硕论')
    await page.screenshot({ path: testInfo.outputPath(`author-import-graph-${width}.png`), animations: 'disabled' })
    expect(requests.filter(path => /^\/(sources|crawl|duplicate-candidates|quality-metrics)/.test(path))).toEqual([])
    expect(requests.filter(path => path.startsWith('/author-orcids'))).toEqual([])
    expect(errors).toEqual([])
  })
}

test('预览之外的问题行可见并阻止提交，四个旧模块地址失效', async ({ page }) => {
  await fixture(page)
  await page.route('**/api/v1/author-import/files/preview', route => route.fulfill({ json: { ...bundle, totalRows: 21, validRows: 20, canConfirm: false, files: [{ ...bundle.files[0], preview: { ...preview, totalRows: 21, validRows: 20, issues: [{ rowNumber: 22, errors: ['缺少题名'], warnings: [] }] } }] } }))
  await page.goto('/author-import')
  await page.getByLabel('选择信息表').setInputFiles({ name: '问题.csv', mimeType: 'text/csv', buffer: Buffer.from('Title,Author\n论文,张老师') })
  await page.getByRole('button', { name: '解析并预览' }).click()
  await expect(page.getByText('第 22 行', { exact: true })).toBeVisible()
  await expect(page.getByText('缺少题名', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '确认导入 21 条' })).toBeDisabled()
  for (const route of ['/sources', '/crawl', '/governance', '/quality']) {
    await page.goto(route)
    await expect(page.getByRole('heading', { name: '页面不存在' })).toBeVisible()
  }
})
