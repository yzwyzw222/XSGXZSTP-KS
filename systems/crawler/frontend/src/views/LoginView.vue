<script setup lang="ts">
import { ElAlert, ElButton, ElInput } from 'element-plus'
import { ArrowRight, Eye, EyeOff, FileText, LockKeyhole, Share2, ShieldCheck, UserRound } from 'lucide-vue-next'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError, toErrorMessage } from '@/services/api'
import { useSessionStore } from '@/stores/session'

const baseUrl = import.meta.env.BASE_URL
const route = useRoute()
const router = useRouter()
const sessionStore = useSessionStore()

const loading = ref(false)
const errorMessage = ref('')
const passwordVisible = ref(false)
const form = reactive({ username: '', password: '' })

const features = [
  { icon: FileText, label: '来源留痕', note: '每条成果保留采集来源与观测时间' },
  { icon: Share2, label: '冲突治理', note: '确定性标识关联，人工决定可追溯' },
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
    <img
      :src="`${baseUrl}images/login-campus-background.png`"
      alt=""
      class="login-page__background"
      aria-hidden="true"
      fetchpriority="high"
    />
    <div class="login-page__content">
      <header class="login-page__masthead">
        <span class="login-page__motto">探索学术数据 · 赋能科研创新</span>
        <div class="login-page__brand">
          <strong>学术成果信息采集及可视化系统</strong>
          <small>ACADEMIC ACHIEVEMENT VISUALIZATION PLATFORM</small>
        </div>
        <span class="login-page__motto">数据连接世界 · 知识创造未来</span>
      </header>

      <div class="login-panel">
        <section class="login-panel__intro" aria-labelledby="login-intro-title">
          <p class="login-panel__poem" aria-hidden="true">KNOWLEDGE<br />FOR A BRIGHTER<br />TOMORROW</p>
          <div class="login-panel__message">
            <h2 id="login-intro-title" class="login-panel__headline">
              汇聚学术成果 · 发现知识关联
            </h2>
            <p class="login-panel__description">
              统一采集、治理与追溯成果记录，让每一次修订都有来源、每一项结论都可复核。
            </p>
          </div>

          <ul class="login-capabilities" aria-label="系统能力">
            <li v-for="feature in features" :key="feature.label" :title="feature.note">
              <span class="login-capabilities__icon"><component :is="feature.icon" :size="28" aria-hidden="true" /></span>
              <span>{{ feature.label }}</span>
            </li>
          </ul>
        </section>

        <section class="login-panel__form-section" aria-labelledby="login-title">
          <img :src="`${baseUrl}images/login-campus-background.png`" alt="" class="login-panel__waves" aria-hidden="true" />
          <h1 id="login-title" class="login-panel__title">登录系统</h1>
          <p class="login-panel__subtitle">使用管理员分配的内部账号继续</p>

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
            <div class="login-form__field">
              <label class="login-form__label" for="username">
                <UserRound :size="24" aria-hidden="true" />用户名
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
            <div class="login-form__field">
              <label class="login-form__label" for="password">
                <LockKeyhole :size="24" class="login-form__lock" aria-hidden="true" />密码
              </label>
              <ElInput
                id="password"
                class="login-form__input"
                v-model="form.password"
                :type="passwordVisible ? 'text' : 'password'"
                autocomplete="current-password"
                :maxlength="128"
                placeholder="请输入密码"
                size="large"
                @keydown.enter="submit"
              >
                <template #suffix>
                  <button
                    type="button"
                    class="login-form__password-toggle"
                    :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
                    :aria-pressed="passwordVisible"
                    @click="passwordVisible = !passwordVisible"
                  >
                    <component :is="passwordVisible ? Eye : EyeOff" :size="23" aria-hidden="true" />
                  </button>
                </template>
              </ElInput>
            </div>
            <ElButton
              type="primary"
              size="large"
              class="login-form__submit"
              :loading="loading"
              @click="submit"
            >
              进入工作台
              <ArrowRight :size="24" aria-hidden="true" />
            </ElButton>
          </form>

          <p class="login-panel__help">
            <ShieldCheck :size="25" aria-hidden="true" />
            <span>请使用分配给你的账号登录。<br />忘记密码请联系管理员重置。</span>
          </p>
        </section>
      </div>

      <footer class="login-page__footer">
        <span>成果 · 作者 · 机构 · 期刊 · 主题</span>
        <span class="login-page__footer-note">连接学术成果与知识脉络</span>
      </footer>
    </div>
  </main>
</template>

<style scoped>
.login-page {
  --login-ink: #0c1c4b;
  --login-muted: #566b96;
  --login-blue: #0964ee;
  position: relative;
  isolation: isolate;
  min-height: 100dvh;
  padding: 42px clamp(24px, 4.4vw, 104px) 24px;
  color: var(--login-ink);
  background: #f4f9ff;
}

.login-page__background {
  position: absolute;
  z-index: -1;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  opacity: .8;
  pointer-events: none;
}

.login-page__content {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 1840px;
  min-height: calc(100dvh - 66px);
  margin-inline: auto;
}

.login-page__masthead {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 24px;
  min-height: 74px;
  margin-bottom: 42px;
}

.login-page__motto {
  padding-left: 22px;
  border-left: 3px solid #287dff;
  color: var(--login-muted);
  font-size: clamp(13px, 1vw, 17px);
  line-height: 1.25;
  letter-spacing: 1.3px;
  white-space: nowrap;
}

.login-page__motto:last-child {
  padding: 0 22px 0 0;
  border-left: 0;
  border-right: 3px solid #287dff;
  text-align: right;
}

.login-page__brand { text-align: center; }
.login-page__brand strong {
  display: block;
  font-size: clamp(25px, 2.18vw, 40px);
  line-height: 1.45;
  font-weight: 750;
  letter-spacing: 1px;
}
.login-page__brand small {
  display: block;
  margin-top: 3px;
  color: #627eae;
  font-size: clamp(9px, .72vw, 13px);
  line-height: 1.5;
  letter-spacing: 3px;
}

.login-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) clamp(440px, 35.9vw, 660px);
  flex: 1;
  gap: clamp(32px, 4vw, 80px);
  min-height: clamp(620px, calc(100dvh - 275px), 666px);
}

