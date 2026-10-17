<script setup lang="ts">
const sections = [
  { to: '/admin/users', icon: '◎', title: '用户管理', description: '调整用户角色、启用状态并强制下线。' },
  { to: '/admin/categories', icon: '▦', title: '服务器管理', description: '创建、重命名和删除反馈所属的服务器。' },
  { to: '/admin/tags', icon: '◇', title: '标签管理', description: '维护用于反馈筛选和归类的全局标签。' },
  { to: '/admin/config', icon: '⚙', title: '站点配置', description: '管理注册、邮件、会话和站点展示配置。' },
]
</script>

<template>
  <main class="form-page admin-dashboard">
    <section class="form-card">
      <h1>管理面板</h1>
      <p>集中管理 SimpIssueHub 的用户、服务器和运行配置。</p>
      <div class="admin-dashboard-grid">
        <RouterLink v-for="section in sections" :key="section.to" :to="section.to" class="admin-dashboard-card">
          <span aria-hidden="true">{{ section.icon }}</span>
          <div><strong>{{ section.title }}</strong><p>{{ section.description }}</p></div>
        </RouterLink>
      </div>
    </section>
  </main>
</template>

<style scoped>
.admin-dashboard-grid { display: grid; gap: 12px; margin-top: 20px; }
.admin-dashboard-card { display: flex; gap: 14px; padding: 16px; border: 1px solid var(--border-default); border-radius: 8px; color: inherit; text-decoration: none; }
.admin-dashboard-card:hover { background: var(--canvas-subtle); }
.admin-dashboard-card span { font-size: 24px; }
.admin-dashboard-card p { margin: 5px 0 0; color: var(--fg-muted); }
</style>
