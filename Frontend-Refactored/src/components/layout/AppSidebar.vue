<script setup lang="ts">
import { RouterLink } from 'vue-router'

interface NavItem {
  to: string
  label: string
}

const navItems: NavItem[] = [
  { to: '/', label: '首页' },
  { to: '/issues', label: '议题' },
  { to: '/settings', label: '设置' },
]
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar__brand">
      <span class="sidebar__logo">IH</span>
      <span class="sidebar__name">IssueHub</span>
    </div>

    <nav class="sidebar__nav">
      <RouterLink
        v-for="item in navItems"
        v-ripple
        :key="item.to"
        :to="item.to"
        class="sidebar__link"
      >
        {{ item.label }}
      </RouterLink>
    </nav>
  </aside>
</template>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  width: var(--sidebar-width);
  height: 100vh;
  position: sticky;
  top: 0;
  padding: 1.25rem 0;
  /* border-right: 1px solid var(--color-border);  */
  /* background: var(--color-background-soft); */
}

/* ---------- 品牌区 ---------- */
.sidebar__brand {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0 0.5rem 0 1.25rem;
  margin-bottom: 1.5rem;
}

.sidebar__logo {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: var(--color-primary);
  color: #fff;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.sidebar__name {
  color: var(--color-heading);
  font-size: 0.95rem;
  font-weight: 600;
}

/* ---------- 导航区 ---------- */
.sidebar__nav {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.sidebar__link {
  display: flex;
  padding: 0.5rem 0.75rem 0.5rem 1.5rem;
  align-items: center;
  margin-right: 0.5rem;
  border-radius: 0 30px 30px 0;
  color: var(--color-text);
  font-size: 0.9rem;
  text-decoration: none;
  height: 3rem;
  transition:
    background-color 0.15s,
    color 0.15s,
    font-size 0.15s;
}

/* RouterLink 自动添加的激活类 */
.sidebar__link.router-link-exact-active {
  background: var(--ih-c-sidebar-now);
  color: var(--ih-c-text);
  font-weight: 500;
  font-size: 1.2rem;
}

/* hover 写在激活态之后：两者优先级相同，靠后的规则生效，从而覆盖激活样式 */
.sidebar__link:hover {
  background: var(--ih-c-hover);
  color: var(--ih-c-text);
  transition:
    font-size 0.15s;
}
</style>
