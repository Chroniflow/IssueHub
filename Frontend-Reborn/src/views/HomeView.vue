<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DropdownSelect from '@/components/DropdownSelect.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import HorizontalSwitch from '@/components/HorizontalSwitch.vue'
import { fetchIssueCounts, getApiErrorMessage, getApiErrorStatus } from '@/api/http'
import { useIssueStore, type Issue, type IssueState } from '@/stores/issues'
import { useAuthStore } from '@/stores/auth'

const activeFilter = ref<'ALL' | IssueState>('OPEN')
const sort = ref<'updatedDesc' | 'updatedAsc' | 'createdDesc' | 'createdAsc'>('updatedDesc')
const sortOptions = [
  { value: 'updatedDesc', label: '最近更新' },
  { value: 'updatedAsc', label: '最早更新' },
  { value: 'createdDesc', label: '最新创建' },
  { value: 'createdAsc', label: '最早创建' },
]
const stateActionId = ref<number | null>(null)
const stateActionError = ref('')
const router = useRouter()
const route = useRoute()
const store = useIssueStore()
const auth = useAuthStore()
const issueCounts = ref({ all: 0, open: 0, closed: 0 })
const countsError = ref('')
const selectedTag = computed(() => (typeof route.query.tag === 'string' ? route.query.tag : ''))
const selectedCategory = computed(() =>
  typeof route.query.category === 'string' ? route.query.category : '',
)
const search = computed(() => (typeof route.query.query === 'string' ? route.query.query : ''))
const denied = computed(() => route.query.denied === 'admin')
const filterSwitchValue = computed({
  get: () => activeFilter.value,
  set: (value: string) => {
    activeFilter.value = value === 'CLOSED' || value === 'ALL' ? value : 'OPEN'
  },
})
const filterSwitchOptions = computed(() => [
  { value: 'OPEN', label: `处理中 ${issueCounts.value.open}`, icon: '◉' },
  { value: 'CLOSED', label: `已解决 ${issueCounts.value.closed}`, icon: '✓' },
  { value: 'ALL', label: `全部 ${issueCounts.value.all}`, icon: '☰' },
])
let searchTimer: ReturnType<typeof setTimeout> | undefined
let countsTimer: ReturnType<typeof setTimeout> | undefined
let countsRequestId = 0

watch(
  [selectedTag, selectedCategory, activeFilter, search, sort],
  ([tag, category, state, query, ordering]) => {
    if (searchTimer) clearTimeout(searchTimer)
    searchTimer = setTimeout(() => {
      void store.loadFromApi({
        tag: tag || undefined,
        category: category || undefined,
        state: state === 'ALL' ? undefined : state,
        query: query.trim() || undefined,
        sort: ordering,
      })
    }, 300)
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
})

async function loadIssueCounts() {
  const requestId = ++countsRequestId
  countsError.value = ''
  try {
    const counts = await fetchIssueCounts({
      tag: selectedTag.value || undefined,
      category: selectedCategory.value || undefined,
      query: search.value.trim() || undefined,
    })
    if (requestId === countsRequestId) issueCounts.value = counts
  } catch (requestError: unknown) {
    if (requestId === countsRequestId)
      countsError.value = getApiErrorMessage(requestError, '反馈统计暂时不可用。')
  }
}

watch(
  [selectedTag, selectedCategory, search],
  () => {
    if (countsTimer) clearTimeout(countsTimer)
    countsTimer = setTimeout(() => {
      void loadIssueCounts()
    }, 300)
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  if (countsTimer) clearTimeout(countsTimer)
})

const visibleIssues = computed(() => store.issues)

function changePage(page: number) {
  void store.goToPage(page, currentListOptions())
}

function currentListOptions() {
  return {
    tag: selectedTag.value || undefined,
    category: selectedCategory.value || undefined,
    state: activeFilter.value === 'ALL' ? undefined : activeFilter.value,
    query: search.value.trim() || undefined,
    sort: sort.value,
  }
}

function clearFilters() {
  void router.push('/')
}

async function retryIssueList() {
  await store.loadFromApi(currentListOptions())
}

async function toggleState(issue: Issue) {
  stateActionId.value = issue.id
  stateActionError.value = ''
  try {
    if (issue.state === 'OPEN') await store.closeFromApi(issue.id)
    else await store.reopenFromApi(issue.id)
  } catch (requestError: unknown) {
    const status = getApiErrorStatus(requestError)
    stateActionError.value =
      status === 401
        ? '请先登录后再修改状态。'
        : status === 403
          ? '你没有权限修改这条反馈的状态。'
          : status === 409
            ? '反馈状态已变化，请刷新后重试。'
            : getApiErrorMessage(requestError, '状态修改失败，请稍后重试。')
    stateActionId.value = null
    return
  }
  try {
    await loadIssueCounts()
    await store.loadFromApi(currentListOptions())
    if (store.loadError) stateActionError.value = `状态已更新，但列表刷新失败：${store.loadError}`
  } catch (requestError: unknown) {
    stateActionError.value = `状态已更新，但列表刷新失败：${getApiErrorMessage(requestError, '请稍后重试。')}`
  } finally {
    stateActionId.value = null
  }
}

