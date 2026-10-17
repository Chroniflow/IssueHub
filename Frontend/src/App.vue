<script setup lang="ts">
import { useRouter } from 'vue-router'
import { nextTick, onMounted, ref, watch } from 'vue'
import { getApiErrorMessage } from './api/http'
import { useAuthStore } from './stores/auth'
import UserAvatar from './components/UserAvatar.vue'
import { applySiteIcon, DEFAULT_SITE_ICON, siteIconUrl } from './siteConfig'

const router = useRouter()
const auth = useAuthStore()
const routeAnnouncement = ref<HTMLElement | null>(null)
const logoutError = ref('')
onMounted(() => { void auth.initialize() })
watch(() => router.currentRoute.value.fullPath, async () => {
  await nextTick()
  if (routeAnnouncement.value) {
    routeAnnouncement.value.textContent = `已打开 ${document.title || 'SimpIssueHub'} 页面`
    routeAnnouncement.value.focus()
  }
})

async function logout() {
  logoutError.value = ''
  try { await auth.logout() }
  catch (requestError: unknown) { logoutError.value = getApiErrorMessage(requestError, '退出请求未完成，本地登录状态已清除。') }
  finally { await router.push('/') }
}
</script>

<template>
  <div class="shell">
    <div ref="routeAnnouncement" class="sr-only" tabindex="-1" aria-live="polite"></div>
    <header class="site-header">
      <div class="global-bar">
        <a class="brand-mark-link" href="https://issue.simpmc.org" aria-label="打开 issue.simpmc.org" title="打开 issue.simpmc.org"><img class="brand-mark" :src="siteIconUrl" alt="SimpIssueHub 站点图标" @error="applySiteIcon(DEFAULT_SITE_ICON)" /></a>
        <strong class="global-title">SimpMC 玩家社区</strong>
        <span class="global-spacer"></span>
        <span v-if="logoutError" class="logout-error" role="status">{{ logoutError }}</span>
        <template v-if="auth.user">
          <RouterLink class="header-account-link" to="/account/security"><UserAvatar :username="auth.user.username" :url="auth.user.avatarUrl" size="small" /><span>{{ auth.user.username }}</span></RouterLink>
          <button class="header-button" type="button" @click="logout">退出</button>
        </template>
        <button v-else class="header-button" type="button" @click="router.push('/login')">登录</button>
      </div>
      <div class="repo-bar">
        <div class="repo-identity"><span class="repo-icon" aria-hidden="true">◇</span><span>SimpMC</span><span class="repo-slash">/</span><strong>SimpIssueHub</strong><span class="visibility-badge">登录可见</span></div>
        <span class="repo-description">Minecraft 服务器玩家反馈与问题跟踪</span>
      </div>
      <nav class="repo-nav" aria-label="站点导航">
        <RouterLink to="/" :class="{ active: router.currentRoute.value.path === '/' || router.currentRoute.value.path.startsWith('/issues') }"><span aria-hidden="true">◉</span>反馈</RouterLink>
        <RouterLink to="/polls" exact-active-class="active"><span aria-hidden="true">▥</span>投票</RouterLink>
        <RouterLink to="/about" exact-active-class="active"><span aria-hidden="true">ⓘ</span>关于</RouterLink>
        <RouterLink v-if="auth.user?.role === 'ADMIN'" to="/admin" exact-active-class="active"><span aria-hidden="true">⚙</span>管理</RouterLink>
      </nav>
    </header>
    <RouterView />
  </div>
</template>
