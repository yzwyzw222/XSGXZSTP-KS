import assert from 'node:assert/strict'

/** 空哈希仅表示文件确实不存在，删除也必须与来源及适配记录一致。 */
export function verifyAdaptations(sourceFiles, currentFiles, changes, label) {
  const approved = new Map(changes.map(change => [change.path, change]))
  assert.equal(approved.size, changes.length, `${label} 存在重复适配记录`)
  for (const file of new Set([...sourceFiles.keys(), ...currentFiles.keys()])) {
    const source = sourceFiles.get(file) ?? null
    const current = currentFiles.get(file) ?? null
    const change = approved.get(file)
    if (change) {
      assert.equal(change.sourceBlob, source, `${label}/${file} 来源发生变化`)
      assert.equal(current, change.adaptedBlob, `${label}/${file} 适配内容未经记录`)
      approved.delete(file)
    } else assert.equal(current, source, `${label}/${file} 出现未经记录的适配`)
  }
  assert.equal(approved.size, 0, `${label} 存在过期适配记录`)
}
