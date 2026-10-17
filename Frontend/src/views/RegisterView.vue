<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { hasTextWithin, isValidEmail } from '../utils/format'
import { fetchRegistrationConfig, getApiErrorMessage, requestRegistrationCode } from '../api/http'
import TurnstileWidget from '../components/TurnstileWidget.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const username = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const message = ref('')
const verificationCode = ref('')
const emailVerificationRequired = ref(true)
const turnstileEnabled = ref(false)
const turnstileRequired = ref(false)
const turnstileToken = ref('')
const turnstileWidget = ref<InstanceType<typeof TurnstileWidget> | null>(null)
const sendingCode = ref(false)
const codeMessage = ref('')
const cooldown = ref(0)
const turnstileSiteKey = import.meta.env.VITE_TURNSTILE_SITE_KEY?.trim() ?? ''
let cooldownTimer: number | undefined

onMounted(async () => {
  try {
    const config = await fetchRegistrationConfig()
    emailVerificationRequired.value = config.emailVerificationRequired
    turnstileEnabled.value = config.turnstileEnabled
    turnstileRequired.value = config.turnstileRequired
  } catch (error: unknown) {
    message.value = getApiErrorMessage(error, '无法读取注册配置，请稍后重试。')
  }
})

onBeforeUnmount(() => {
  if (cooldownTimer !== undefined) window.clearInterval(cooldownTimer)
})

function startCooldown() {
  cooldown.value = 60
  if (cooldownTimer !== undefined) window.clearInterval(cooldownTimer)
  cooldownTimer = window.setInterval(() => {
    cooldown.value--
    if (cooldown.value <= 0 && cooldownTimer !== undefined) {
      window.clearInterval(cooldownTimer)
      cooldownTimer = undefined
    }
  }, 1000)
}

async function sendCode() {
  message.value = ''
  codeMessage.value = ''
  const normalizedEmail = email.value.trim()
  if (!isValidEmail(normalizedEmail)) {
    message.value = '请输入有效邮箱后再获取验证码。'
    return
  }
  if (turnstileRequired.value && !turnstileSiteKey) {
    message.value = '站点未配置 Turnstile 公钥，请联系管理员。'
    return
  }
  if (turnstileRequired.value && !turnstileToken.value) {
    message.value = '请先完成人机验证。'
    return
  }
  sendingCode.value = true
  try {
    const result = await requestRegistrationCode(normalizedEmail, turnstileToken.value || undefined)
    codeMessage.value = result.message
    startCooldown()
  } catch (error: unknown) {
    message.value = getApiErrorMessage(error, '验证码发送失败，请稍后重试。')
    try { turnstileRequired.value = (await fetchRegistrationConfig()).turnstileRequired } catch { /* retain current state */ }
  } finally {
    sendingCode.value = false
    if (turnstileEnabled.value) turnstileWidget.value?.reset()
  }
}

async function register() {
  message.value = ''
  const normalizedUsername = username.value.trim()
  const normalizedEmail = email.value.trim()
  if (!hasTextWithin(normalizedUsername, 3, 32)) {
    message.value = '用户名长度必须为 3-32 个字符。'
    return
  }
  if (!isValidEmail(normalizedEmail)) {
    message.value = '请输入有效邮箱。'
    return
  }
  if (!hasTextWithin(password.value, 8, 128)) {
    message.value = '密码长度必须为 8-128 个字符。'
    return
  }
  if (password.value !== confirmPassword.value) {
    message.value = '两次输入的密码不一致。'
    return
  }
  if (emailVerificationRequired.value && !/^\d{6}$/.test(verificationCode.value.trim())) {
    message.value = '请输入 6 位邮箱验证码。'
    return
  }
  try {
    if (turnstileRequired.value && !turnstileToken.value) {
      message.value = '请先完成人机验证。'
      return
    }
    await auth.register(normalizedUsername, normalizedEmail, password.value, verificationCode.value.trim() || undefined, turnstileToken.value || undefined)
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') && !route.query.redirect.startsWith('//') ? route.query.redirect : '/'
    router.replace(redirect)
  } catch {
    message.value = auth.error
    try { turnstileRequired.value = (await fetchRegistrationConfig()).turnstileRequired } catch { /* retain current state */ }
  }
}
</script>

<template>
  <main class="form-page compact">
    <button class="back-link" type="button" @click="router.push('/')">← 返回反馈列表</button>
    <section class="form-panel">
      <p class="eyebrow">PLAYER ACCOUNT</p>
      <h1>创建账号</h1>
      <p class="form-lede">注册后即可提交反馈并持续跟进处理进度。</p>
      <form @submit.prevent="register">
        <label>用户名<input v-model="username" minlength="3" maxlength="32" autocomplete="username" required /></label>
        <label>邮箱<input v-model="email" type="email" maxlength="254" autocomplete="email" required /></label>
        <TurnstileWidget v-if="turnstileRequired && turnstileSiteKey" ref="turnstileWidget" :site-key="turnstileSiteKey" @token="turnstileToken = $event" />
        <label v-if="emailVerificationRequired">邮箱验证码
          <span class="verification-row">
            <input v-model="verificationCode" inputmode="numeric" autocomplete="one-time-code" maxlength="6" pattern="[0-9]{6}" required />
            <button class="code-button" type="button" :disabled="sendingCode || cooldown > 0" @click="sendCode">
              {{ sendingCode ? '发送中…' : cooldown > 0 ? `${cooldown} 秒` : '获取验证码' }}
            </button>
          </span>
        </label>
        <p v-if="codeMessage" class="form-message" role="status">{{ codeMessage }}</p>
        <label>密码<input v-model="password" type="password" minlength="8" maxlength="128" autocomplete="new-password" required /></label>
        <label>确认密码<input v-model="confirmPassword" type="password" minlength="8" maxlength="128" autocomplete="new-password" required /></label>
        <p v-if="message" class="form-error" role="alert">{{ message }}</p>
        <button class="primary-button" type="submit" :disabled="auth.loading">{{ auth.loading ? '注册中…' : '创建账号' }}</button>
      </form>
      <p class="auth-switch">已有账号？<RouterLink :to="route.query.redirect ? { path: '/login', query: { redirect: route.query.redirect } } : '/login'">返回登录</RouterLink></p>
    </section>
  </main>
</template>
