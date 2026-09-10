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
  for (const system of ['relation', 'crawler']) {
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
