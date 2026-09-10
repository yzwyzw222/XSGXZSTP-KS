import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { loadConfig, rootDirectory } from './lib/config.mjs'

const git = (...args) => execFileSync('git', args, { cwd: rootDirectory, encoding: 'utf8' }).trim()
const records = JSON.parse(readFileSync(new URL('../docs/import-records.json', import.meta.url), 'utf8'))
const config = loadConfig()
const adaptations = JSON.parse(readFileSync(new URL('../docs/source-adaptations.json', import.meta.url), 'utf8'))
const activeIds = config.systems.map(system => system.id).sort()
assert.deepEqual(records.imports.filter(record => !record.retired).map(record => record.id).sort(), activeIds, '有效来源记录必须与当前子系统一致')
assert.deepEqual(Object.keys(adaptations).sort(), activeIds, '适配记录必须只覆盖当前子系统')
assert.equal(git('rev-parse', `${records.main}^{tree}`), records.mainTree)
git('merge-base', '--is-ancestor', records.main, 'HEAD')
for (const record of records.imports) {
  const sourceTree = git('rev-parse', `${record.source}^{tree}`)
  assert.equal(sourceTree, record.tree)
  if (record.method !== 'archive') {
    assert.equal(git('rev-parse', `${record.commit}:systems/${record.id}`), sourceTree)
    git('merge-base', '--is-ancestor', record.source, record.commit)
    git('merge-base', '--is-ancestor', record.source, 'HEAD')
  } else {
    assert.equal(record.id, 'scholar', '仅历史 Li 来源使用 archive 导入记录')
  }
  // 已删除系统保留不可变导入证据；有效系统仍逐文件核对来源与适配。
  if (record.retired) {
    assert.ok(!config.systems.some(system => system.id === record.id), `${record.id} 已移除但仍有运行配置`)
    continue
  }
  assert.equal(config.systems.find(system => system.id === record.id).sourceSha, record.source)
  const sourceFiles = new Map(git('ls-tree', '-r', record.source).split('\n').map(line => {
    const [metadata, file] = line.split('\t')
    return [file, metadata.split(' ')[2]]
  }))
  const approved = new Map(adaptations[record.id].map(change => [change.path, change]))
  const currentFiles = git('ls-files', '--cached', '--others', '--exclude-standard', '--', `systems/${record.id}`)
    .split('\n').filter(Boolean).map(file => file.slice(`systems/${record.id}/`.length))
  const files = [...new Set([...sourceFiles.keys(), ...currentFiles])]
  const hashes = execFileSync('git', ['hash-object', '--stdin-paths'], {
    cwd: rootDirectory, encoding: 'utf8', input: files.map(file => `systems/${record.id}/${file}`).join('\n') + '\n',
  }).trim().split('\n')
  for (let index = 0; index < files.length; index++) {
    const file = files[index]
    const change = approved.get(file)
    if (change) {
      assert.equal(change.sourceBlob, sourceFiles.get(file) ?? null, `${record.id}/${file} 来源发生变化`)
      assert.equal(hashes[index], change.adaptedBlob, `${record.id}/${file} 适配内容未经记录`)
      approved.delete(file)
    } else {
      assert.equal(hashes[index], sourceFiles.get(file), `${record.id}/${file} 出现未经记录的适配`)
    }
  }
  assert.equal(approved.size, 0, `${record.id} 存在过期适配记录`)
  process.stdout.write(`${record.id}: 原始来源树${record.method === 'archive' ? '（archive 导入）' : '及来源祖先'}、全部文件和已记录适配校验通过\n`)
}