.login-panel__intro {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  min-width: 0;
  padding: 0 0 2px 18px;
}
.login-panel__poem {
  position: absolute;
  top: 50px;
  left: 24px;
  color: #c7dbef;
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 20px;
  line-height: 1.5;
  letter-spacing: 3px;
}
.login-panel__message::before {
  content: '';
  display: block;
  width: 66px;
  margin-bottom: 23px;
  border-top: 4px solid #2780ff;
  border-radius: 4px;
}
.login-panel__headline {
  margin: 0;
  font-size: clamp(28px, 2.52vw, 46px);
  font-weight: 750;
  line-height: 1.4;
  letter-spacing: 1.8px;
  text-wrap: initial;
}
.login-panel__description {
  margin-top: 12px;
  color: #405780;
  font-size: clamp(15px, 1.16vw, 21px);
  line-height: 1.8;
  text-wrap: initial;
}
.login-capabilities {
  display: flex;
  align-items: center;
  gap: 44px;
  margin-top: 25px;
  padding: 0;
  list-style: none;
}
.login-capabilities li {
  position: relative;
  display: flex;
  align-items: center;
  gap: 20px;
  font-size: clamp(16px, 1.2vw, 21px);
  white-space: nowrap;
}
.login-capabilities li + li { padding-left: 44px; }
.login-capabilities li + li::before {
  content: '';
  position: absolute;
  left: 0;
  height: 23px;
  border-left: 1px solid #bad3f6;
}
.login-capabilities__icon {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  flex-shrink: 0;
  border: 2px solid white;
  border-radius: 50%;
  color: #0670ff;
  background: #e3efff;
}

