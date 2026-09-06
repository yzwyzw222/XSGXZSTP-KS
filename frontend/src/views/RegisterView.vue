<template>
  <div class="register-page">
    <el-card class="register-card">
      <h1 class="title">注册账号</h1>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="显示名称" prop="displayName">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirm">
          <el-input v-model="form.confirm" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-alert v-if="error" :title="error" type="error" :closable="false" class="alert" />
        <el-button type="primary" class="submit" native-type="submit" :loading="loading">
          注 册
        </el-button>
        <p class="hint">
          已有账号？
          <router-link to="/login">返回登录</router-link>
        </p>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { get, post } from '../api/http'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const error = ref('')

const form = reactive({ username: '', displayName: '', password: '', confirm: '' })
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名长度 3-32 个字符', trigger: 'blur' }
  ],
  displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度 6-64 个字符', trigger: 'blur' }
  ],
  confirm: [
    {
      validator: (_rule, value: string, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function onSubmit() {
  error.value = ''
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await get('/api/v1/auth/csrf')
    await post('/api/v1/auth/register', {
      username: form.username,
      displayName: form.displayName,
      password: form.password
    })
    router.push({ name: 'login', query: { registered: '1' } })
  } catch (err) {
    error.value = err instanceof Error ? err.message : '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--background);
}

.register-card {
  width: 380px;
  background: var(--paper);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
}

.title {
  font-size: 20px;
  text-align: center;
  color: var(--ink);
  margin: 0 0 var(--space-6);
}

.alert {
  margin-bottom: var(--space-3);
}

.submit {
  width: 100%;
}

.hint {
  text-align: center;
  color: var(--muted);
  margin-top: var(--space-4);
}
</style>
