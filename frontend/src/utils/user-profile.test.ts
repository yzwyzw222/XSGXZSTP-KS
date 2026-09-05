import { describe, expect, it } from 'vitest'
import { profileForm, validateProfile } from './user-profile'
import { browserLabel } from './audit'

describe('用户资料校验', () => {
  it('兼容历史空资料，新建页面要求姓名', () => {
    expect(validateProfile(profileForm())).toBe('')
    expect(validateProfile(profileForm(), true)).toBe('请输入姓名')
  })
  it('验证邮箱、国际电话、长度与控制字符', () => {
    const form = profileForm({ realName: '姓名', email: 'person@example.invalid', phone: '+86 (010) 1234-5678' })
    expect(validateProfile(form, true)).toBe('')
    expect(validateProfile({ ...form, email: 'invalid' })).toContain('邮箱')
    expect(validateProfile({ ...form, phone: '()' })).toContain('联系电话')
    expect(validateProfile({ ...form, realName: '长'.repeat(65) })).toContain('64')
    expect(validateProfile({ ...form, department: '院\n系' })).toContain('无效字符')
    expect(validateProfile({ ...form, remark: '第一行\n第二行' })).toBe('')
  })
  it('浏览器识别优先匹配 Edge 并保留未知来源', () => {
    expect(browserLabel('Chrome/130 Safari/537 Edg/130')).toBe('Microsoft Edge')
    expect(browserLabel(null)).toBe('--')
    expect(browserLabel('unrecognized-client')).toBe('其他客户端')
  })
})
