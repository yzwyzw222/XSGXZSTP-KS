<script setup>
import { onUnmounted, ref } from 'vue'
import PortalIcon from './PortalIcon.vue'

defineProps({ pending: Boolean, error: { type: String, default: '' } })
const emit = defineEmits(['submit'])
const username = ref('')
const password = ref('')
const showPassword = ref(false)

function submit() {
  emit('submit', { username: username.value.trim(), password: password.value })
}

onUnmounted(() => { password.value = '' })
</script>

<template>
  <div class="portal-shell login-shell">
    <header class="site-header login-header">
      <a class="brand" href="/" aria-label="学术智能平台首页">
        <PortalIcon name="mortarboard-fill" class="brand-mark" />
        <span class="brand-copy">学术智能平台<small>AI FOR A BETTER RESEARCH</small></span>
      </a>
      <span class="login-header-note">连接知识 · 探索未来</span>
    </header>

    <main class="login-main">
      <section class="login-intro" aria-labelledby="login-intro-title">
        <h1 id="login-intro-title">一次登录<br /><span>开启学术探索</span></h1>
        <p class="login-intro-copy">从学术数据到知识发现，<br />在同一工作空间，连接每一步研究。</p>
        <ul class="login-capabilities" aria-label="登录后可访问的研究工具">
          <li><PortalIcon name="share" /><span>学术关系<span>发现合作与引用网络</span></span></li>
          <li><PortalIcon name="file-earmark-text" /><span>实体抽取<span>从多源文本构建知识</span></span></li>
          <li><PortalIcon name="graph-up-arrow" /><span>成果采集<span>汇集数据与可视化分析</span></span></li>
          <li><PortalIcon name="person" /><span>学者图谱<span>探索学者与科研成果</span></span></li>
        </ul>
      </section>

      <section class="login-panel" aria-labelledby="login-title">
        <h2 id="login-title">登录学术智能平台</h2>
        <p class="login-subtitle">欢迎回来，继续你的研究探索。</p>
        <form class="login-form" :aria-busy="pending" @submit.prevent="submit">
          <div class="login-field">
            <label for="portal-username">账号</label>
            <div class="login-input">
              <PortalIcon name="person" />
              <input id="portal-username" v-model="username" name="username" type="text" autocomplete="username" placeholder="请输入账号" required maxlength="64" :readonly="pending" :aria-describedby="error ? 'login-error' : undefined" />
            </div>
          </div>
          <div class="login-field">
            <label for="portal-password">密码</label>
            <div class="login-input">
              <input id="portal-password" v-model="password" name="password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="请输入密码" required maxlength="128" :readonly="pending" :aria-describedby="error ? 'login-error' : undefined" />
              <button type="button" class="password-toggle" :aria-label="showPassword ? '隐藏密码' : '显示密码'" :aria-pressed="showPassword" @click="showPassword = !showPassword">{{ showPassword ? '隐藏' : '显示' }}</button>
            </div>
          </div>
          <p v-if="error" id="login-error" class="login-error" role="alert">{{ error }}</p>
          <button class="entry-button login-submit" type="submit" :disabled="pending || !username.trim() || !password">
            {{ pending ? '正在登录…' : '登录并进入平台' }}<PortalIcon v-if="!pending" name="arrow-right" />
          </button>
          <p class="login-form-note">登录后可直接进入各子系统，无需重复登录。</p>
        </form>
        <p class="login-support"><PortalIcon name="question-circle-fill" />需要账号或忘记密码？请联系平台管理员。</p>
      </section>
    </main>
    <footer class="login-footer">学术无界 · 智能未来<span>AI × KNOWLEDGE × RESEARCH</span></footer>
  </div>
</template>

