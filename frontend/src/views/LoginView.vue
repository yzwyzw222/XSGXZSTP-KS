<script setup lang="ts">
import { Lock, ShieldCheck, UserRound } from 'lucide-vue-next'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
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
  <main class="grid min-h-screen grid-cols-1 bg-background lg:grid-cols-[minmax(0,1.1fr)_minmax(440px,0.9fr)]">
    <!-- 品牌叙事（移动端隐藏） -->
    <section
      class="relative hidden overflow-hidden bg-sidebar p-12 lg:flex lg:flex-col lg:justify-center"
      aria-hidden="false"
    >
      <div
        class="pointer-events-none absolute -left-40 -top-40 size-[560px] rounded-full border border-primary/10"
        aria-hidden="true"
      />
      <div
        class="pointer-events-none absolute -bottom-52 -right-40 size-[560px] rounded-full border border-primary/10"
        aria-hidden="true"
      />
      <div class="relative z-10 max-w-xl">
        <h1 class="mt-6 text-5xl font-semibold leading-[1.05] tracking-tight text-foreground">
          把学术数据整理为<br />可核验的知识资产
        </h1>
        <p class="mt-5 max-w-lg text-base leading-relaxed text-muted-foreground">
          统一采集、治理与追溯成果记录，让每一次修订都有来源、每一项结论都可复核。
        </p>
      </div>
      <div class="relative z-10 mt-14 flex gap-8 border-t border-border pt-6 text-xs text-muted-foreground">
        <span class="flex items-center gap-2"><ShieldCheck class="size-4 text-primary" />01 · 来源留痕</span>
        <span class="flex items-center gap-2"><ShieldCheck class="size-4 text-primary" />02 · 冲突治理</span>
        <span class="flex items-center gap-2"><ShieldCheck class="size-4 text-primary" />03 · 质量审阅</span>
      </div>
    </section>

    <!-- 登录卡片 -->
    <section class="grid place-items-center px-5 py-12 sm:px-10">
      <div class="w-full max-w-sm">
        <img :src="brandLogo" alt="AACV System" class="mb-8 size-12 rounded-xl" />
        <span class="eyebrow">受控访问</span>
        <h2 class="mt-2 text-3xl font-semibold tracking-tight text-foreground">登录 AACV System</h2>
        <p class="mt-2 mb-7 text-sm text-muted-foreground">使用管理员分配的内部账号继续。</p>

        <div class="space-y-3">
          <Alert v-if="route.query.reason === 'expired'" variant="warning">
            <AlertTitle>会话已过期，请重新登录</AlertTitle>
          </Alert>
          <Alert v-if="route.query.reason === 'unavailable'" variant="destructive">
            <AlertTitle>会话检查失败，请确认服务可用后重试</AlertTitle>
          </Alert>
          <Alert v-if="errorMessage" variant="destructive">
            <AlertTitle>{{ errorMessage }}</AlertTitle>
            <AlertDescription v-if="route.query.reason">请重新输入凭据后继续。</AlertDescription>
          </Alert>
        </div>

        <form class="mt-5 space-y-4" novalidate @submit.prevent="submit">
          <div class="grid gap-1.5">
            <Label for="username" class="flex items-center gap-1.5">
              <UserRound class="size-3.5 text-muted-foreground" aria-hidden="true" />用户名
            </Label>
            <Input
              id="username"
              v-model="form.username"
              autocomplete="username"
              :maxlength="64"
              placeholder="请输入用户名"
            />
          </div>
          <div class="grid gap-1.5">
            <Label for="password" class="flex items-center gap-1.5">
              <Lock class="size-3.5 text-muted-foreground" aria-hidden="true" />密码
            </Label>
            <Input
              id="password"
              v-model="form.password"
              type="password"
              autocomplete="current-password"
              :maxlength="128"
              placeholder="请输入密码"
              @keydown.enter="submit"
            />
          </div>
          <Button type="button" class="mt-2 w-full" size="lg" :loading="loading" @click="submit">
            进入工作台
          </Button>
        </form>
      </div>
    </section>
  </main>
</template>
