import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { closeSync, mkdirSync, openSync, readFileSync, unlinkSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
const runtime = path.join(root, '.local/integration-runtime')
const checkOnly = process.argv.includes('--check')
assert.ok(process.argv.slice(2).every(argument => argument === '--check'), '仅支持 --check 参数')
const stamp = new Date().toISOString().slice(0, 23).replace('T', ' ')
const numericId = id => 910000 + id
const textId = (kind, id) => `course-demo-${kind}-${id}`
const marked = text => `[试用] ${text}`
const doi = id => `10.9999/course-demo.${id}`
const quote = value => value === null ? 'NULL' : typeof value === 'number' ? String(value) : `'${String(value).replaceAll('\\', '\\\\').replaceAll("'", "''")}'`
const identifier = value => {
  assert.match(value, /^[a-z_]+$/)
  return `\`${value}\``
}
const insert = (table, row) => `INSERT INTO ${identifier(table)} (${Object.keys(row).map(identifier).join(',')}) VALUES (${Object.values(row).map(quote).join(',')});`

function docker(args, input) {
  const result = spawnSync('docker', args, { input, encoding: 'utf8', timeout: 90000, maxBuffer: 4 * 1024 * 1024, windowsHide: true, cwd: root })
  if (result.error) throw new Error(`Docker 命令未完成：${result.error.code ?? '未知执行错误'}`)
  if (result.status !== 0) throw new Error(`Docker 命令失败：${result.stderr.trim()}`)
  return result.stdout.trim()
}

function mysql(system, sql) {
  // 凭据由容器环境提供，不读取本机凭据文件，也不放入进程参数或输出。
  return docker(['exec', '-i', `course-integration-${system}-mysql-1`, 'sh', '-c',
    'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql --default-character-set=utf8mb4 --batch --raw --skip-column-names -u "$MYSQL_USER" "$MYSQL_DATABASE"'], sql)
}

function verifyOwner(system) {
  const owner = docker(['inspect', '--format', '{{index .Config.Labels "com.docker.compose.project.working_dir"}}', `course-integration-${system}-mysql-1`])
  assert.equal(path.resolve(owner).toLowerCase(), path.resolve(runtime).toLowerCase(), `${system} 容器不属于当前工作区`)
}

// 只解析仓库现有样例中的简单 VALUES 数据，绝不执行其 DELETE 或其他 SQL。
function readSample() {
  const source = readFileSync(path.join(root, 'systems/relation/docs/sql/sample-data.sql'), 'utf8').replace(/^\s*--[^\n]*/gm, '')
  const supportedTables = new Set(['institution', 'author', 'venue', 'keyword', 'paper', 'paper_author', 'paper_keyword', 'paper_reference'])
  const tables = {}
  for (const match of source.matchAll(/INSERT INTO (\w+)\s*\(([^)]+)\)\s*VALUES\s*([\s\S]*?);/g)) {
    const [, table, columnList, values] = match
    assert.ok(supportedTables.has(table), `样例包含未支持的表 ${table}`)
    const columns = columnList.split(',').map(value => value.trim())
    const tuples = [...values.matchAll(/\(([^()]*)\)/g)]
    assert.equal(values.replace(/\(([^()]*)\)/g, '').replace(/[\s,]/g, ''), '', `${table} 包含未支持的表达式`)
    tables[table] = tuples.map(tuple => {
      const tokens = [...tuple[1].matchAll(/'(?:[^']|'')*'|NULL|-?\d+/g)].map(token => token[0])
      assert.equal(tuple[1].replace(/'(?:[^']|'')*'|NULL|-?\d+/g, '').replace(/[\s,]/g, ''), '', `${table} 包含未支持的值`)
      assert.equal(tokens.length, columns.length, `${table} 字段数不匹配`)
      return Object.fromEntries(columns.map((column, index) => {
        const token = tokens[index]
        return [column, token === 'NULL' ? null : token.startsWith("'") ? token.slice(1, -1).replaceAll("''", "'") : Number(token)]
      }))
    })
  }
  for (const table of supportedTables) assert.ok(tables[table]?.length > 0, `${table} 缺少样例`)
  assert.equal(tables.paper.length, 30, '论文样例数量已变化，请先核对样例来源')
  return tables
}

const sample = readSample()
const inRange = column => `${identifier(column)} BETWEEN 910100 AND 910999`
const textRange = column => `${identifier(column)} LIKE 'course-demo-%'`
const audit = { created_at: stamp, updated_at: stamp }
const builders = {}

