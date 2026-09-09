<template>
  <div class="login-page">
    <el-card class="login-card">
      <h1 class="title">学术关系知识图谱构建平台</h1>
      <p class="subtitle">从孤立文献到学术网络</p>
      <!-- 登录表单：Element Plus Form + 校验规则（必填），登录失败统一提示、不暴露账号是否存在 -->
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
import { setCurrentUser, setCsrfToken } from '../session'

const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance>()
const loading = ref(false)
const error = ref('')

const form = reactive({ username: '', password: '' })

// Element Plus 表单校验规则：提交前验证必填项（后端另有 Bean Validation 兜底）
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

/**
 * 登录三段式流程（与后端 Session+CSRF 契约一一对应）：
 *  1) GET /auth/csrf  —— 建立 SESSION Cookie，并获取 CSRF Token 存入内存；
 *  2) POST /auth/login —— 携带 Cookie 与 CSRF Header 提交凭据（服务端成功后旋转 Session ID）；
 *  3) GET /auth/me    —— 恢复当前用户/角色/权限并写入会话模块，路由守卫据此放行。
 */
async function onSubmit() {
  error.value = ''
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const csrfResp = await get<{ token: string }>('/api/v1/auth/csrf')
    setCsrfToken(csrfResp.token)
    await post('/api/v1/auth/login', { username: form.username, password: form.password })
    // 登录成功后会话已旋转，重新获取 CSRF Token 以匹配新 Session
    const afterLogin = await get<{ token: string }>('/api/v1/auth/csrf')
    setCsrfToken(afterLogin.token)
    const me = await get<SessionUser>('/api/v1/auth/me')
    setCurrentUser(me)
    // 登录前被守卫拦截的目标地址（redirect 参数）优先，否则进图谱首页
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
