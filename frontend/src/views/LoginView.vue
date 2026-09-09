<template>
  <div class="login-view">
    <div class="login-view__card">
      <h1 class="login-view__title">ACV 学术知识图谱</h1>
      <p class="login-view__subtitle">学术多元实体抽取与知识图谱构建系统</p>
      <el-form :model="form" @submit.prevent="handleLogin" class="login-view__form">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">
            登 录
          </el-button>
        </el-form-item>
        <p v-if="error" class="login-view__error">{{ error }}</p>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth.js'

const router = useRouter()
const { login } = useAuth()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')

async function handleLogin() {
  error.value = ''
  loading.value = true
  try {
    await login(form.username, form.password)
    router.push('/')
  } catch (e) {
    error.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.login-view {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: var(--bg);

  &__card {
    width: 360px;
    background: var(--card-bg);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-lg);
    padding: var(--spacing-xl);
  }

  &__title {
    text-align: center;
    color: var(--accent);
    font-size: 1.5rem;
    margin: 0 0 var(--spacing-xs);
  }

  &__subtitle {
    text-align: center;
    color: var(--text-tertiary);
    font-size: 0.8rem;
    margin: 0 0 var(--spacing-lg);
  }

  &__error {
    color: var(--danger);
    font-size: 0.8rem;
    text-align: center;
    margin: 0;
  }
}
</style>
