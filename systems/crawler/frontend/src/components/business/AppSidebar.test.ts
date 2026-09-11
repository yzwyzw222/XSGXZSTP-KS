import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import AppSidebar from '@/components/business/AppSidebar.vue'
import { navItems } from '@/config/nav'

async function render(items = navItems, collapsed = false, path = '/catalog/achievements/42') {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }] })
  await router.push(path)
  await router.isReady()
  return mount(AppSidebar, { props: { items, collapsed }, global: { plugins: [router] } })
}

describe('研究界面侧栏导航', () => {
  it('完整菜单按业务职责分组，详情仍高亮成果目录', async () => {
    const wrapper = await render()
    expect(wrapper.findAll('nav section').map((section) => section.attributes('aria-label')))
      .toEqual(['研究工作', '数据管理', '管理工具'])
    expect(wrapper.get('a[aria-current="page"]').attributes('href')).toBe('/catalog')
    expect(wrapper.findAll('nav section > ul > li')).toHaveLength(11)
    expect(wrapper.get('button[aria-label="成果目录"]').attributes('aria-expanded')).toBe('true')
    expect(wrapper.findAll('.graph-submenu-link').map(link => link.text()))
      .toEqual(['全部成果'])
    expect(wrapper.find('button[aria-label="知识图谱"]').exists()).toBe(false)
    expect(wrapper.get('section[aria-label="管理工具"]').text()).toContain('日志管理')
    expect(wrapper.get('section[aria-label="管理工具"]').text()).toContain('账号管理')
    wrapper.unmount()
  })

  it('三类图谱均为独立主入口，切换保留作者且无需展开父菜单', async () => {
    const wrapper = await render(navItems, false, '/academic-relations?authorId=42')
    expect(wrapper.find('button[aria-label="知识图谱"]').exists()).toBe(false)
    for (const mode of ['relations', 'achievements', 'background']) {
      expect(wrapper.get(`nav > section > ul > li > a[href="/academic-${mode}?authorId=42"]`).attributes('href')).toBe(`/academic-${mode}?authorId=42`)
    }
    expect(wrapper.get('a[aria-current="page"]').attributes('href')).toBe('/academic-relations?authorId=42')
    wrapper.unmount()
  })

  it('合并重复入口，全部展开后保留研究、作者导入与日志功能', async () => {
    const wrapper = await render(navItems, false, '/')
    for (const toggle of wrapper.findAll('.graph-menu-toggle')) await toggle.trigger('click')
    expect(wrapper.findAll('nav a')).toHaveLength(19)
    expect(wrapper.findAll('.graph-menu-toggle')).toHaveLength(3)
    expect(wrapper.findAll('.graph-submenu-link')).toHaveLength(11)
    expect(wrapper.find('a[href="/operations"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('运行监控')
    expect(wrapper.get('a[href="/logs"]').text()).toBe('日志管理')
    wrapper.unmount()
  })

  it.each([
    ['/catalog/organizations', '/catalog/organizations'], ['/catalog/venues', '/catalog/venues'],
    ['/catalog/topics', '/catalog/topics'], ['/catalog/patents', '/catalog/patents'],
    ['/catalog/master-theses', '/catalog/master-theses'], ['/catalog/doctoral-theses', '/catalog/doctoral-theses'], ['/analytics/research', '/analytics/distributions'],
    ['/analytics/coverage', '/analytics'],
  ])('页内分类 %s 高亮合并后的入口 %s', async (path, target) => {
    const wrapper = await render(navItems, false, path)
    expect(wrapper.findAll('nav a[aria-current="page"]')).toHaveLength(1)
    expect(wrapper.get('nav a[aria-current="page"]').attributes('href')).toBe(target)
    wrapper.unmount()
  })

  it('实体子页使用最长匹配入口，返回成果详情时恢复全部成果高亮', async () => {
    const wrapper = await render(navItems, false, '/catalog/authors')
    expect(wrapper.findAll('nav a[aria-current="page"]')).toHaveLength(1)
    expect(wrapper.get('nav a[aria-current="page"]').attributes('href')).toBe('/catalog/authors')
    await wrapper.vm.$router.push('/catalog/achievements/42')
    expect(wrapper.findAll('nav a[aria-current="page"]')).toHaveLength(1)
    expect(wrapper.get('nav a[aria-current="page"]').attributes('href')).toBe('/catalog')
    wrapper.unmount()
  })

  it('权限过滤后隐藏空组，折叠时链接仍有可访问名称', async () => {
    const items = navItems.filter((item) => !item.permission || ['CATALOG_READ', 'GRAPH_READ', 'ANALYTICS_READ'].includes(item.permission))
    const wrapper = await render(items, true)
    expect(wrapper.findAll('nav section')).toHaveLength(1)
    expect(wrapper.get('nav section').attributes('aria-label')).toBe('研究工作')
    expect(wrapper.get('a[href="/academic-relations"]').attributes('aria-label')).toBe('学术关系图谱')
    expect(wrapper.find('a[href="/users"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
