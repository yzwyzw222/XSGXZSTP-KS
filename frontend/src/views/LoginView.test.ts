import { createMemoryHistory, createRouter } from 'vue-router'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import LoginView from '@/views/LoginView.vue'

describe('LoginView', () => {
  it('空凭据提交时显示明确校验错误', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/login', component: LoginView }],
    })
    await router.push('/login')
    await router.isReady()
    const wrapper = mount(LoginView, { global: { plugins: [router] } })

    const button = wrapper.findAll('button').find((item) => item.text().includes('进入工作台'))
    await button?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请输入用户名和密码')
  })
})
