/**
 * CNKI HTML 格式 .xls 导入脚本
 * 用法: node scripts/import-cnki.mjs <xls文件路径> [--merge]
 *   默认:    覆盖导入（清空 scholar 域全部表后全量重建，用户表不受影响）
 *   --merge: 合并导入（保留库中已有数据，仅追加新论文；已存在的论文按标题 md5 去重跳过）
 *
 * 数据规则（与既有库一致）：
 *   paper_id   = 'p_' + md5(标题) 前 20 位
 *   author_id  = 'a_' + md5(作者名) 前 20 位
 *   venue_id   = 'v_' + md5(来源名) 前 20 位
 *   topic_id   = 't_' + md5(关键词) 前 20 位
 *   institution_id = 'i_' + md5(机构名) 前 20 位
 *   authorship_id  = 'auth_{序}_{paper_id}'
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const file = process.argv[2]
const MERGE = process.argv.includes('--merge')
if (!file) {
  console.error('用法: node scripts/import-cnki.mjs <CNKI导出的.xls文件> [--merge]')
  process.exit(1)
}

const md5 = (s) => createHash('md5').update(s, 'utf8').digest('hex').slice(0, 20)

// ---------- 1. 解析 HTML 表格 ----------
const html = readFileSync(file, 'utf8')
const rowRe = /<tr[^>]*>([\s\S]*?)<\/tr>/g
const cellRe = /<t[dh][^>]*>([\s\S]*?)<\/t[dh]>/g

function decode(s) {
  return s
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&nbsp;/g, ' ')
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)))
    .replace(/&#x([0-9a-fA-F]+);/g, (_, n) => String.fromCodePoint(parseInt(n, 16)))
}

const rows = []
let m
while ((m = rowRe.exec(html)) !== null) {
  const cells = []
  let c
  while ((c = cellRe.exec(m[1])) !== null) {
    cells.push(decode(c[1]).trim())
  }
  if (cells.length >= 10) rows.push(cells)
}
if (rows.length < 2) {
  console.error('未解析到数据行，请确认是 CNKI 导出的 .xls（HTML 表格）文件')
  process.exit(1)
}
// 列: 0来源库 1题名 2作者 3单位 4文献来源 5关键词 6摘要 7发表时间 8第一责任人 9基金 10年 ...
const header = rows[0].map((h) => h.replace(/\s+/g, ''))
const col = (name) => header.findIndex((h) => h.startsWith(name))
const C = {
  db: col('SrcDatabase'),
  title: col('Title'),
  author: col('Author'),
  organ: col('Organ'),
  source: col('Source'),
  keyword: col('Keyword'),
  summary: col('Summary'),
  pubTime: col('PubTime'),
  year: col('Year'),
  doi: col('DOI'),
}

// ---------- 2. 构建实体 ----------
const papers = new Map()
const authors = new Map()
const venues = new Map()
const topics = new Map()
const institutions = new Map()
const authorships = []
const paperTopics = []
let skippedDup = 0

const splitList = (s) =>
  (s || '')
    .split(/[;；]/)
    .map((x) => x.trim())
    .filter(Boolean)

for (const r of rows.slice(1)) {
  const title = r[C.title]
  if (!title || title.startsWith('Title-')) continue
  const paperId = 'p_' + md5(title)
  if (papers.has(paperId)) {
    skippedDup++
    continue
  }

  const srcDb = r[C.db] || '其他'
  const source = (r[C.source] || '').trim()
  const venueId = source ? 'v_' + md5(source) : null
  if (source && !venues.has(venueId)) venues.set(venueId, { id: venueId, name: source, type: srcDb })

  const yearSrc = (r[C.year] || '').trim() || (r[C.pubTime] || '').trim()
  const year = parseInt(yearSrc.slice(0, 4), 10)
  const pubTime = (r[C.pubTime] || '').trim()
  const pubDate = /^\d{4}-\d{2}-\d{2}$/.test(pubTime) ? pubTime : null

  papers.set(paperId, {
    id: paperId,
    title,
    year: Number.isFinite(year) ? year : null,
    pubDate,
    abstract: (r[C.summary] || '').trim() || null,
    paperType: srcDb,
    venueId,
    doi: (r[C.doi] || '').trim() || null,
  })

  // 作者 + 署名
  const authorNames = splitList(r[C.author])
  const seen = new Set()
  let order = 0
  for (const name of authorNames) {
    const aid = 'a_' + md5(name)
    if (!authors.has(aid)) authors.set(aid, { id: aid, name })
    if (seen.has(aid)) continue
    seen.add(aid)
    order++
    authorships.push({
      id: `auth_${order}_${paperId}`,
      paperId,
      authorId: aid,
      order,
      rawAffiliation: (r[C.organ] || '').trim() || null,
      source: srcDb,
    })
  }

  // 机构（单位字段拆分）
  for (const inst of splitList(r[C.organ])) {
    const iid = 'i_' + md5(inst)
    if (!institutions.has(iid)) institutions.set(iid, { id: iid, name: inst })
  }

  // 关键词 → 主题
  for (const kw of splitList(r[C.keyword])) {
    const tid = 't_' + md5(kw)
    if (!topics.has(tid)) topics.set(tid, { id: tid, name: kw })
    paperTopics.push({ paperId, topicId: tid })
  }
}

// 去重 paper_topic
const ptSeen = new Set()
const ptDeduped = paperTopics.filter((pt) => {
  const key = pt.paperId + '|' + pt.topicId
  if (ptSeen.has(key)) return false
  ptSeen.add(key)
  return true
})

// ---------- 3. 合并模式：查询库中已有论文，跳过重复 ----------
const mysqlPwd = process.env.MYSQL_PWD || 'l123578964'
const mysqlBase = ['-u', 'root', `-p${mysqlPwd}`, '--default-character-set=utf8mb4', 'aacv']

let skippedExisting = 0
if (MERGE) {
  let out = ''
  try {
    out = execFileSync('mysql', [...mysqlBase, '-N', '-B', '-e', 'SELECT paper_id FROM paper;'], {
      encoding: 'utf8',
    })
  } catch {
    console.error('查询已有论文失败，取消合并导入')
    process.exit(1)
  }
  const existing = new Set(out.split(/\r?\n/).map((s) => s.trim()).filter(Boolean))
  for (const pid of existing) {
    if (papers.delete(pid)) skippedExisting++
  }
  for (let i = authorships.length - 1; i >= 0; i--) {
    if (existing.has(authorships[i].paperId)) authorships.splice(i, 1)
  }
  for (let i = ptDeduped.length - 1; i >= 0; i--) {
    if (existing.has(ptDeduped[i].paperId)) ptDeduped.splice(i, 1)
  }
}

// ---------- 4. 生成 SQL ----------
const esc = (s) =>
  s === null || s === undefined
    ? 'NULL'
    : "'" + String(s).replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/\r?\n/g, '\\n') + "'"

const insert = (table, columns, values) =>
  `${MERGE ? 'INSERT IGNORE INTO' : 'INSERT INTO'} ${table} (${columns}) VALUES (${values});`

const lines = ['SET NAMES utf8mb4;', 'SET FOREIGN_KEY_CHECKS = 0;']
if (!MERGE) {
  lines.push(
    'TRUNCATE TABLE paper_reference;',
    'TRUNCATE TABLE paper_topic;',
    'TRUNCATE TABLE authorship;',
    'TRUNCATE TABLE paper;',
    'TRUNCATE TABLE author;',
    'TRUNCATE TABLE venue;',
    'TRUNCATE TABLE topic;',
    'TRUNCATE TABLE institution;'
  )
}
lines.push('SET FOREIGN_KEY_CHECKS = 1;')

for (const v of venues.values())
  lines.push(
    insert('venue', 'venue_id, created_at, updated_at, name, normalized_name, publisher, type',
      `'${v.id}', NOW(), NOW(), ${esc(v.name)}, ${esc(v.name)}, NULL, ${esc(v.type)}`)
  )
for (const a of authors.values())
  lines.push(
    insert('author', 'author_id, created_at, updated_at, homepage, name, normalized_name, orcid',
      `'${a.id}', NOW(), NOW(), NULL, ${esc(a.name)}, ${esc(a.name)}, NULL`)
  )
for (const i of institutions.values())
  lines.push(
    insert('institution', 'institution_id, created_at, updated_at, country, homepage, name, normalized_name',
      `'${i.id}', NOW(), NOW(), NULL, NULL, ${esc(i.name)}, ${esc(i.name)}`)
  )
for (const t of topics.values())
  lines.push(
    insert('topic', 'topic_id, created_at, updated_at, description, name, normalized_name',
      `'${t.id}', NOW(), NOW(), NULL, ${esc(t.name)}, ${esc(t.name)}`)
  )
for (const p of papers.values())
  lines.push(
    insert('paper', 'paper_id, created_at, updated_at, abstract, doi, paper_type, publication_date, title, venue_id, year',
      `'${p.id}', NOW(), NOW(), ${esc(p.abstract)}, ${esc(p.doi)}, ${esc(p.paperType)}, ${p.pubDate ? `'${p.pubDate}'` : 'NULL'}, ${esc(p.title)}, ${p.venueId ? `'${p.venueId}'` : 'NULL'}, ${p.year ?? 'NULL'}`)
  )
for (const a of authorships)
  lines.push(
    insert('authorship', 'authorship_id, created_at, updated_at, author_id, author_order, is_corresponding, institution_id, paper_id, raw_affiliation, source',
      `'${a.id}', NOW(), NOW(), '${a.authorId}', ${a.order}, b'0', NULL, '${a.paperId}', ${esc(a.rawAffiliation)}, ${esc(a.source)}`)
  )
for (const pt of ptDeduped)
  lines.push(
    insert('paper_topic', 'created_at, updated_at, version, paper_id, topic_id',
      `NOW(), NOW(), 0, '${pt.paperId}', '${pt.topicId}'`)
  )

// ---------- 5. 执行 ----------
const sqlText = lines.join('\n')

console.log(`解析: 论文 ${papers.size}（文件内去重 ${skippedDup}，库中已存在跳过 ${skippedExisting}）、作者 ${authors.size}、机构 ${institutions.size}、期刊 ${venues.size}、主题 ${topics.size}、署名 ${authorships.length}、主题关联 ${ptDeduped.length}`)
execFileSync('mysql', mysqlBase, { input: sqlText })

console.log(MERGE ? '合并导入完成（原有数据已保留）' : '覆盖导入完成')
