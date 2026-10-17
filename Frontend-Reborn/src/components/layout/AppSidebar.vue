<script setup lang="ts">
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

defineProps<{ collapsed?: boolean }>()

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const primaryNav = [
  { to: '/', label: '反馈', icon: '◉' },
  { to: '/tags', label: '标签', icon: '◇' },
  { to: '/about', label: '关于', icon: 'ⓘ' },
]

const adminNav = [
  { to: '/admin/users', label: '用户', icon: '◎' },
  { to: '/admin/categories', label: '分类', icon: '▦' },
  { to: '/admin/config', label: '配置', icon: '⚙' },
]

const isAdmin = computed(() => auth.user?.role === 'ADMIN')

function isActive(to: string) {
  if (to === '/') return route.path === '/' || route.path.startsWith('/issues')
  return route.path === to || route.path.startsWith(`${to}/`)
}

async function logout() {
  await auth.logout()
  await router.push('/')
}
</script>

<template>
  <aside class="sidebar" :class="{ 'sidebar--collapsed': collapsed }" aria-label="站点导航">
    <nav class="sidebar__nav">
      <RouterLink
        v-for="item in primaryNav"
        :key="item.to"
        v-ripple
        :to="item.to"
        class="sidebar__link"
        :class="{ 'sidebar__link--active': isActive(item.to) }"
        :title="collapsed ? item.label : undefined"
      >
        <span class="sidebar__icon" aria-hidden="true">{{ item.icon }}</span>
        <span class="sidebar__label">{{ item.label }}</span>
      </RouterLink>

      <template v-if="isAdmin">
        <span class="sidebar__heading">管理</span>
        <RouterLink
          v-for="item in adminNav"
          :key="item.to"
          v-ripple
          :to="item.to"
          class="sidebar__link"
          :class="{ 'sidebar__link--active': isActive(item.to) }"
          :title="collapsed ? item.label : undefined"
        >
          <span class="sidebar__icon" aria-hidden="true">{{ item.icon }}</span>
          <span class="sidebar__label">{{ item.label }}</span>
        </RouterLink>
      </template>
    </nav>

    <div class="sidebar__spacer"></div>

    <div class="sidebar__footer">
      <template v-if="auth.user">
        <RouterLink
          v-ripple
          to="/settings"
          class="sidebar__link sidebar__link--sub"
          :title="collapsed ? '设置' : undefined"
        >
          <span class="sidebar__icon" aria-hidden="true">⚙</span>
          <span class="sidebar__label">设置</span>
        </RouterLink>
        <button
          v-ripple
          class="sidebar__link sidebar__action"
          type="button"
          :title="collapsed ? '退出' : undefined"
          @click="logout"
        >
          <span class="sidebar__icon" aria-hidden="true">⏻</span>
          <span class="sidebar__label">退出</span>
        </button>
      </template>
      <RouterLink
        v-else
        v-ripple
        to="/login"
        class="sidebar__link sidebar__link--sub"
        :title="collapsed ? '登录' : undefined"
      >
        <span class="sidebar__icon" aria-hidden="true">◐</span>
        <span class="sidebar__label">登录</span>
      </RouterLink>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  width: var(--sidebar-width);
  position: sticky;
  top: var(--header-height);
  height: calc(100vh - var(--header-height));
  padding: 1.25rem 0;
  transition: width 0.2s ease;
}

/* ---------- 导航区 ---------- */
.sidebar__nav {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.sidebar__heading {
  padding: 0.9rem 0.75rem 0.25rem 1.5rem;
  color: var(--color-text-subtle);
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.sidebar__link {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  height: 2.6rem;
  margin-right: 0.5rem;
  padding: 0.5rem 0.75rem 0.5rem 1.5rem;
  border: 0;
  border-radius: 0 30px 30px 0;
  color: var(--color-text);
  background: transparent;
  font-family: inherit;
  font-size: 0.9rem;
  text-align: left;
  text-decoration: none;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color 0.15s,
    color 0.15s;
}

.sidebar__icon {
  flex-shrink: 0;
  width: 18px;
  text-align: center;
  opacity: 0.7;
}

.sidebar__label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar__link--active {
  background: var(--ih-c-sidebar-now);
  color: var(--ih-c-text);
  font-weight: 600;
}

.sidebar__link:hover {
  background: var(--ih-c-hover);
  color: var(--ih-c-text);
}

.sidebar__link--sub {
  font-size: 0.85rem;
}

/* ---------- 底部区 ---------- */
.sidebar__spacer {
  flex: 1;
}

.sidebar__footer {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.sidebar__action {
  width: calc(100% - 0.5rem);
}

/* ---------- 收起为图标栏 ---------- */
.sidebar--collapsed {
  width: var(--sidebar-collapsed-width);
}

.sidebar--collapsed .sidebar__label,
.sidebar--collapsed .sidebar__heading {
  display: none;
}

.sidebar--collapsed .sidebar__link {
  justify-content: center;
  padding: 0.5rem;
  margin-right: 0.5rem;
  border-radius: 12px;
}

.sidebar--collapsed .sidebar__action {
  width: calc(100% - 0.5rem);
}

/* ---------- 移动端：抽屉式浮层 ---------- */
@media (max-width: 900px) {
  .sidebar {
    position: fixed;
    top: var(--header-height);
    left: 0;
    z-index: 40;
    width: min(260px, 80vw);
    height: calc(100vh - var(--header-height));
    padding: 1rem 0;
    background: var(--color-background);
    box-shadow: 0 12px 32px rgba(0, 0, 0, 0.18);
    transform: translateX(-100%);
    transition: transform 0.2s ease;
  }

  .app-shell--nav-open .sidebar {
    transform: translateX(0);
  }

  .sidebar--collapsed {
    width: min(260px, 80vw);
  }

  .sidebar--collapsed .sidebar__label,
  .sidebar--collapsed .sidebar__heading {
    display: block;
  }

  .sidebar--collapsed .sidebar__link {
    justify-content: flex-start;
    padding: 0.5rem 0.75rem 0.5rem 1.5rem;
    border-radius: 0 30px 30px 0;
  }
}
</style>
