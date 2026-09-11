import assert from 'node:assert/strict'
import { test } from 'node:test'
import { verifyAdaptations } from './source-adaptations.mjs'

test('删除必须明确记录，旧来源和当前内容都必须匹配', () => {
  const source = new Map([['old.ts', 'original']])
  assert.throws(() => verifyAdaptations(source, new Map(), [], 'crawler'), /未经记录/)
  const deletion = [{ path: 'old.ts', sourceBlob: 'original', adaptedBlob: null }]
  assert.doesNotThrow(() => verifyAdaptations(source, new Map(), deletion, 'crawler'))
  assert.throws(() => verifyAdaptations(source, new Map([['old.ts', 'unexpected']]), deletion, 'crawler'), /未经记录/)
  assert.throws(() => verifyAdaptations(new Map([['old.ts', 'changed']]), new Map(), deletion, 'crawler'), /来源发生变化/)
})

test('新增和修改仍要求内容匹配，重复和过期记录被拒绝', () => {
  const change = { path: 'new.ts', sourceBlob: null, adaptedBlob: 'new' }
  assert.doesNotThrow(() => verifyAdaptations(new Map(), new Map([['new.ts', 'new']]), [change], 'crawler'))
  assert.throws(() => verifyAdaptations(new Map(), new Map([['new.ts', 'tampered']]), [change], 'crawler'), /未经记录/)
  assert.throws(() => verifyAdaptations(new Map(), new Map(), [change], 'crawler'), /过期/)
  assert.throws(() => verifyAdaptations(new Map(), new Map(), [change, change], 'crawler'), /重复/)
})
