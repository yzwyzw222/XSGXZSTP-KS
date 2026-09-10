import { ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useMotion } from './useMotion'

const reducedMotion = ref(false)
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => reducedMotion }))
const kinds = ['fast', 'normal', 'slow', 'chart'] as const

beforeEach(() => { reducedMotion.value = false })
afterEach(() => {
  for (const kind of kinds) document.documentElement.style.removeProperty(`--duration-${kind}`)
  vi.unstubAllGlobals()
})

describe('画布动画时长统一为毫秒', () => {
  it.each([
    ['280ms', 280], ['.28s', 280], ['0.28s', 280], ['1.5s', 1500],
    ['  .12s  ', 120], ['120MS', 120], ['0ms', 0], ['0s', 0], ['0', 0],
  ])('CSS 时间 %s 转换为 %s 毫秒', (value, expected) => {
    document.documentElement.style.setProperty('--duration-slow', value)
    expect(useMotion().duration('slow')).toBe(expected)
  })

  it.each(['', '-280ms', '-.28s', '120', '120px', '120ms extra', 'NaNms', 'Infinitys'])('非法时间 %s 使用既有默认值', value => {
    document.documentElement.style.setProperty('--duration-slow', value)
    expect(useMotion().duration('slow')).toBe(280)
  })

  it('超出有限数值范围的时间使用既有默认值', () => {
    document.documentElement.style.setProperty('--duration-slow', `${'9'.repeat(400)}s`)
    expect(useMotion().duration('slow')).toBe(280)
  })

  it('缺少配置时各类动效保留既有默认时长', () => {
    const { duration } = useMotion()
    expect(kinds.map(kind => duration(kind))).toEqual([120, 200, 280, 320])
    expect(duration()).toBe(200)
  })

  it('减少动画偏好生效时返回零，恢复后继续解析当前配置', () => {
    document.documentElement.style.setProperty('--duration-slow', '.28s')
    const { duration } = useMotion()
    reducedMotion.value = true
    expect(duration('slow')).toBe(0)
    reducedMotion.value = false
    expect(duration('slow')).toBe(280)
  })

  it('无 document 的环境使用默认时长', () => {
    const { duration } = useMotion()
    vi.stubGlobal('document', undefined)
    expect(duration('slow')).toBe(280)
    vi.unstubAllGlobals()
  })
})
