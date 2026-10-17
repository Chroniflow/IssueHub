import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { vRipple } from './directives/ripple'
import { useAuthStore } from './stores/auth'
import { http } from './api/http'
import { loadSiteConfig } from './siteConfig'

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
        await router.replace({
          path: '/login',
          query: { sessionExpired: '1', redirect: current.fullPath },
        })
      }
    }
    return Promise.reject(error)
  },
)

router.beforeEach(async (to) => {
  await auth.initialize()
  if (to.meta.requiresAuth && !auth.user)
    return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.requiresAdmin && auth.user?.role !== 'ADMIN')
    return { path: '/', query: { denied: 'admin' } }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title
  document.title = title ? `SimpIssueHub | ${title}` : 'SimpIssueHub'
})

void loadSiteConfig()

const app = createApp(App)

app.use(pinia)
app.use(router)
app.directive('ripple', vRipple)

app.mount('#app')
