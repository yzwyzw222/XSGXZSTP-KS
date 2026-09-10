import assert from 'node:assert/strict'
import test from 'node:test'
import { loadConfig, publicConfig } from '../lib/config.mjs'
import { getSystems } from '../../portal/src/services/systems.js'

test('门户读取两个有效系统，拒绝缺失、重复及已删除系统的入口配置', async t => {
  const active = publicConfig(loadConfig()).systems
  let systems = active
  t.mock.method(globalThis, 'fetch', async () => ({ ok: true, json: async () => ({ systems }) }))
  assert.deepEqual(await getSystems(), active)
  for (const invalid of [null, [], active.slice(0, 1), [active[0], active[0]],
    ...['extraction', 'scholar'].map(id => [active[0], { ...active[1], id, path: `/${id}/` }]),
    [...active, { ...active[0], id: 'extraction', path: '/extraction/' }]]) {
    systems = invalid
    await assert.rejects(getSystems(), /接入状态配置有误/)
  }
})
