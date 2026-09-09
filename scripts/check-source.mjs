import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { loadConfig, rootDirectory } from './lib/config.mjs'

const git = (...args) => execFileSync('git', args, { cwd: rootDirectory, encoding: 'utf8' }).trim()
const records = JSON.parse(readFileSync(new URL('../docs/import-records.json', import.meta.url), 'utf8'))
const config = loadConfig()
assert.equal(git('rev-parse', `${records.main}^{tree}`), records.mainTree)
git('merge-base', '--is-ancestor', records.main, 'HEAD')
for (const record of records.imports) {
  const sourceTree = git('rev-parse', `${record.source}^{tree}`)
  assert.equal(sourceTree, record.tree)
  assert.equal(git('rev-parse', `${record.commit}:systems/${record.id}`), sourceTree)
  git('merge-base', '--is-ancestor', record.source, record.commit)
  git('merge-base', '--is-ancestor', record.source, 'HEAD')
  assert.equal(config.systems.find(system => system.id === record.id).sourceSha, record.source)
  assert.equal(git('rev-parse', `HEAD:systems/${record.id}`), sourceTree)
  assert.equal(git('diff', '--name-only', 'HEAD', '--', `systems/${record.id}`), '')
  process.stdout.write(`${record.id}: 原始导入树、当前子树及来源祖先检查通过\n`)
}