builders.relation = () => {
  const rows = {}
  for (const [table, originals] of Object.entries(sample)) {
    rows[table] = originals.map(original => {
      const row = Object.fromEntries(Object.entries(original).map(([key, value]) => [key, value !== null && (key === 'id' || key.endsWith('_id')) ? numericId(value) : value]))
      row.created_at = stamp
      if (['institution', 'author', 'venue', 'keyword', 'paper'].includes(table)) row.updated_at = stamp
      if (['institution', 'author', 'paper'].includes(table)) row.version = 0
      if (row.display_name) row.display_name = marked(row.display_name)
      if (table === 'author') row.orcid = null
      if (table === 'keyword') row.name = marked(row.name)
      if (table === 'paper') {
        row.title = marked(row.title)
        row.doi = doi(original.id)
        row.abstract_text = `本记录为虚构试用数据。${row.abstract_text}`
        row.publication_year = Number(row.publication_date.slice(0, 4))
        row.extraction_status = 'PENDING'
      }
      return row
    })
  }
  const queries = Object.entries(rows).map(([table, values]) => ({ table, where: inRange(table === 'paper_reference' ? 'citing_paper_id' : table.startsWith('paper_') ? 'paper_id' : 'id'), expected: values.length }))
  const statements = Object.entries(rows).flatMap(([table, values]) => values.map(row => insert(table, row)))
  for (const [table, type] of [['venue', 'VENUE'], ['institution', 'INSTITUTION'], ['keyword', 'KEYWORD'], ['author', 'AUTHOR'], ['paper', 'PAPER']]) {
    for (const row of rows[table]) statements.push(insert('graph_sync_event', { entity_type: type, entity_id: row.id, event_type: 'UPSERT', status: 'PENDING', attempts: 0, created_at: stamp }))
  }
  return { statements, queries, marker: "SELECT COUNT(*) FROM paper WHERE doi LIKE '10.9999/course-demo.%' AND title LIKE '[试用] %'", markerCount: 30 }
}

