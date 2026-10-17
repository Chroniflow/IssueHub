<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  fetchAdminUsers,
  getApiErrorMessage,
  kickoutAdminUser,
  updateAdminUser,
  type AdminUserResponse,
} from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { formatLocalDateTime } from '@/utils/format'
import UserAvatar from '@/components/UserAvatar.vue'

const router = useRouter()
const auth = useAuthStore()
const users = ref<AdminUserResponse[]>([])
const loading = ref(true)
const error = ref('')
const notice = ref('')
const saving = ref<number | null>(null)
const confirmingKickout = ref<number | null>(null)
const currentPage = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)

async function loadPage(page: number) {
  loading.value = true
  error.value = ''
  confirmingKickout.value = null
  try {
    const result = await fetchAdminUsers({ page, size: 20 })
    users.value = result.content
    currentPage.value = result.number
    totalPages.value = result.totalPages
    totalElements.value = result.totalElements
  } catch (requestError: unknown) {
    error.value = getApiErrorMessage(requestError, '无法加载用户列表。')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await auth.initialize()
  if (auth.user?.role !== 'ADMIN') {
    await router.replace('/')
    return
  }
  await loadPage(0)
})

async function update(user: AdminUserResponse, role: 'USER' | 'STAFF' | undefined, enabled: boolean) {
  saving.value = user.id
  confirmingKickout.value = null
  error.value = ''
  notice.value = ''
  try {
    const saved = await updateAdminUser(user.id, { ...(role ? { role } : {}), enabled })
    users.value = users.value.map((item) => (item.id === saved.id ? saved : item))
  } catch (requestError: unknown) {
    error.value = getApiErrorMessage(requestError, '用户更新失败。')
  } finally {
    saving.value = null
  }
}

async function kickout(user: AdminUserResponse) {
  if (confirmingKickout.value !== user.id) {
    confirmingKickout.value = user.id
    return
  }
  saving.value = user.id
  error.value = ''
  notice.value = ''
  try {
    await kickoutAdminUser(user.id)
    confirmingKickout.value = null
    notice.value = `已强制 ${user.username} 的登录会话下线。`
  } catch (requestError: unknown) {
    error.value = getApiErrorMessage(requestError, '强制下线失败。')
  } finally {
    saving.value = null
  }
}
</script>

<template>
  <main class="form-page">
    <button v-ripple class="back-link" @click="router.push('/')">← 返回反馈列表</button>
    <section class="form-panel admin-users">
      <p class="eyebrow">ADMINISTRATION</p>
      <h1>用户管理</h1>
      <p class="form-lede">管理工作人员权限、账号状态和登录会话。</p>
      <p v-if="loading" class="list-status">正在加载用户…</p>
      <p v-if="error" class="list-status warning" role="alert">
        {{ error }}
        <button type="button" class="inline-action" :disabled="loading" @click="loadPage(currentPage)">
          重试
        </button>
      </p>
      <p v-if="notice" class="form-message" role="status">{{ notice }}</p>
      <div v-if="!loading && users.length" class="admin-user-list">
        <div v-for="user in users" :key="user.id" class="admin-user-row">
          <div class="admin-user-identity">
            <UserAvatar :username="user.username" :url="user.avatarUrl" size="medium" />
            <div>
              <strong>{{ user.username }}</strong>
              <small
                >{{ user.email }} · {{ user.role }} ·
                {{ user.enabled ? '已启用' : '已停用' }} · 创建于
                {{ formatLocalDateTime(user.createdAt) }}</small
              >
            </div>
          </div>
          <div class="admin-user-actions">
            <template v-if="user.id !== auth.user?.id">
              <button
                type="button"
                :disabled="saving === user.id"
                @click="update(user, user.role === 'STAFF' ? 'USER' : 'STAFF', user.enabled)"
              >
                {{
                  user.role === 'USER'
                    ? '提升为工作人员'
                    : user.role === 'ADMIN'
                      ? '降为工作人员'
                      : '降为普通用户'
                }}
              </button>
              <button type="button" :disabled="saving === user.id" @click="update(user, undefined, !user.enabled)">
                {{ user.enabled ? '停用' : '启用' }}
              </button>
            </template>
            <template v-if="user.id !== auth.user?.id && user.enabled">
              <template v-if="confirmingKickout === user.id">
                <span class="admin-user-confirm">确认强制下线？</span>
                <button type="button" :disabled="saving === user.id" @click="kickout(user)">
                  {{ saving === user.id ? '处理中…' : '确认' }}
                </button>
                <button type="button" :disabled="saving === user.id" @click="confirmingKickout = null">
                  取消
                </button>
              </template>
              <button v-else type="button" :disabled="saving === user.id" @click="kickout(user)">
                强制下线
              </button>
            </template>
            <span v-if="user.id === auth.user?.id" class="admin-badge">当前账号</span>
          </div>
        </div>
      </div>
      <p v-else-if="!loading && !error" class="no-comments">暂无用户。</p>
      <div v-if="!loading && totalElements > 0" class="admin-pagination">
        <span>共 {{ totalElements }} 位用户 · 第 {{ currentPage + 1 }} / {{ totalPages }} 页</span>
        <div class="pagination" aria-label="用户列表分页">
          <button v-ripple type="button" :disabled="currentPage === 0" @click="loadPage(currentPage - 1)">
            上一页
          </button>
          <button
            v-ripple
            type="button"
            :disabled="currentPage >= totalPages - 1"
            @click="loadPage(currentPage + 1)"
          >
            下一页
          </button>
        </div>
      </div>
    </section>
  </main>
</template>
