<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import UserAvatar from '@/components/UserAvatar.vue'

const router = useRouter()
const auth = useAuthStore()
</script>

<template>
  <main class="form-page compact">
    <button v-ripple class="back-link" @click="router.push('/')">← 返回反馈列表</button>
    <section class="form-panel">
      <p class="eyebrow">SETTINGS</p>
      <h1>设置</h1>
      <p class="form-lede">管理你的账户与应用信息。</p>

      <template v-if="auth.user">
        <div class="settings-account">
          <UserAvatar :username="auth.user.username" :url="auth.user.avatarUrl" size="large" />
          <div>
            <strong>{{ auth.user.username }}</strong>
            <small>{{ auth.user.email }} · {{ auth.user.role }}</small>
          </div>
        </div>

        <nav class="settings-links" aria-label="账户设置导航">
          <RouterLink v-ripple class="settings-link" to="/account/security">
            <span aria-hidden="true">◈</span>
            <span>
              <strong>账户安全</strong>
              <small>修改密码、邮箱和头像</small>
            </span>
          </RouterLink>
          <RouterLink v-ripple class="settings-link" to="/account/tokens">
            <span aria-hidden="true">⚿</span>
            <span>
              <strong>API Token</strong>
              <small>创建和管理用于脚本访问的 Token</small>
            </span>
          </RouterLink>
          <RouterLink v-if="auth.user.role === 'ADMIN'" v-ripple class="settings-link" to="/admin/config">
            <span aria-hidden="true">⚙</span>
            <span>
              <strong>站点配置</strong>
              <small>管理员站点参数</small>
            </span>
          </RouterLink>
        </nav>
      </template>

      <template v-else>
        <p class="list-status">登录后即可管理账户安全与 API Token。</p>
        <button v-ripple class="primary-button" type="button" @click="router.push('/login')">
          前往登录
        </button>
      </template>

      <nav class="settings-links settings-links--secondary" aria-label="站点导航">
        <RouterLink v-ripple class="settings-link" to="/about">
          <span aria-hidden="true">ⓘ</span>
          <span><strong>关于 SimpIssueHub</strong><small>了解这个反馈站</small></span>
        </RouterLink>
        <RouterLink v-ripple class="settings-link" to="/tags">
          <span aria-hidden="true">◇</span>
          <span><strong>标签</strong><small>按标签浏览反馈</small></span>
        </RouterLink>
      </nav>
    </section>
  </main>
</template>

<style scoped>
.settings-account {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  padding-bottom: 1.25rem;
  border-bottom: 1px solid var(--color-border);
}

.settings-account strong {
  display: block;
  color: var(--color-heading);
  font-size: 1.05rem;
  font-weight: 600;
}

.settings-account small {
  color: var(--color-text-muted);
}

.settings-links {
  display: grid;
  gap: 0.5rem;
  margin-top: 1.25rem;
}

.settings-links--secondary {
  padding-top: 1.25rem;
  border-top: 1px solid var(--color-border);
}

.settings-link {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  padding: 0.75rem 0.9rem;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  color: var(--color-text);
  background: var(--color-background);
  text-decoration: none;
  transition:
    background-color 0.15s,
    border-color 0.15s;
}

.settings-link:hover {
  border-color: var(--color-border-hover);
  background: var(--ih-c-hover);
}

.settings-link > span:first-child {
  font-size: 1.1rem;
  color: var(--color-text-muted);
}

.settings-link strong {
  display: block;
  color: var(--color-heading);
  font-weight: 600;
}

.settings-link small {
  color: var(--color-text-muted);
}
</style>
