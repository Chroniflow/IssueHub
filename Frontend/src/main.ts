import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import HomeView from './views/HomeView.vue'
import IssueDetail from './views/IssueDetail.vue'
import IssueCreate from './views/IssueCreate.vue'
import LoginView from './views/LoginView.vue'
import RegisterView from './views/RegisterView.vue'
import TagsView from './views/TagsView.vue'
import AboutView from './views/AboutView.vue'
import AdminUsersView from './views/AdminUsersView.vue'
import AdminCategoriesView from './views/AdminCategoriesView.vue'
import AdminConfigView from './views/AdminConfigView.vue'
import ApiTokensView from './views/ApiTokensView.vue'
import SecurityView from './views/SecurityView.vue'
import PasswordResetView from './views/PasswordResetView.vue'
import NotFoundView from './views/NotFoundView.vue'
import AdminDashboardView from './views/AdminDashboardView.vue'
import PollsView from './views/PollsView.vue'
import { useAuthStore } from './stores/auth'
import { http } from './api/http'
import { loadSiteConfig } from './siteConfig'
import './styles.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: HomeView, meta: { requiresAuth: true, title: '反馈' } },
    { path: '/login', component: LoginView, meta: { title: '登录' } },
    { path: '/register', component: RegisterView, meta: { title: '创建账号' } },
    { path: '/reset-password', component: PasswordResetView, meta: { title: '找回密码' } },
    { path: '/issues/new', component: IssueCreate, meta: { requiresAuth: true, title: '新建反馈' } },
    { path: '/issues/:id', component: IssueDetail, meta: { requiresAuth: true, title: '反馈详情' } },
    { path: '/tags', redirect: '/' },
    { path: '/about', component: AboutView, meta: { title: '关于' } },
    { path: '/polls', component: PollsView, meta: { requiresAuth: true, title: '投票' } },
    { path: '/admin', component: AdminDashboardView, meta: { requiresAdmin: true, title: '管理面板' } },
    { path: '/admin/users', component: AdminUsersView, meta: { requiresAdmin: true, title: '用户管理' } },
    { path: '/admin/categories', component: AdminCategoriesView, meta: { requiresAdmin: true, title: '服务器管理' } },
    { path: '/admin/config', component: AdminConfigView, meta: { requiresAdmin: true, title: '站点配置' } },
    { path: '/admin/tags', component: TagsView, meta: { requiresAdmin: true, title: '标签管理' } },
    { path: '/account/tokens', component: ApiTokensView, meta: { requiresAuth: true, title: 'API Token' } },
    { path: '/account/security', component: SecurityView, meta: { requiresAuth: true, title: '账户安全' } },
    { path: '/:pathMatch(.*)*', component: NotFoundView, meta: { title: '页面不存在' } },
  ],
})

const pinia = createPinia()
const auth = useAuthStore(pinia)
http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error?.response?.status
    const url = String(error?.config?.url ?? '')
    const authenticationRequest = url.startsWith('/auth/')
    if (status === 401 && !authenticationRequest && auth.user) {
      auth.user = null
      auth.initialized = true
      const current = router.currentRoute.value
      if (current.meta.requiresAuth || current.meta.requiresAdmin) {
        await router.replace({ path: '/login', query: { sessionExpired: '1', redirect: current.fullPath } })
      }
    }
    return Promise.reject(error)
  },
)
router.beforeEach(async (to) => {
  await auth.initialize()
  if (to.meta.requiresAuth && !auth.user) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.requiresAdmin && auth.user?.role !== 'ADMIN') return { path: '/', query: { denied: 'admin' } }
  return true
})
router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : ''
  document.title = title ? `SimpIssueHub | ${title}` : 'SimpIssueHub'
})

void loadSiteConfig()
createApp(App).use(pinia).use(router).mount('#app')
