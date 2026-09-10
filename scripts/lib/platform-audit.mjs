import { appendFileSync, existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { randomUUID } from 'node:crypto'
import { identityCookies } from './auth.mjs'

/** 网关只记录固定路由、身份与响应结果，不读取请求体、查询值、Cookie 或原始异常。 */
export function auditTarget(pathname) {
  if (/^\/__integration\/auth\/(login|logout)$/.test(pathname)) return { system: 'portal', resource: pathname }
  const match = pathname.match(/^\/(relation|crawler)\/api\/v1\/(.+)$/)
  if (!match || /^auth\/(csrf|me)$/.test(match[2])) return null
  const segments = match[2].split('/').slice(0, 6)
  const known = new Set(['auth', 'users', 'login', 'logout', 'register', 'statistics', 'overview', 'analytics', 'papers', 'paper', 'authors', 'author', 'institutions', 'keywords', 'graph', 'relations', 'nodes', 'edges', 'entities', 'extraction', 'extract', 'search', 'crawl', 'tasks', 'runs', 'sources', 'catalog', 'achievements', 'governance', 'quality', 'operations', 'audits', 'alerts', 'events', 'exports', 'maintenance', 'scholars', 'scholar', 'recommendations'])
  for (const action of ['enable', 'disable', 'reset-password', 'roles', 'trigger', 'pause', 'resume', 'cancel', 'failures', 'retry-failures', 'schedules', 'schedule']) known.add(action)
  const system = match[1] === 'crawler' && segments[0] === 'users' ? 'portal' : match[1]
  return { system, resource: `/api/v1/${segments.map(segment => known.has(segment) ? segment : ':resource').join('/')}` }
}

/** 每次查询确认实时身份，沿用 crawler 的权限，不建立独立管理员或授权缓存。 */
export async function platformIdentity(request, config) {
  const system = config.systems.find(entry => entry.id === 'crawler' && entry.status === 'enabled')
  if (!system) throw new Error('IDENTITY_UNAVAILABLE')
  const response = await fetch(`http://127.0.0.1:${system.runtime.backendPort}${system.runtime.contextPath ?? ''}/api/v1/auth/me`, {
    headers: { Cookie: identityCookies(request.headers.cookie), Accept: 'application/json' },
    redirect: 'error', signal: AbortSignal.timeout(6000),
  })
  if (response.status === 401) return null
  if (!response.ok) throw new Error('IDENTITY_UNAVAILABLE')
  const user = await response.json()
  if (!Array.isArray(user.permissions) || typeof user.username !== 'string') throw new Error('IDENTITY_INVALID')
  return user
}

export function createPlatformAudit(root, limit = 10000) {
  const file = path.join(root, '.local/integration/platform-audit.jsonl')
  let entries
  let unavailable = false
  function read() {
    if (entries) return entries
    entries = existsSync(file) ? readFileSync(file, 'utf8').split('\n').filter(Boolean).map(line => JSON.parse(line)).slice(-limit) : []
    return entries
  }
  function record(entry) {
    try {
      const current = read()
      const row = { id: randomUUID(), createdAt: new Date().toISOString(), ...entry }
      mkdirSync(path.dirname(file), { recursive: true })
      appendFileSync(file, `${JSON.stringify(row)}\n`, { mode: 0o600 })
      current.push(row)
      if (current.length > limit) {
        const retained = current.slice(-Math.floor(limit * 0.9))
        writeFileSync(`${file}.tmp`, retained.map(value => JSON.stringify(value)).join('\n') + '\n', { mode: 0o600 })
        renameSync(`${file}.tmp`, file)
        entries = retained
      }
      unavailable = false
    } catch {
      unavailable = true
      entries = undefined
      process.stderr.write('平台日志写入失败，请检查本机日志目录权限和磁盘空间。\n')
    }
  }
  return {
    record,
    observe(request, response, target, userPromise) {
      const identity = userPromise.catch(() => undefined)
      const started = Date.now()
      let recorded = false
      const finish = async () => {
        if (recorded) return
        recorded = true
        const durationMs = Date.now() - started
        const user = await identity
        record({ ...target, username: user?.username ?? (user === null ? '未登录' : '身份服务不可用'),
          method: request.method, status: response.writableFinished ? response.statusCode : 499,
          durationMs })
      }
      response.once('finish', finish)
      response.once('close', finish)
    },
    query(search) {
      const page = Number(search.get('page') ?? 0)
      const size = Number(search.get('size') ?? 20)
      const system = search.get('system') || ''
      const result = search.get('result') || ''
      const username = (search.get('username') || '').trim()
      const from = search.get('from') || ''
      const to = search.get('to') || ''
      if (!Number.isSafeInteger(page) || page < 0 || page > 10000 || !Number.isInteger(size) || size < 1 || size > 100 ||
        !['', 'portal', 'relation', 'crawler'].includes(system) || !['', 'SUCCESS', 'FAILURE'].includes(result) || username.length > 64 ||
        (from && !Number.isFinite(Date.parse(from))) || (to && !Number.isFinite(Date.parse(to))) || (from && to && Date.parse(from) >= Date.parse(to))) {
        throw new RangeError('日志筛选参数无效。')
      }
      if (unavailable) throw new Error('AUDIT_UNAVAILABLE')
      const rows = read().filter(row => (!system || row.system === system) && (!username || row.username.includes(username)) &&
        (!result || (row.status < 400 ? 'SUCCESS' : 'FAILURE') === result) &&
        (!from || Date.parse(row.createdAt) >= Date.parse(from)) && (!to || Date.parse(row.createdAt) < Date.parse(to))).reverse()
      return { items: rows.slice(page * size, (page + 1) * size), page, size, totalElements: rows.length, totalPages: Math.ceil(rows.length / size), retentionLimit: limit }
    },
  }
}
