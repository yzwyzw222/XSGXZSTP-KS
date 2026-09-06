<template>
  <div class="login-page">
    <el-card class="login-card">
      <h1 class="title">学术关系知识图谱构建平台</h1>
      <p class="subtitle">从孤立文献到学术网络</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-alert v-if="error" :title="error" type="error" :closable="false" class="alert" />
        <el-button type="primary" class="submit" native-type="submit" :loading="loading">
          登 录
        </el-button>
        <p class="hint">
          还没有账号？
          <router-link to="/register">立即注册</router-link>
        </p>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { get, post } from '../api/http'
import type { SessionUser } from '../session'
import { setCurrentUser } from '../session'

const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance>()
const loading = ref(false)
const error = ref('')

const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function onSubmit() {
  error.value = ''
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await get('/api/v1/auth/csrf')
    await post('/api/v1/auth/login', { username: form.username, password: form.password })
    const me = await get<SessionUser>('/api/v1/auth/me')
    setCurrentUser(me)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.replace(redirect)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--background);
}

.login-card {
  width: 380px;
  background: var(--paper);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
}

.title {
  font-size: 20px;
  text-align: center;
  color: var(--ink);
  margin: 0 0 var(--space-2);
}

.subtitle {
  text-align: center;
  color: var(--muted);
  margin: 0 0 var(--space-6);
}

.alert {
  margin-bottom: var(--space-3);
}

.submit {
  width: 100%;
  margin-top: var(--space-2);
}

.hint {
  text-align: center;
  color: var(--muted);
  margin-top: var(--space-4);
}
</style>
