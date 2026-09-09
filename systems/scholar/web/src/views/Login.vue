<template>
  <div class="login-page">
    <div class="login-card">
      <h1 class="login-title">AACV 系统</h1>
      <p class="login-subtitle">请登录以继续</p>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        @submit.prevent="handleLogin"
        label-width="0"
        size="large"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            :prefix-icon="User"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            autocomplete="current-password"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            native-type="submit"
            :loading="loading"
            class="login-btn"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
        <div v-if="errorMsg" class="login-error">
          {{ errorMsg }}
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '../api/auth'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const errorMsg = ref('')

const form = reactive({
  username: '',
  password: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  errorMsg.value = ''

  try {
    await login(form.username, form.password)
    router.push('/dashboard')
  } catch (e) {
    errorMsg.value = '用户名或密码错误，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--background);
}

.login-card {
  width: 400px;
  padding: 40px;
  background-color: var(--paper);
  border: 1px solid #1e3a5e;
  border-radius: 8px;
}

.login-title {
  text-align: center;
  font-size: 28px;
  font-weight: 700;
  color: var(--accent);
  margin-bottom: 8px;
}

.login-subtitle {
  text-align: center;
  color: var(--muted);
  margin-bottom: 32px;
  font-size: 14px;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
}

.login-error {
  text-align: center;
  color: var(--danger);
  font-size: 13px;
  margin-top: -8px;
}
</style>