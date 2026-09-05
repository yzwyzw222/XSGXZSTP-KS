import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import AppSidebar from '@/components/business/AppSidebar.vue'
import { navItems } from '@/config/nav'

async function render(items = navItems, collapsed = false) {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }] })
  await router.push('/catalog/achievements/42')
  await router.isReady()
  return mount(AppSidebar, { props: { items, collapsed }, global: { plugins: [router] } })
}

describe('四组侧栏导航', () => {
  it('完整菜单按业务职责分组，详情仍高亮成果目录', async () => {
    const wrapper = await render()
    expect(wrapper.findAll('nav section').map((section) => section.attributes('aria-label')))
      .toEqual(['可视化', '爬虫管理', '系统状态', '用户管理'])
    expect(wrapper.get('a[aria-current="page"]').attributes('href')).toBe('/catalog')
    expect(wrapper.findAll('nav a')).toHaveLength(11)
    expect(wrapper.get('section[aria-label="系统状态"]').text()).toContain('日志管理')
    expect(wrapper.get('section[aria-label="用户管理"]').text()).toContain('账号管理')
    wrapper.unmount()
  })

  it('权限过滤后隐藏空组，折叠时链接仍有可访问名称', async () => {
    const items = navItems.filter((item) => !item.permission || ['CATALOG_READ', 'GRAPH_READ', 'ANALYTICS_READ'].includes(item.permission))
    const wrapper = await render(items, true)
    expect(wrapper.findAll('nav section')).toHaveLength(1)
    expect(wrapper.get('nav section').attributes('aria-label')).toBe('可视化')
    expect(wrapper.get('a[href="/graph"]').attributes('aria-label')).toBe('知识图谱')
    expect(wrapper.find('a[href="/users"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