builders.extraction = () => {
  const authors = sample.author.map(author => {
    const papers = sample.paper_author.filter(row => row.author_id === author.id).map(row => sample.paper.find(paper => paper.id === row.paper_id))
    const affiliations = [...new Set(sample.paper_author.filter(row => row.author_id === author.id).map(row => sample.institution.find(institution => institution.id === row.institution_id).display_name))]
    const citations = papers.map(paper => paper.citation_count).sort((left, right) => right - left)
    return { id: numericId(author.id), semantic_scholar_id: textId('author', author.id), name: marked(author.display_name), affiliation: affiliations.map(marked).join('；'), paper_count: papers.length, citation_count: citations.reduce((total, count) => total + count, 0), h_index: citations.filter((count, index) => count >= index + 1).length }
  })
  const venues = sample.venue.map(row => ({ id: numericId(row.id), name: marked(row.display_name), type: row.venue_type === 'conference' ? 'CONFERENCE' : 'JOURNAL' }))
  const papers = sample.paper.map(row => ({ id: numericId(row.id), semantic_scholar_id: textId('paper', row.id), title: marked(row.title), abstract_text: `本记录为虚构试用数据。${row.abstract_text}`, year: Number(row.publication_date.slice(0, 4)), doi: doi(row.id), citation_count: row.citation_count, reference_count: sample.paper_reference.filter(reference => reference.citing_paper_id === row.id).length, venue_id: numericId(row.venue_id), extraction_status: row.id <= 105 ? 'COMPLETED' : 'PENDING' }))
  const topics = sample.keyword.map(row => ({ id: numericId(row.id), name: marked(row.name), description: `试用研究主题：${row.field_name}` }))
  const entities = sample.paper.slice(0, 5).flatMap((row, index) => [
    { id: 910501 + index * 2, paper_id: numericId(row.id), entity_name: '图神经网络', entity_type: 'METHOD', properties: JSON.stringify({ fixture: 'course-demo', note: '人工编写的展示结果，未经模型调用' }) },
    { id: 910502 + index * 2, paper_id: numericId(row.id), entity_name: '学术知识图谱', entity_type: 'TOPIC', properties: JSON.stringify({ fixture: 'course-demo' }) },
  ])
  const relationships = sample.paper.slice(0, 5).map((row, index) => ({ id: 910501 + index, paper_id: numericId(row.id), source_entity_id: 910501 + index * 2, target_entity_id: 910502 + index * 2, relationship_type: 'APPLIED_TO', evidence_text: '[试用] 人工编写的实体关系，仅供界面操作测试。', confidence: 0.92 }))
  const rows = { authors, venues, papers, research_topics: topics,
    paper_authors: sample.paper_author.map(row => ({ paper_id: numericId(row.paper_id), author_id: numericId(row.author_id), author_position: row.author_position })),
    paper_topics: sample.paper_keyword.map(row => ({ paper_id: numericId(row.paper_id), topic_id: numericId(row.keyword_id), confidence: 0.95 })),
    paper_citations: sample.paper_reference.map(row => ({ citing_paper_id: numericId(row.citing_paper_id), cited_paper_id: numericId(row.cited_paper_id) })),
    extracted_entities: entities, entity_relationships: relationships }
  const statements = Object.entries(rows).flatMap(([table, values]) => values.map(row => insert(table, row)))
  const event = (type, aggregate, id, payload) => statements.push(insert('outbox_events', { event_type: type, aggregate_type: aggregate, aggregate_id: id, payload: JSON.stringify(payload) }))
  for (const paper of papers) event('PAPER_CREATED', 'PAPER', paper.id, { id: paper.id, title: paper.title })
  for (const author of authors) event('AUTHOR_CREATED', 'AUTHOR', author.id, { id: author.id, name: author.name })
  for (const entity of entities) event('ENTITY_CREATED', 'ENTITY', entity.id, { id: entity.id, name: entity.entity_name, type: entity.entity_type, paperId: entity.paper_id })
  for (const relation of relationships) event('RELATIONSHIP_CREATED', 'RELATIONSHIP', relation.id, { sourceId: relation.source_entity_id, targetId: relation.target_entity_id, relType: relation.relationship_type })
  for (const reference of rows.paper_citations) event('CITATION_CREATED', 'PAPER', reference.citing_paper_id, { citingId: reference.citing_paper_id, citedId: reference.cited_paper_id })
  const queries = Object.entries(rows).map(([table, values]) => ({ table, where: inRange(table === 'paper_citations' ? 'citing_paper_id' : table.startsWith('paper_') ? 'paper_id' : 'id'), expected: values.length }))
  return { statements, queries, marker: "SELECT COUNT(*) FROM papers WHERE semantic_scholar_id LIKE 'course-demo-paper-%' AND title LIKE '[试用] %'", markerCount: 30 }
}

builders.scholar = () => {
  const rows = {
    institution: sample.institution.map(row => ({ institution_id: textId('institution', row.id), name: marked(row.display_name), normalized_name: marked(row.display_name).toLowerCase(), country: row.country_code })),
    author: sample.author.map(row => ({ author_id: textId('author', row.id), name: marked(row.display_name), normalized_name: marked(row.display_name).toLowerCase() })),
    venue: sample.venue.map(row => ({ venue_id: textId('venue', row.id), name: marked(row.display_name), normalized_name: marked(row.display_name).toLowerCase(), type: row.venue_type })),
    topic: sample.keyword.map(row => ({ topic_id: textId('topic', row.id), name: marked(row.name), normalized_name: marked(row.name), description: row.field_name })),
    paper: sample.paper.map(row => ({ paper_id: textId('paper', row.id), title: marked(row.title), abstract: `本记录为虚构试用数据。${row.abstract_text}`, doi: doi(row.id), publication_date: row.publication_date, year: Number(row.publication_date.slice(0, 4)), paper_type: row.paper_type, venue_id: textId('venue', row.venue_id) })),
    authorship: sample.paper_author.map(row => ({ authorship_id: textId('authorship', `${row.paper_id}-${row.author_id}`), author_id: textId('author', row.author_id), paper_id: textId('paper', row.paper_id), institution_id: textId('institution', row.institution_id), author_order: row.author_position, is_corresponding: row.author_position === 1 ? 1 : 0, source: 'COURSE_DEMO' })),
    paper_topic: sample.paper_keyword.map(row => ({ paper_id: textId('paper', row.paper_id), topic_id: textId('topic', row.keyword_id), version: 0 })),
    paper_reference: sample.paper_reference.map(row => ({ citing_paper_id: textId('paper', row.citing_paper_id), cited_paper_id: textId('paper', row.cited_paper_id), version: 0 })),
  }
  const statements = Object.entries(rows).flatMap(([table, values]) => values.map(row => insert(table, { ...row, ...audit })))
  const queries = Object.entries(rows).map(([table, values]) => ({ table, where: textRange(table === 'paper_reference' ? 'citing_paper_id' : table === 'paper_topic' ? 'paper_id' : `${table}_id`), expected: values.length }))
  return { statements, queries, marker: "SELECT COUNT(*) FROM paper WHERE paper_id LIKE 'course-demo-paper-%' AND title LIKE '[试用] %'", markerCount: 30 }
}

