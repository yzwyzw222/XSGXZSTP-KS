import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { closeSync, mkdirSync, openSync, readFileSync, unlinkSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
const runtime = path.join(root, '.local/integration-runtime')
const checkOnly = process.argv.includes('--check')
assert.ok(process.argv.slice(2).every(argument => argument === '--check'), '仅支持 --check 参数')
const identifier = value => {
  assert.match(value, /^[a-z_]+$/)
  return `\`${value}\``
}

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

const builders = {}

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
  for (const system of ['crawler']) {
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
