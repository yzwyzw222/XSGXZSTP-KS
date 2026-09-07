<script setup lang="ts">
import { ElAlert, ElButton, ElInput } from 'element-plus'
import { ArrowRight, History, Lock, Network, Percent, ShieldCheck, UserRound } from 'lucide-vue-next'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError, toErrorMessage } from '@/services/api'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const sessionStore = useSessionStore()

const loading = ref(false)
const errorMessage = ref('')
const form = reactive({ username: '', password: '' })
const brandLogo = '/favicon.svg'

const features = [
  { icon: History, label: '来源留痕', note: '每条成果保留采集来源与观测时间' },
  { icon: Percent, label: '冲突治理', note: '确定性标识关联，人工决定可追溯' },
  { icon: ShieldCheck, label: '质量审核', note: '字段覆盖率与问题样本按运行定位' },
]

async function submit(): Promise<void> {
  errorMessage.value = ''
  if (!form.username.trim() || !form.password) {
    errorMessage.value = '请输入用户名和密码'
    return
  }
  if (loading.value) return

  loading.value = true
  try {
    await sessionStore.login(form.username.trim(), form.password)
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
  <main class="login-page">
    <svg class="login-page__constellation" viewBox="0 0 1600 1000" fill="none" preserveAspectRatio="xMidYMid slice" aria-hidden="true" focusable="false">
      <g class="login-page__connections">
        <path d="M-40 340 92 224 242 110 422 156 572 68M92 224 62 68M242 110 282-24M242 110 422 156 486 310M92 224 188 410" />
        <path d="m1120 916 174-72 168-78 158 66m-326 12 66 134m102-212 38-158m-38 158 138-68m-280 252 168-48" />
        <circle cx="242" cy="110" r="32" />
        <circle cx="1462" cy="766" r="36" />
      </g>
      <g class="login-page__nodes">
        <circle cx="92" cy="224" r="6" /><circle cx="62" cy="68" r="4" />
        <circle cx="242" cy="110" r="9" /><circle cx="422" cy="156" r="6" />
        <circle cx="572" cy="68" r="4" /><circle cx="188" cy="410" r="5" />
        <circle cx="1120" cy="916" r="4" /><circle cx="1294" cy="844" r="6" />
        <circle cx="1462" cy="766" r="9" /><circle cx="1360" cy="978" r="4" />
        <circle cx="1500" cy="608" r="5" /><circle cx="1528" cy="928" r="5" />
      </g>
      <g class="login-page__node-labels">
        <text x="260" y="88">学术成果</text><text x="48" y="254">作者</text><text x="436" y="144">机构</text>
        <text x="1480" y="748">主题</text><text x="1254" y="877">期刊</text>
      </g>
    </svg>

    <div class="login-page__content">
      <header class="login-page__masthead">
        <span class="flex items-center gap-2"><Network class="size-4 text-primary" aria-hidden="true" />学术数据工作空间</span>
        <span class="hidden sm:inline">采集 · 治理 · 分析 · 可视化</span>
      </header>

      <div class="login-panel">
        <!-- 左：品牌与能力说明 -->
        <section class="login-panel__intro">
          <div>
            <div class="flex items-center gap-3">
              <span class="grid size-12 shrink-0 place-items-center rounded-lg border border-border bg-card">
                <img :src="brandLogo" alt="" class="size-8" />
              </span>
              <span class="text-base font-semibold tracking-tight text-foreground">学术成果爬虫及可视化系统</span>
            </div>
            <h2 class="login-panel__headline">
              把学术数据整理为<br />可核验的知识资产
            </h2>
            <p class="mt-5 max-w-md text-base leading-relaxed text-muted-foreground">
              统一采集、治理与追溯成果记录，让每一次修订都有来源、每一项结论都可复核。
            </p>
          </div>

          <ol class="flex flex-col">
            <li v-for="(feature, index) in features" :key="feature.label" class="flex flex-col">
              <div v-if="index > 0" class="ml-[23px] h-4 w-px bg-border" aria-hidden="true" />
              <div class="flex items-start gap-4">
                <span class="grid size-12 shrink-0 place-items-center rounded-lg border border-border bg-card text-primary">
                  <component :is="feature.icon" class="size-5" aria-hidden="true" />
                </span>
                <span class="min-w-0 pt-1">
                  <span class="block text-base font-medium text-foreground">{{ feature.label }}</span>
                  <span class="mt-1 block text-sm leading-relaxed text-muted-foreground">{{ feature.note }}</span>
                </span>
              </div>
            </li>
          </ol>
        </section>

        <!-- 右：登录面板 -->
        <section class="login-panel__form-section">
          <div class="mb-8 flex items-center gap-3 lg:hidden">
            <img :src="brandLogo" alt="" class="size-9" />
            <span class="text-sm font-semibold text-foreground">学术成果爬虫及可视化系统</span>
          </div>
          <h1 class="text-2xl font-semibold tracking-tight text-foreground">登录 AACV System</h1>
          <p class="mt-3 text-sm leading-relaxed text-muted-foreground">使用管理员分配的内部账号继续</p>

          <div v-if="route.query.reason === 'expired' || route.query.reason === 'unavailable' || errorMessage" class="mt-6 space-y-2">
            <ElAlert
              v-if="route.query.reason === 'expired'"
              type="warning"
              :closable="false"
              title="会话已过期，请重新登录"
              show-icon
            />
            <ElAlert
              v-if="route.query.reason === 'unavailable'"
              type="error"
              :closable="false"
              title="会话检查失败，请确认服务可用后重试"
              show-icon
            />
            <ElAlert
              v-if="errorMessage"
              type="error"
              :closable="false"
              :title="errorMessage"
              :description="route.query.reason ? '请重新输入凭据后继续。' : undefined"
              show-icon
            />
          </div>

          <form class="login-form" novalidate @submit.prevent="submit">
            <div class="grid gap-2.5">
              <label class="text-sm font-medium text-foreground" for="username">
                <UserRound class="mr-1.5 inline size-4 align-[-3px] text-muted-foreground" aria-hidden="true" />用户名
              </label>
              <ElInput
                id="username"
                class="login-form__input"
                v-model="form.username"
                autocomplete="username"
                :maxlength="64"
                placeholder="请输入用户名"
                size="large"
                clearable
              />
            </div>
            <div class="grid gap-2.5">
              <label class="text-sm font-medium text-foreground" for="password">
                <Lock class="mr-1.5 inline size-4 align-[-3px] text-muted-foreground" aria-hidden="true" />密码
              </label>
              <ElInput
                id="password"
                class="login-form__input"
                v-model="form.password"
                type="password"
                autocomplete="current-password"
                :maxlength="128"
                placeholder="请输入密码"
                size="large"
                show-password
                @keydown.enter="submit"
              />
            </div>
            <ElButton
              type="primary"
              size="large"
              class="login-form__submit mt-1 w-full"
              :loading="loading"
              @click="submit"
            >
              进入工作台
              <ArrowRight class="ml-1 size-4" aria-hidden="true" />
            </ElButton>
          </form>

          <p class="mt-7 flex items-start gap-2 border-t border-border pt-5 text-xs leading-relaxed text-muted-foreground">
            <ShieldCheck class="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            <span>请使用分配给你的账号登录。<br />忘记密码请联系管理员重置。</span>
          </p>
        </section>
      </div>

      <footer class="login-page__footer">
        <span>成果 · 作者 · 机构 · 期刊 · 主题</span>
        <span class="hidden sm:inline">连接学术成果与知识脉络</span>
      </footer>
    </div>
  </main>
</template>

<style scoped>
.login-page {
  position: relative;
  display: grid;
  min-height: 100dvh;
  place-items: center;
  overflow: hidden;
  padding: clamp(32px, 7dvh, 88px) clamp(24px, 4vw, 80px);
  background: hsl(var(--background));
}

.login-page__constellation {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.login-page__connections { stroke: hsl(var(--primary) / 0.14); stroke-width: 1; }
.login-page__nodes { fill: hsl(var(--background)); stroke: hsl(var(--primary) / 0.3); stroke-width: 1.5; }
.login-page__node-labels { fill: hsl(var(--muted-foreground) / 0.65); font-size: 12px; }

.login-page__content {
  position: relative;
  width: 100%;
  max-width: 1360px;
}

.login-page__masthead,
.login-page__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  color: hsl(var(--muted-foreground));
  font-size: var(--font-size-sm);
}

.login-page__masthead { margin-bottom: var(--space-5); }
.login-page__footer { margin-top: var(--space-5); }

.login-panel {
  display: grid;
  grid-template-columns: 1.08fr 1fr;
  min-height: 640px;
  overflow: hidden;
  border: 1px solid hsl(var(--border));
  border-radius: var(--radius-xl);
  background: hsl(var(--card));
}

.login-panel__intro,
.login-panel__form-section {
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: clamp(32px, 4vw, 64px);
}

.login-panel__intro {
  justify-content: space-between;
  gap: var(--space-10);
  border-right: 1px solid hsl(var(--border));
  background: hsl(var(--muted) / 0.3);
}

.login-panel__headline {
  margin-top: var(--space-10);
  color: hsl(var(--foreground));
  font-size: clamp(var(--font-size-3xl), 2.4vw, var(--font-size-4xl));
  font-weight: var(--font-semibold);
  line-height: var(--leading-snug);
  letter-spacing: var(--tracking-tight);
}

.login-panel__form-section { justify-content: center; }
.login-form { display: grid; gap: var(--space-6); margin-top: var(--space-8); }
.login-form__input { --el-input-height: 52px; font-size: var(--font-size-lg); }
.login-form__submit { --el-button-size: 52px; font-size: var(--font-size-lg); }

@media (max-width: 1023px) {
  .login-page__content { max-width: 560px; }
  .login-page__constellation,
  .login-panel__intro { display: none; }
  .login-panel { grid-template-columns: minmax(0, 1fr); min-height: 0; }
  .login-panel__form-section { padding: var(--space-10); }
}

@media (max-width: 639px) {
  .login-page { padding: var(--space-8) var(--space-4); }
  .login-panel__form-section { padding: var(--space-8) var(--space-6); }
  .login-page__footer { justify-content: center; }
}
</style>
