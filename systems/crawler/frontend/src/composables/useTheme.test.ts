import { afterEach, describe, expect, it, vi } from 'vitest'
import { initTheme, useTheme } from './useTheme'

afterEach(() => vi.restoreAllMocks())
describe('固定科研主题', () => {
  it.each(['light', 'auto', 'dark', 'invalid'])('旧偏好 %s 在初始化时归一到深色', value => {
    localStorage.setItem('aacv-theme', value)
    initTheme()
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(localStorage.getItem('aacv-theme')).toBe('dark')
    expect(useTheme().isDark.value).toBe(true)
  })
  it('存储权限被禁用也能显示深色', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new DOMException('Storage unavailable', 'SecurityError') })
    expect(initTheme).not.toThrow()
    expect(document.documentElement.style.colorScheme).toBe('dark')
  })
})
