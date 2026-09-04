import { describe, expect, it } from 'vitest'

import { easeOutCubic } from '@/utils/motion'

describe('motion utilities', () => {
  it('将缓动输入限制在合法区间', () => {
    expect(easeOutCubic(-1)).toBe(0)
    expect(easeOutCubic(0)).toBe(0)
    expect(easeOutCubic(1)).toBe(1)
    expect(easeOutCubic(2)).toBe(1)
  })

  it('在中段提供明显的 ease-out 加速', () => {
    expect(easeOutCubic(0.5)).toBeCloseTo(0.875)
  })
})
