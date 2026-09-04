export interface EntityLinkItem {
  id: string | number
  label: string
  to?: string
}

export interface LogEntry {
  id: string | number
  time?: string
  level?: 'info' | 'success' | 'warning' | 'error'
  message: string
}
