<script setup lang="ts">
import { ElAlert, ElButton, ElInput } from 'element-plus'
import { ArrowRight, GraduationCap, History, Lock, Percent, ShieldCheck, UserRound } from 'lucide-vue-next'
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
  <main class="login-page research-surface">
    <div class="login-page__content">
      <header class="login-page__masthead">
        <span class="login-page__motto">探索学术数据 · 赋能科研创新</span>
        <div class="research-brand"><span><strong>学术成果爬虫及可视化系统</strong><small>ACADEMIC ACHIEVEMENT VISUALIZATION PLATFORM</small></span></div>
        <span class="login-page__motto">数据连接世界 · 知识创造未来</span>
      </header>

      <div class="login-panel">
        <!-- 左：品牌与能力说明 -->
        <section class="login-panel__intro">
          <img :src="'/images/research-core.png'" alt="" class="login-core" aria-hidden="true" />
          <div>
            <h2 class="login-panel__headline">
              汇聚学术成果 · 发现知识关联
            </h2>
            <p class="mt-5 max-w-md text-base leading-relaxed text-muted-foreground">
              统一采集、治理与追溯成果记录，让每一次修订都有来源、每一项结论都可复核。
            </p>
          </div>

          <div class="login-capabilities"><span v-for="feature in features" :key="feature.label"><component :is="feature.icon" :size="18" />{{ feature.label }}</span></div>
        </section>

        <!-- 右：登录面板 -->
        <section class="login-panel__form-section">
          <div class="mb-8 flex items-center gap-3 lg:hidden">
            <GraduationCap class="size-9 text-primary" aria-hidden="true" />
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
.login-page { position: relative; display: grid; min-height: 100dvh; place-items: center; padding: 32px; overflow-x: hidden; }
.login-page__content { position: relative; width: 100%; max-width: 1480px; }
.login-page__masthead,.login-page__footer { display: flex; align-items: center; justify-content: space-between; color: #96c5e2; font-size: 13px; padding: 20px 4px; letter-spacing: 2px; }
.login-page__masthead { display: grid; grid-template-columns: 1fr auto 1fr; gap: 16px; border-bottom: 1px solid hsl(var(--primary) / .65); background: linear-gradient(0deg, #06325188, transparent); }
.login-page__motto:last-child { text-align: right; }
.login-page__masthead .research-brand strong { font-size: clamp(24px, 2vw, 34px); }
.login-panel { display: grid; grid-template-columns: 1.25fr 1fr; align-items: center; min-height: 660px; gap: clamp(32px, 5vw, 88px); padding: 32px; }
.login-panel__intro { position: relative; min-width: 0; text-align: center; }
.login-panel__intro > div:first-child > div { justify-content: center; }
.login-panel__headline { margin-top: 12px; font-size: clamp(24px, 2vw, 32px); font-weight: 600; line-height: 1.55; color: #cef2ff; letter-spacing: 2px; }
.login-panel__intro p { margin-inline: auto; }
.login-core { width: 100%; height: 400px; object-fit: contain; transform: scale(1.45); margin: 0 auto 15px; pointer-events: none; }
.login-capabilities { display: flex; gap: 24px; justify-content: center; color: #9adbfa; font-size: 14px; margin-top: 26px; }
.login-capabilities span { display: flex; gap: 8px; align-items: center; }
.login-panel__form-section { position: relative; min-width: 0; padding: 48px; border: 1px solid hsl(var(--primary) / .55); background: linear-gradient(140deg, #0a2a4deb, #04162fee); border-radius: 8px; box-shadow: inset 0 0 35px hsl(var(--primary) / .04), 0 0 35px hsl(var(--primary) / .09); }
.login-panel__form-section::before { content: ''; position: absolute; inset: -1px auto auto -1px; width: 60px; height: 20px; border-top: 3px solid hsl(var(--primary)); border-left: 3px solid hsl(var(--primary)); border-radius: 8px 0 0; }
.login-form { display: grid; gap: 24px; margin-top: 32px; }
.login-form__input { --el-input-height: 52px; --el-component-size-large: 52px; }
.login-form__submit { min-height: 52px; letter-spacing: 2px; }
.login-page__footer { border-top: 1px solid hsl(var(--primary) / .3); }
@media (max-width: 1023px) { .login-panel { display: block; min-height: auto; padding: 36px 0; max-width: 540px; margin-inline: auto; } .login-panel__intro { display: none; } .login-page__masthead { display: block; letter-spacing: 0; } .login-page__motto { display: none; } .login-page__masthead .research-brand strong { font-size: 20px; } }
@media (max-width: 639px) { .login-page { padding: 16px; } .login-panel { padding: 24px 0; } .login-panel__form-section { padding: 28px 22px; } .login-page__footer { font-size: 11px; letter-spacing: 1px; } }
</style>
