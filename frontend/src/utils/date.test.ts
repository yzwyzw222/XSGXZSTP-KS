import { expect, it } from 'vitest'
import { instantRange, publicationDate, toLocalDateTime, toUtcInstant } from './date'

it('出版日期保留日历日，拒绝不存在的日期', () => {
  expect(publicationDate('2024-02-29')).toBe('2024-02-29')
  expect(publicationDate(null)).toBeNull()
  expect(() => publicationDate('2026-02-29')).toThrow('有效的出版日期')
})

it('UTC 与本地编辑往返保持同一时刻，空值和非法区间明确处理', () => {
  expect(toUtcInstant(toLocalDateTime('2026-09-06T08:23:14Z'))).toBe('2026-09-06T08:23:14.000Z')
  expect(instantRange('', null)).toEqual({ from: undefined, to: undefined })
  expect(() => instantRange('invalid', null)).toThrow('有效的日期时间')
  expect(() => instantRange('2026-09-06T08:00:00Z', '2026-09-06T08:00:00Z')).toThrow('开始时间必须早于结束时间')
})