builders.crawler = () => {
  const actor = mysql('crawler', "SELECT u.id FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.username='admin' AND u.status='ACTIVE' AND r.role_code='ADMIN';")
  assert.match(actor, /^\d+$/, 'crawler 必须已存在可用的 admin 管理员；导入不创建或修改账号')
  const fixture = readFileSync(path.join(root, 'systems/crawler/tools/development/rendering-sample-data.sql'), 'utf8')
  assert.ok(!/^\s*(?:DELETE|TRUNCATE|DROP|ALTER)\b/im.test(fixture), 'crawler 样例出现破坏性语句，拒绝导入')
  const statements = [`SET @aacv_demo_actor_id=${actor};`, fixture.replace(/^START TRANSACTION;\s*|^COMMIT;\s*/gm, '')]
  const queries = [
    { table: 'achievement', where: "doi_normalized LIKE '10.9999/aacv-demo.%'", expected: 12 },
    { table: 'author_external_id', where: "external_id LIKE 'https://openalex.org/AACVDEMOA%'", expected: 8 },
    { table: 'organization', where: "openalex_id LIKE 'https://openalex.org/AACVDEMOI%'", expected: 4 },
    { table: 'topic', where: "openalex_id LIKE 'https://openalex.org/AACVDEMOT%'", expected: 5 },
    { table: 'crawl_task', where: "task_name LIKE '[页面测试] %'", expected: 2 },
  ]
  return { statements, queries, marker: "SELECT COUNT(*) FROM achievement WHERE doi_normalized LIKE '10.9999/aacv-demo.%'", markerCount: 12 }
}

function counts(system, plan) {
  const sql = plan.queries.map(query => `SELECT COUNT(*) FROM ${identifier(query.table)} WHERE ${query.where};`).join('\n')
  const values = mysql(system, sql).split(/\r?\n/).map(Number)
  assert.equal(values.length, plan.queries.length, `${system} 计数结果不完整`)
  assert.ok(values.every(Number.isSafeInteger), `${system} 计数结果无效`)
  return values
}

const lockPath = path.join(root, '.local/integration/demo-import.lock')
mkdirSync(path.dirname(lockPath), { recursive: true })
let lock
try {
  // 文件锁只协调本工作区的导入脚本；失败时保留数据库已有内容，不清库重试。
  lock = openSync(lockPath, 'wx')
  const plans = {}
  for (const system of ['relation', 'extraction', 'crawler', 'scholar']) {
    verifyOwner(system)
    const plan = builders[system]()
    const existing = counts(system, plan)
    const complete = existing.every((count, index) => count === plan.queries[index].expected)
      && Number(mysql(system, `${plan.marker};`)) === plan.markerCount
    if (checkOnly) assert.ok(complete, `${system} 测试数据数量不完整`)
    else assert.ok(complete || existing.every(count => count === 0), `${system} 测试数据标识存在冲突或部分数据，拒绝覆盖；请先人工核对`)
    plans[system] = { ...plan, complete }
  }
  for (const [system, plan] of Object.entries(plans)) {
    if (!checkOnly && !plan.complete) {
      // 每个数据库独立事务；mysql 批处理发生错误即退出，未提交事务由连接关闭回滚。
      mysql(system, `SET NAMES utf8mb4;\nSTART TRANSACTION;\n${plan.statements.join('\n')}\nCOMMIT;`)
    }
    const actual = counts(system, plan)
    for (const [index, query] of plan.queries.entries()) assert.equal(actual[index], query.expected, `${system}.${query.table} 导入数量不符`)
    process.stdout.write(`${system}：${checkOnly ? '核对通过' : plan.complete ? '样例已存在，保留原内容' : '测试数据已导入'}；${plan.queries.map((query, index) => `${query.table}=${actual[index]}`).join('，')}。\n`)
  }
} catch (error) {
  process.stderr.write(`测试数据准备失败：${error.message}\n`)
  process.exitCode = 1
} finally {
  if (lock !== undefined) {
    closeSync(lock)
    unlinkSync(lockPath)
  }
}