function canChangeState(issue: Issue) {
  const user = auth.user
  if (!user) return false
  return issue.state === 'OPEN'
    ? user.role !== 'USER' || user.username === issue.author
    : user.role === 'STAFF' || user.role === 'ADMIN'
}

function openIssue(issue: Issue) {
  router.push('/issues/' + issue.id)
}
</script>

<template>
  <main class="issues-workspace">
    <section class="issues-main">
      <header class="issues-page-header">
        <div>
          <h1>所有反馈</h1>
          <p>跟踪玩家报告、建议和服务器问题。</p>
        </div>
        <button v-ripple class="primary-button" type="button" @click="router.push('/issues/new')">
          新建反馈
        </button>
      </header>

      <p v-if="denied" class="list-status warning">该页面需要管理员权限，已返回反馈列表。</p>

      <p v-if="countsError" class="list-status warning">
        {{ countsError }}
        <button type="button" class="inline-action" @click="loadIssueCounts">重试</button>
      </p>
      <p v-if="store.loadError" class="list-status warning">
        {{ store.loadError }}
        <button type="button" class="inline-action" @click="retryIssueList">重试</button>
      </p>
      <p
        v-else-if="selectedTag || selectedCategory || search"
        class="list-status tag-filter-status"
      >
        筛选：<span v-if="selectedTag" class="tag">{{ selectedTag }}</span
        ><span v-if="selectedCategory" class="tag category-badge">{{ selectedCategory }}</span
        ><span v-if="search" class="tag">⌕ {{ search }}</span
        ><button type="button" @click="clearFilters">清除筛选</button>
      </p>
      <p v-if="stateActionError" class="list-status warning">{{ stateActionError }}</p>

      <section class="issue-list" aria-live="polite">
        <header class="issue-list-header">
          <HorizontalSwitch v-model="filterSwitchValue" :options="filterSwitchOptions" />
          <DropdownSelect
            v-model="sort"
            class="sort-filter"
            :options="sortOptions"
            ariaLabel="排序方式"
          />
        </header>
        <p v-if="store.loading" class="empty">正在加载反馈…</p>
        <article
          v-for="issue in visibleIssues"
          v-else
          :key="issue.id"
          v-ripple
          class="issue-row"
          tabindex="0"
          @click="openIssue(issue)"
          @keydown.enter="openIssue(issue)"
        >
          <span class="issue-state-icon" :class="issue.state.toLowerCase()" aria-hidden="true">{{
            issue.state === 'OPEN' ? '◉' : '✓'
          }}</span>
          <UserAvatar :username="issue.author" :url="issue.authorAvatarUrl" size="small" />
          <div class="issue-main">
            <div class="issue-heading">
              <h2>{{ issue.title }}</h2>
              <span class="tag category-badge">{{ issue.category }}</span
              ><template v-if="issue.tags.length"
                ><span
                  v-for="tag in issue.tags"
                  :key="tag.id"
                  class="tag"
                  :style="{ color: tag.color }"
                  >{{ tag.name }}</span
                ></template
              ><span v-else class="tag">{{ issue.tag }}</span>
            </div>
            <small
              >#{{ issue.id }} 由 <strong>{{ issue.author }}</strong> 提交 · 更新于
              {{ issue.updatedAt }}</small
            >
          </div>
          <span
            v-if="issue.pollParticipants"
            class="poll-count"
            title="投票参与人数"
            aria-label="投票参与人数"
            >▥ {{ issue.pollParticipants }}</span
          >
          <button
            v-if="canChangeState(issue)"
            v-ripple
            class="state-button"
            :disabled="stateActionId === issue.id"
            @click.stop="toggleState(issue)"
          >
            {{
              stateActionId === issue.id
                ? '处理中…'
                : issue.state === 'OPEN'
                  ? '关闭'
                  : '重新打开'
            }}
          </button>
        </article>
        <div v-if="store.loadError" class="empty server-unavailable" role="alert">
          <strong>咻~服务器飞出了银河系~</strong>
          <a href="https://doc.simpmc.org" target="_blank" rel="noopener noreferrer"
            >点我和腐竹一起去追服务器~</a
          >
        </div>
        <div v-else-if="!store.loading && visibleIssues.length === 0" class="empty">
          没有匹配的反馈
        </div>
      </section>

      <footer>
        <span>第 {{ store.currentPage + 1 }} / {{ store.totalPages }} 页</span>
        <div class="pagination">
          <button
            v-ripple
            type="button"
            :disabled="store.currentPage === 0 || store.loading"
            @click="changePage(store.currentPage - 1)"
          >
            上一页
          </button>
          <button
            v-ripple
            type="button"
            :disabled="store.currentPage >= store.totalPages - 1 || store.loading"
            @click="changePage(store.currentPage + 1)"
          >
            下一页
          </button>
        </div>
      </footer>
    </section>
  </main>
</template>
