export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

export function formatNumber(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : value.toLocaleString('zh-CN')
}

export function splitValues(value: string): string[] {
  return value.split(/[,，\n]/).map((item) => item.trim()).filter(Boolean)
}
