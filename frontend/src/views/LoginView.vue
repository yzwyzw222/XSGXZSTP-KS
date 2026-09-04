<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput } from 'element-plus'

import { toErrorMessage } from '@/services/api'
import { login } from '@/services/session'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const form = reactive({ username: '', password: '' })
const brandLogo = '/favicon.svg'

async function submit(): Promise<void> {
  errorMessage.value = ''
  if (!form.username.trim() || !form.password) {
    errorMessage.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  try {
    await login(form.username.trim(), form.password)
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect
      : '/'
    await router.replace(redirect)
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-intro">
      <span class="eyebrow">ACADEMIC ASSET &amp; CITATION VERIFICATION</span>
      <h1>把学术数据整理为<br />可核验的知识资产</h1>
      <p>统一采集、治理与追溯成果记录，让每一次修订都有来源、每一项结论都可复核。</p>
      <div class="login-proof">
        <span>01 · 来源留痕</span>
        <span>02 · 冲突治理</span>
        <span>03 · 质量审阅</span>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-card">
        <img class="login-brand-mark" :src="brandLogo" alt="AACV System" />
        <div>
          <span class="eyebrow">受控访问</span>
          <h2>登录 AACV System</h2>
          <p>使用管理员分配的内部账号继续。</p>
        </div>

        <ElAlert
          v-if="route.query.reason === 'expired'"
          title="会话已过期，请重新登录"
          type="warning"
          :closable="false"
          show-icon
        />
        <ElAlert
          v-if="route.query.reason === 'unavailable'"
          title="会话检查失败，请确认服务可用后重试"
          type="error"
          :closable="false"
          show-icon
        />
        <ElAlert
          v-if="errorMessage"
          :title="errorMessage"
          type="error"
          :closable="false"
          show-icon
        />

        <ElForm label-position="top" @submit.prevent="submit">
          <ElFormItem label="用户名">
            <ElInput v-model="form.username" autocomplete="username" maxlength="64" />
          </ElFormItem>
          <ElFormItem label="密码">
            <ElInput
              v-model="form.password"
              type="password"
              autocomplete="current-password"
              maxlength="128"
              show-password
              @keyup.enter="submit"
            />
          </ElFormItem>
          <ElButton class="login-submit" type="primary" :loading="loading" @click="submit">
            进入工作台
          </ElButton>
        </ElForm>
      </div>
    </section>
  </main>
</template>
