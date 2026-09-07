/** 日历日期不作时区转换，按后端 LocalDate 契约原样发送。 */
export function publicationDate(value: string | null | undefined): string | null {
  if (!value) return null
  const date = new Date(`${value}T00:00:00Z`)
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || !Number.isFinite(date.getTime()) || date.toISOString().slice(0, 10) !== value) {
    throw new RangeError('请输入有效的出版日期')
  }
  return value
}

/** 日期时间控件输出本地时间；在请求边界显式转换为 UTC。 */
export function toUtcInstant(value: string | null | undefined): string | undefined {
  if (!value) return undefined
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) throw new RangeError('请输入有效的日期时间')
  return date.toISOString()
}

/** 编辑服务器返回的 UTC 时间前转换为本地显示，避免直接截取产生时区偏移。 */
export function toLocalDateTime(value: string | null | undefined): string {
  if (!value) return ''
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) throw new RangeError('服务器返回了无效日期时间')
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function instantRange(from: string | null | undefined, to: string | null | undefined) {
  const start = toUtcInstant(from)
  const end = toUtcInstant(to)
  if (start && end && start >= end) throw new RangeError('开始时间必须早于结束时间')
  return { from: start, to: end }
}