.login-panel__form-section {
  position: relative;
  isolation: isolate;
  align-self: center;
  width: 100%;
  min-width: 0;
  min-height: clamp(620px, calc(100dvh - 275px), 666px);
  padding: 66px clamp(32px, 3.4vw, 62px) 38px;
  padding-right: clamp(32px, 2.75vw, 50px);
  border: 1px solid white;
  border-radius: 18px;
  background: rgb(255 255 255 / 91%);
  box-shadow: 0 18px 54px rgb(59 132 224 / 9%);
}
.login-panel__waves {
  position: absolute;
  z-index: -1;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: none;
  object-position: right bottom;
  opacity: .25;
  pointer-events: none;
}
.login-panel__form-section::before {
  content: '';
  position: absolute;
  top: 21px;
  left: 25px;
  width: 63px;
  border-top: 5px solid #247aff;
  border-radius: 4px;
}
.login-panel__title {
  font-size: clamp(30px, 2.27vw, 40px);
  line-height: 1.45;
  font-weight: 750;
  letter-spacing: .5px;
}
.login-panel__subtitle {
  margin-top: 3px;
  color: var(--login-muted);
  font-size: clamp(16px, 1.2vw, 21px);
  line-height: 1.7;
}
.login-form { display: grid; gap: 32px; margin-top: 34px; }
.login-form__field { display: grid; gap: 9px; }
.login-form__label {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 19px;
  line-height: 1.5;
}
.login-form__label svg { color: var(--login-muted); }
.login-form__label .login-form__lock { color: #0870ff; }
.login-form__input {
  --el-input-height: 58px;
  --el-component-size-large: 58px;
  --el-input-text-color: var(--login-ink);
  --el-input-placeholder-color: #607493;
  --el-input-bg-color: #fff;
  --el-input-border-color: #cbdcfa;
  --el-input-hover-border-color: #8db6f6;
  --el-input-focus-border-color: var(--login-blue);
  --el-input-border-radius: 7px;
  font-size: 19px;
}
.login-form__input :deep(.el-input__wrapper) { padding-inline: 22px; }
.login-form__input :deep(.el-input__icon) { font-size: 23px; color: #627aa5; }
.login-form__password-toggle {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  margin-right: -7px;
  padding: 0;
  border: 0;
  border-radius: 5px;
  color: #627aa5;
  background: transparent;
  cursor: pointer;
}
.login-form__password-toggle:hover { color: var(--login-blue); }
.login-form__submit {
  --el-button-bg-color: var(--login-blue);
  --el-button-border-color: var(--login-blue);
  --el-button-text-color: #fff;
  --el-button-hover-bg-color: #0758d5;
  --el-button-hover-border-color: #0758d5;
  --el-button-active-bg-color: #084dba;
  --el-button-active-border-color: #084dba;
  width: 100%;
  min-height: 63px;
  border-radius: 10px;
  font-size: 20px;
  letter-spacing: 1px;
  box-shadow: 0 10px 23px rgb(12 107 244 / 13%);
}
.login-form__submit svg { margin-left: 18px; }
.login-panel__help {
  display: flex;
  align-items: flex-start;
  gap: 13px;
  margin-top: 36px;
  padding-top: 24px;
  border-top: 1px solid #d5e1f4;
  color: var(--login-muted);
  font-size: 16px;
  line-height: 1.75;
}
.login-panel__help svg { flex-shrink: 0; margin-top: 2px; }
.login-page__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 36px;
  padding-block: 20px 6px;
  border-top: 1px solid #d0e0f5;
  color: var(--login-muted);
  font-size: 16px;
  line-height: 1.6;
  letter-spacing: 1px;
}
.login-page__footer-note { display: flex; align-items: center; gap: 22px; }
.login-page__footer-note::before { content: ''; width: 30px; border-top: 2px solid #8abaff; }

@media (max-width: 1399px) {
  .login-page { padding-top: 28px; }
  .login-page__content { min-height: calc(100dvh - 52px); }
  .login-page__masthead { grid-template-columns: 1fr; margin-bottom: 28px; }
  .login-page__motto { display: none; }
  .login-panel { min-height: 620px; }
  .login-panel__form-section { min-height: 620px; padding-top: 54px; }
  .login-capabilities { gap: 20px; }
  .login-capabilities li { gap: 12px; }
  .login-capabilities li + li { padding-left: 20px; }
  .login-capabilities__icon { width: 48px; height: 48px; }
}
@media (max-width: 1099px) {
  .login-panel { display: block; min-height: 0; width: 100%; max-width: 550px; margin-inline: auto; }
  .login-panel__intro { display: none; }
  .login-panel__form-section { min-height: 0; }
  .login-panel__waves { display: none; }
  .login-page__background { object-position: 38% center; }
}
@media (max-width: 639px) {
  .login-page { padding: 26px 18px 16px; }
  .login-page__content { min-height: calc(100dvh - 42px); }
  .login-page__masthead { min-height: 0; margin-bottom: 30px; }
  .login-page__brand strong { max-width: 340px; margin: auto; font-size: 23px; line-height: 1.5; }
  .login-page__brand small { max-width: 320px; margin: 8px auto 0; font-size: 9px; line-height: 1.7; letter-spacing: 1.7px; }
  .login-panel__form-section { padding: 48px 24px 26px; border-radius: 16px; }
  .login-panel__title { font-size: 29px; }
  .login-panel__subtitle { margin-top: 6px; font-size: 15px; }
  .login-form { gap: 24px; margin-top: 28px; }
  .login-form__label { gap: 10px; font-size: 16px; }
  .login-form__input { --el-input-height: 52px; --el-component-size-large: 52px; font-size: 16px; }
  .login-form__input :deep(.el-input__wrapper) { padding-inline: 15px; }
  .login-form__submit { min-height: 54px; font-size: 17px; }
  .login-panel__help { margin-top: 28px; padding-top: 21px; gap: 10px; font-size: 13px; }
  .login-page__footer { justify-content: center; margin-top: 26px; padding-top: 16px; font-size: 12px; }
  .login-page__footer-note { display: none; }
}
</style>
