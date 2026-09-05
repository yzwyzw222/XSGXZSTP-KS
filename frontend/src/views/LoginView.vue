<script setup lang="ts">
import { History, Lock, Percent, ShieldCheck, UserRound } from 'lucide-vue-next'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError, toErrorMessage } from '@/services/api'
import { login } from '@/services/session'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const form = reactive({ username: '', password: '' })
const brandLogo = '/favicon.svg'

const features = [
  { icon: History, label: '来源留痕' },
  { icon: Percent, label: '冲突治理' },
  { icon: ShieldCheck, label: '质量审核' },
]

const hexClip = 'polygon(50% 0, 100% 25%, 100% 75%, 50% 100%, 0 75%, 0 25%)'

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
    if (error instanceof ApiError && error.status === 401) {
      errorMessage.value = '账号或者密码错误'
    } else {
      errorMessage.value = toErrorMessage(error)
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="relative grid min-h-screen place-items-center overflow-hidden bg-[#edf0f4] px-4 py-10 sm:px-8">
    <!-- 背景装饰：网络连线、分子结构、虚化六边形与光斑 -->
    <div class="pointer-events-none absolute inset-0" aria-hidden="true">
      <svg class="absolute inset-0 size-full" viewBox="0 0 1440 900" fill="none" preserveAspectRatio="xMidYMid slice">
        <g stroke="#94a3b8" stroke-width="1" opacity="0.4">
          <path d="M40 500 L140 452 L232 540 L128 626 Z" />
          <path d="M140 452 L206 382" />
          <path d="M1120 40 L1235 118 L1352 62" />
          <path d="M1235 118 L1292 212" />
          <path d="M1180 300 L1272 356 L1226 428 L1318 470" />
          <path d="M150 762 L286 706 L404 782" />
          <path d="M960 806 L1082 748 L1204 818" />
          <path d="M60 120 L170 80 L252 148" />
          <path d="M180 655 v-30 l26 -15 l26 15 v30 l-26 15 Z" />
          <path d="M206 610 v-24 M232 625 l20 -12 M232 670 l20 12 M180 670 l-20 12" />
          <path d="M1240 330 v-24 l20 -12 l20 12 v24 l-20 12 Z" />
          <path d="M1260 294 v-18 M1280 306 l16 -10 M1280 342 l16 10" />
        </g>
        <g fill="#94a3b8" opacity="0.55">
          <circle cx="40" cy="500" r="3.5" />
          <circle cx="232" cy="540" r="3" />
          <circle cx="128" cy="626" r="3" />
          <circle cx="206" cy="382" r="3" />
          <circle cx="1120" cy="40" r="3.5" />
          <circle cx="1352" cy="62" r="3" />
          <circle cx="1292" cy="212" r="3.5" />
          <circle cx="1180" cy="300" r="3" />
          <circle cx="1318" cy="470" r="3" />
          <circle cx="150" cy="762" r="3" />
          <circle cx="404" cy="782" r="3.5" />
          <circle cx="960" cy="806" r="3" />
          <circle cx="1204" cy="818" r="3" />
          <circle cx="60" cy="120" r="3" />
          <circle cx="252" cy="148" r="3.5" />
          <circle cx="206" cy="586" r="2.5" />
          <circle cx="252" cy="613" r="2.5" />
          <circle cx="1260" cy="276" r="2.5" />
        </g>
        <g fill="#c9a86a" opacity="0.85">
          <circle cx="140" cy="452" r="4.5" />
          <circle cx="1235" cy="118" r="4.5" />
          <circle cx="1272" cy="356" r="4" />
          <circle cx="286" cy="706" r="4.5" />
          <circle cx="1082" cy="748" r="4" />
          <circle cx="170" cy="80" r="4" />
        </g>
      </svg>
      <div class="absolute -left-20 -top-24 size-80 rounded-full border-[30px] border-slate-400/30 blur-xl" />
      <div class="absolute left-[7%] top-[15%] size-28 rounded-full bg-amber-300/50 blur-2xl" />
      <div class="absolute right-[7%] top-[5%] size-52 rotate-6 bg-slate-500/40 blur-2xl" :style="{ clipPath: hexClip }" />
      <div class="absolute left-[4%] top-[62%] size-36 bg-slate-500/35 blur-xl" :style="{ clipPath: hexClip }" />
      <div class="absolute -bottom-[8%] right-[9%] size-60 bg-slate-500/35 blur-2xl" :style="{ clipPath: hexClip }" />
    </div>

    <!-- 中央玻璃卡片 -->
    <div
      class="relative grid w-full max-w-6xl gap-6 rounded-[2rem] border border-white/60 bg-white/40 p-5 shadow-[0_30px_70px_-24px_rgba(15,23,42,0.35)] backdrop-blur-xl sm:p-6 lg:grid-cols-[1.35fr_1fr] lg:gap-10 lg:p-7"
    >
      <!-- 左：品牌叙事 -->
      <section class="flex flex-col justify-center px-3 py-8 lg:px-10 lg:py-14">
        <img :src="brandLogo" alt="AACV System" class="size-16 rounded-2xl shadow-lg" />
        <span class="mt-7 text-sm text-slate-500">受控访问</span>
        <h1 class="mt-3 text-4xl font-bold leading-[1.25] tracking-tight text-slate-900 lg:text-[2.6rem]">
          把学术数据整理为<br />可核验的知识资产
        </h1>
        <p class="mt-5 max-w-md text-[15px] leading-relaxed text-slate-600">
          统一采集、治理与追溯成果记录，让每一次修订都有来源、每一项结论都可复核。
        </p>

        <ol class="mt-10 flex flex-col">
          <li v-for="(feature, index) in features" :key="feature.label" class="flex flex-col">
            <div v-if="index > 0" class="ml-[17px] h-6 w-px bg-slate-300" aria-hidden="true" />
            <div class="flex items-center gap-4">
              <span
                class="grid size-9 shrink-0 place-items-center rounded-full border border-slate-400/80 text-[11px] text-slate-600"
              >0{{ index + 1 }}</span>
              <span class="grid size-10 shrink-0 place-items-center rounded-full bg-white text-[#1273e6] shadow-[0_6px_16px_-6px_rgba(15,23,42,0.3)]">
                <component :is="feature.icon" class="size-5" aria-hidden="true" />
              </span>
              <span class="text-[15px] text-slate-800">{{ feature.label }}</span>
            </div>
          </li>
        </ol>
      </section>

      <!-- 右：登录面板 -->
      <section
        class="flex flex-col justify-center rounded-[1.75rem] border border-white/70 bg-white/55 px-6 py-10 shadow-[0_20px_45px_-18px_rgba(15,23,42,0.28)] backdrop-blur-xl sm:px-9 lg:px-10 lg:py-12"
      >
        <h2 class="text-3xl font-semibold tracking-tight text-slate-900">登录 AACV System</h2>
        <p class="mt-3 text-sm text-slate-600">使用管理员分配的内部账号继续</p>

        <div class="mt-6 space-y-3">
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

        <form class="mt-7 space-y-5" novalidate @submit.prevent="submit">
          <div class="grid gap-2">
            <Label for="username" class="flex items-center gap-1.5 text-sm font-normal text-slate-700">
              <UserRound class="size-3.5 text-slate-500" aria-hidden="true" />用户名
            </Label>
            <div class="relative">
              <UserRound
                class="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400"
                aria-hidden="true"
              />
              <Input
                id="username"
                v-model="form.username"
                autocomplete="username"
                :maxlength="64"
                placeholder="请输入用户名"
                class="h-11 rounded-lg border-slate-200/90 bg-white/90 pl-9 text-slate-900 shadow-none placeholder:text-slate-400 dark:border-slate-200/90 dark:bg-white/90 dark:text-slate-900 dark:placeholder:text-slate-400"
              />
            </div>
          </div>
          <div class="grid gap-2">
            <Label for="password" class="flex items-center gap-1.5 text-sm font-normal text-slate-700">
              <Lock class="size-3.5 text-slate-500" aria-hidden="true" />密码
            </Label>
            <div class="relative">
              <Lock
                class="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400"
                aria-hidden="true"
              />
              <Input
                id="password"
                v-model="form.password"
                type="password"
                autocomplete="current-password"
                :maxlength="128"
                placeholder="请输入密码"
                class="h-11 rounded-lg border-slate-200/90 bg-white/90 pl-9 text-slate-900 shadow-none placeholder:text-slate-400 dark:border-slate-200/90 dark:bg-white/90 dark:text-slate-900 dark:placeholder:text-slate-400"
                @keydown.enter="submit"
              />
            </div>
          </div>
          <Button
            type="button"
            class="mt-1 h-11 w-full rounded-full bg-[#1273e6] text-[15px] font-medium text-white shadow-[0_12px_26px_-12px_rgba(18,115,230,0.8)] hover:bg-[#0f63cc] dark:bg-[#1273e6] dark:text-white dark:hover:bg-[#0f63cc]"
            size="lg"
            :loading="loading"
            @click="submit"
          >
            进入工作台
          </Button>
        </form>
      </section>
    </div>
  </main>
</template>
