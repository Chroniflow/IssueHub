import { defineStore } from 'pinia'
import { fetchCurrentUser, getApiErrorMessage, loginUser, logoutUser, registerUser, type UserResponse } from '../api/http'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null as UserResponse | null,
    loading: false,
    error: '',
    initialized: false,
  }),
  getters: {
    isAuthenticated: (state) => state.user !== null,
  },
  actions: {
    async initialize() {
      if (this.initialized) return
      try { this.user = await fetchCurrentUser() } catch { this.user = null }
      this.initialized = true
    },
    async login(username: string, password: string, turnstileToken?: string) {
      this.loading = true
      this.error = ''
      try {
        this.user = await loginUser({ username, password, turnstileToken })
      } catch (error: unknown) {
        this.error = getApiErrorMessage(error, '登录失败，请检查账号和密码。')
        throw error
      } finally { this.loading = false }
    },
    async register(username: string, email: string, password: string, verificationCode?: string, turnstileToken?: string) {
      this.loading = true
      this.error = ''
      try {
        this.user = await registerUser({ username, email, password, verificationCode, turnstileToken })
      } catch (error: unknown) {
        this.error = getApiErrorMessage(error, '注册失败，请稍后重试。')
        throw error
      } finally { this.loading = false }
    },
    async logout() {
      try {
        await logoutUser()
      } finally {
        this.user = null
        this.initialized = true
      }
    },
  },
})