<style scoped>
.login-shell { display: flex; flex-direction: column; background-position: center; }
.login-header { justify-content: space-between; }
.login-header-note { color: var(--muted); font-size: 13px; letter-spacing: 2px; }
.login-main { display: grid; grid-template-columns: minmax(0, 1fr) minmax(360px, 440px); align-items: center; gap: clamp(48px, 8vw, 140px); flex: 1; width: min(1160px, 100% - 80px); margin: 0 auto; padding: 72px 0; }
.login-intro h1 { margin: 0; color: var(--ink); font-size: clamp(38px, 4vw, 60px); line-height: 1.42; font-weight: 700; letter-spacing: 2px; }
.login-intro h1 span { color: var(--cyan); }
.login-intro-copy { margin: 24px 0 38px; color: #c3dcf6; font-size: 18px; line-height: 1.9; }
.login-capabilities { display: grid; grid-template-columns: 1fr 1fr; gap: 28px 20px; padding: 0; margin: 0; list-style: none; }
.login-capabilities li { display: flex; align-items: flex-start; gap: 12px; font-size: 15px; }
.login-capabilities .portal-icon { width: 23px; height: 23px; color: var(--cyan); }
.login-capabilities li > span > span { display: block; margin-top: 7px; color: #aacbec; font-size: 12px; line-height: 1.6; }
.login-panel { padding: 42px 36px 28px; border: 1px solid #367cb5; border-radius: 16px; background: #061a32f5; }
.login-panel h2 { margin: 0; font-size: 26px; line-height: 1.45; }
.login-subtitle { margin: 10px 0 32px; color: var(--muted); font-size: 14px; line-height: 1.8; }
.login-form { display: flex; flex-direction: column; gap: 23px; }
.login-field label { display: block; margin-bottom: 10px; font-size: 14px; }
.login-input { display: flex; align-items: center; gap: 12px; min-height: 50px; padding: 0 14px; border: 1px solid #367cb5; border-radius: 7px; background: #07152a; color: #a0c5e6; }
.login-input:focus-within { border-color: var(--cyan); outline: 1px solid var(--cyan); }
.login-input input { width: 100%; min-width: 0; min-height: 48px; padding: 0; border: 0; outline: 0; background: transparent; color: var(--ink); font-size: 15px; caret-color: var(--cyan); }
.login-input input::placeholder { color: #91abc7; }
.login-input input::selection { background: #275e8d; }
.password-toggle { align-self: stretch; flex-shrink: 0; min-width: 42px; padding: 0 2px; background: transparent; color: #9fdbff; font-size: 13px; }
.password-toggle:hover { color: #e3f9ff; }
.login-error { margin: -3px 0; padding: 12px; border: 1px solid #9b5967; border-radius: 6px; background: #4b263b; color: #ffdae1; font-size: 13px; line-height: 1.7; overflow-wrap: anywhere; }
.login-submit { display: flex; justify-content: center; width: 100%; min-height: 50px; margin-top: 5px; border-radius: 7px; font-size: 16px; }
.login-submit:disabled { cursor: not-allowed; opacity: .65; transform: none; }
.login-form-note { margin: -8px 0 0; text-align: center; color: var(--muted); font-size: 12px; line-height: 1.8; text-wrap: pretty; }
.login-support { display: flex; align-items: center; justify-content: center; gap: 8px; margin: 30px 0 0; padding-top: 22px; border-top: 1px solid #244364; color: #a3bfdd; font-size: 12px; line-height: 1.8; }
.login-support .portal-icon { width: 15px; height: 15px; }
.login-footer { padding: 0 24px 28px; text-align: center; color: #a2bedf; font-size: 12px; letter-spacing: 2px; }
.login-footer span { display: block; margin-top: 10px; font-size: 9px; letter-spacing: 3px; }
@media (max-width: 900px) {
  .login-main { width: min(600px, 100% - 40px); grid-template-columns: 1fr; gap: 32px; padding: 40px 0; }
  .login-intro h1 { font-size: 34px; text-align: center; }
  .login-intro h1 br { display: none; }
  .login-intro-copy, .login-capabilities { display: none; }
  .login-panel { width: min(440px, 100%); margin: 0 auto; }
}
@media (max-width: 480px) {
  .login-header { height: 68px; padding: 0 20px; }
  .login-header .brand { gap: 10px; padding: 0; }
  .login-header .brand-mark { width: 38px; height: 36px; }
  .login-header .brand-copy { font-size: 19px; }
  .login-header-note { display: none; }
  .login-main { gap: 26px; padding: 36px 0; }
  .login-intro h1 { font-size: clamp(23px, 6vw, 30px); letter-spacing: 0; }
  .login-panel { padding: 30px 22px 24px; }
  .login-panel h2 { font-size: 23px; }
  .login-support { align-items: flex-start; font-size: 11px; }
  .login-support .portal-icon { margin-top: 3px; }
}
</style>
