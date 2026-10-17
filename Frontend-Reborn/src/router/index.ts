import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    requiresAdmin?: boolean
    title?: string
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: HomeView, meta: { title: '反馈' } },
    {
      path: '/issues/new',
      name: 'issue-create',
      component: () => import('@/views/IssueCreateView.vue'),
      meta: { requiresAuth: true, title: '新建反馈' },
    },
    {
      path: '/issues/:id',
      name: 'issue-detail',
      component: () => import('@/views/IssueDetailView.vue'),
      meta: { title: '反馈详情' },
    },
    { path: '/tags', name: 'tags', component: () => import('@/views/TagsView.vue'), meta: { title: '标签' } },
    { path: '/about', name: 'about', component: () => import('@/views/AboutView.vue'), meta: { title: '关于' } },
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { title: '登录' } },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { title: '创建账号' },
    },
    {
      path: '/reset-password',
      name: 'password-reset',
      component: () => import('@/views/PasswordResetView.vue'),
      meta: { title: '找回密码' },
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('@/views/SettingsView.vue'),
      meta: { title: '设置' },
    },
    {
      path: '/account/security',
      name: 'account-security',
      component: () => import('@/views/SecurityView.vue'),
      meta: { requiresAuth: true, title: '账户安全' },
    },
    {
      path: '/account/tokens',
      name: 'account-tokens',
      component: () => import('@/views/ApiTokensView.vue'),
      meta: { requiresAuth: true, title: 'API Token' },
    },
    {
      path: '/admin/users',
      name: 'admin-users',
      component: () => import('@/views/AdminUsersView.vue'),
      meta: { requiresAdmin: true, title: '用户管理' },
    },
    {
      path: '/admin/categories',
      name: 'admin-categories',
      component: () => import('@/views/AdminCategoriesView.vue'),
      meta: { requiresAdmin: true, title: '分类管理' },
    },
    {
      path: '/admin/config',
      name: 'admin-config',
      component: () => import('@/views/AdminConfigView.vue'),
      meta: { requiresAdmin: true, title: '站点配置' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: '页面不存在' },
    },
  ],
})

export default router
