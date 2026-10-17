<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import SearchInput from '@/components/SearchInput.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { useAuthStore } from '@/stores/auth'

defineEmits<{ 'toggle-sidebar': [] }>()

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const term = ref(typeof route.query.query === 'string' ? route.query.query : '')

watch(
  () => route.query.query,
  (value) => {
    const next = typeof value === 'string' ? value : ''
    if (next !== term.value) term.value = next
  },
)

function submitSearch(value: string) {
  const query = { ...route.query, query: value.trim() || undefined }
  void router.push({ path: '/', query })
}
</script>

<template>
  <header class="app-header">
    <button
      v-ripple
      type="button"
      class="app-header__toggle"
      aria-label="切换导航栏"
      @click="$emit('toggle-sidebar')"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path
          d="M4 6h16M4 12h16M4 18h16"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
        />
      </svg>
    </button>

    <RouterLink v-ripple to="/" class="app-header__logo" aria-label="返回首页">
      <span aria-hidden="true">IH</span>
    </RouterLink>

    <div class="app-header__search">
      <SearchInput v-model="term" placeholder="搜索反馈…" @enter="submitSearch" />
    </div>

    <div class="app-header__account">
      <RouterLink
        v-if="auth.user"
        v-ripple
        to="/settings"
        class="app-header__avatar"
        :title="auth.user.username"
        :aria-label="`${auth.user.username} 的账户`"
      >
        <UserAvatar :username="auth.user.username" :url="auth.user.avatarUrl" size="medium" />
      </RouterLink>
      <RouterLink v-else v-ripple to="/login" class="app-header__login" aria-label="登录" title="登录">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="8" r="3.75" fill="none" stroke="currentColor" stroke-width="1.8" />
          <path
            d="M4.5 20a7.5 7.5 0 0 1 15 0"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
          />
        </svg>
      </RouterLink>
    </div>
  </header>
</template>

<style scoped>
.app-header__toggle {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 50%;
  color: var(--color-text);
  background: transparent;
  transition:
    background-color 0.15s,
    color 0.15s;
}

.app-header__toggle:hover {
  color: var(--color-primary);
  background: var(--ih-c-hover);
}

.app-header__toggle svg {
  width: 22px;
  height: 22px;
}

.app-header__logo {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--color-primary);
  color: #fff;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  text-decoration: none;
}

.app-header__search {
  flex: 1;
  min-width: 0;
  max-width: 640px;
  margin-left: 0.25rem;
}

.app-header__account {
  display: flex;
  align-items: center;
  margin-left: auto;
}

.app-header__avatar {
  display: block;
  border-radius: 50%;
  transition: box-shadow 0.15s;
}

.app-header__avatar:hover {
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.app-header__login {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  color: var(--color-text);
  text-decoration: none;
  transition:
    background-color 0.15s,
    color 0.15s;
}

.app-header__login:hover {
  color: var(--color-primary);
  background: var(--ih-c-hover);
}

.app-header__login svg {
  width: 24px;
  height: 24px;
}

@media (max-width: 720px) {
  .app-header {
    gap: 0.5rem;
    padding-inline: 0.75rem;
  }

  .app-header__logo {
    display: none;
  }
}
</style>
