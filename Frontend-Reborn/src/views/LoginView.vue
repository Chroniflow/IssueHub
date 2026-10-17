<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { fetchRegistrationConfig, getApiErrorMessage } from '@/api/http'
import TurnstileWidget from '@/components/TurnstileWidget.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const username = ref('')
const password = ref('')
const message = ref('')
const turnstileEnabled = ref(false)
const turnstileRequired = ref(false)
const turnstileToken = ref('')
const turnstileWidget = ref<InstanceType<typeof TurnstileWidget> | null>(null)
const turnstileSiteKey = import.meta.env.VITE_TURNSTILE_SITE_KEY?.trim() ?? ''
if (route.query.passwordChanged === '1') message.value = '密码已修改，请使用新密码重新登录。'
else if (route.query.sessionExpired === '1') message.value = '登录状态已失效，请重新登录。'

onMounted(async () => {
  try {
    const config = await fetchRegistrationConfig()
    turnstileEnabled.value = config.turnstileEnabled
    turnstileRequired.value = config.turnstileRequired
  } catch (error: unknown) {
    message.value = getApiErrorMessage(error, '无法读取登录配置，请稍后重试。')
  }
})

async function login() {
  message.value = ''
  if (!username.value || !password.value) {
    message.value = '请输入用户名和密码。'
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
  try {
    await auth.login(username.value, password.value, turnstileToken.value || undefined)
    const redirect =
      typeof route.query.redirect === 'string' &&
      route.query.redirect.startsWith('/') &&
      !route.query.redirect.startsWith('//')
        ? route.query.redirect
        : '/'
    await router.replace(redirect)
  } catch {
    message.value = auth.error
    try {
      turnstileRequired.value = (await fetchRegistrationConfig()).turnstileRequired
    } catch {
      /* retain current state */
    }
  } finally {
    if (turnstileRequired.value) turnstileWidget.value?.reset()
  }
}
</script>

<template>
  <main class="form-page compact">
    <button v-ripple class="back-link" @click="router.push('/')">← 返回反馈列表</button>
    <section class="form-panel">
      <p class="eyebrow">PLAYER ACCOUNT</p>
      <h1>登录</h1>
      <p class="form-lede">登录后可以提交反馈并跟进处理进度。</p>
      <form @submit.prevent="login">
        <label
          >用户名或邮箱<input
            v-model="username"
            maxlength="254"
            autocomplete="username"
            required
        /></label>
        <label
          >密码<input
            v-model="password"
            type="password"
            maxlength="128"
            autocomplete="current-password"
            required
        /></label>
        <TurnstileWidget
          v-if="turnstileRequired && turnstileSiteKey"
          ref="turnstileWidget"
          :site-key="turnstileSiteKey"
          action="login"
          @token="turnstileToken = $event"
        />
        <p v-if="message" class="form-error" role="alert">{{ message }}</p>
        <button v-ripple class="primary-button" type="submit" :disabled="auth.loading">
          {{ auth.loading ? '登录中…' : '登录' }}
        </button>
      </form>
      <p class="auth-switch">
        <RouterLink to="/reset-password">忘记密码？</RouterLink> · 还没有账号？<RouterLink
          :to="
            route.query.redirect ? { path: '/register', query: { redirect: route.query.redirect } } : '/register'
          "
          >创建账号</RouterLink
        >
      </p>
    </section>
  </main>
</template>
