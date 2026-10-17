<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppSelect from '../components/AppSelect.vue'
import { fetchCategories, fetchIssueCounts, fetchTags, getApiErrorMessage, getApiErrorStatus, type CategoryResponse, type TagResponse } from '../api/http'
import { useIssueStore, type Issue, type IssueState } from '../stores/issues'
import { useAuthStore } from '../stores/auth'

const activeFilter = ref<'ALL' | IssueState>('OPEN')
const search = ref('')
const author = ref('')
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
author.value = auth.user?.username ?? ''
const issueCounts = ref({ all: 0, open: 0, closed: 0 })
const countsError = ref('')
const selectedTag = computed(() => typeof route.query.tag === 'string' ? route.query.tag : '')
const selectedCategory = computed(() => typeof route.query.category === 'string' ? route.query.category : '')
const categories = ref<CategoryResponse[]>([])
const tags = ref<TagResponse[]>([])
const categoryOptions = computed(() => [{ value: '', label: '全部服务器' }, ...categories.value.map((item) => ({ value: item.name, label: item.name }))])
const tagOptions = computed(() => [{ value: '', label: '全部标签' }, ...tags.value.map((item) => ({ value: item.name, label: `${item.name} (${item.issueCount})` }))])
let searchTimer: ReturnType<typeof setTimeout> | undefined
let countsTimer: ReturnType<typeof setTimeout> | undefined
let countsRequestId = 0
watch([selectedTag, selectedCategory, activeFilter, search, author, sort], ([tag, category, state, query, byAuthor, ordering]) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    void store.loadFromApi({ tag: tag || undefined, category: category || undefined, state: state === 'ALL' ? undefined : state, query: query.trim() || undefined, author: byAuthor.trim() || undefined, sort: ordering })
  }, 300)
}, { immediate: true })
onBeforeUnmount(() => { if (searchTimer) clearTimeout(searchTimer) })
async function loadIssueCounts() {
  const requestId = ++countsRequestId
  countsError.value = ''
  try {
    const counts = await fetchIssueCounts({ tag: selectedTag.value || undefined, category: selectedCategory.value || undefined, query: search.value.trim() || undefined, author: author.value.trim() || undefined })
    if (requestId === countsRequestId) issueCounts.value = counts
  }
  catch (requestError: unknown) { if (requestId === countsRequestId) countsError.value = getApiErrorMessage(requestError, '反馈统计暂时不可用。') }
}

watch([selectedTag, selectedCategory, search, author], () => {
  if (countsTimer) clearTimeout(countsTimer)
  countsTimer = setTimeout(() => { void loadIssueCounts() }, 300)
}, { immediate: true })
onBeforeUnmount(() => { if (countsTimer) clearTimeout(countsTimer) })
onMounted(async () => {
  const [categoryResult, tagResult] = await Promise.allSettled([fetchCategories(), fetchTags()])
  categories.value = categoryResult.status === 'fulfilled' ? categoryResult.value : []
  tags.value = tagResult.status === 'fulfilled' ? tagResult.value : []
})

const visibleIssues = computed(() => store.issues)

function changePage(page: number) {
  void store.goToPage(page, currentListOptions())
}

function currentListOptions() {
  return { tag: selectedTag.value || undefined, category: selectedCategory.value || undefined, state: activeFilter.value === 'ALL' ? undefined : activeFilter.value, query: search.value.trim() || undefined, author: author.value.trim() || undefined, sort: sort.value }
}

async function retryIssueList() {
  await store.loadFromApi(currentListOptions())
}

async function toggleState(issue: Issue) {
  stateActionId.value = issue.id; stateActionError.value = ''
  try {
    if (issue.state === 'OPEN') await store.closeFromApi(issue.id)
    else await store.reopenFromApi(issue.id)
  } catch (requestError: unknown) {
    const status = getApiErrorStatus(requestError)
    stateActionError.value = status === 401 ? '请先登录后再修改状态。' : status === 403 ? '你没有权限修改这条反馈的状态。' : status === 409 ? '反馈状态已变化，请刷新后重试。' : getApiErrorMessage(requestError, '状态修改失败，请稍后重试。')
    stateActionId.value = null
    return
  }
    try {
    await loadIssueCounts()
    await store.loadFromApi(currentListOptions())
    if (store.loadError) stateActionError.value = `状态已更新，但列表刷新失败：${store.loadError}`
  } catch (requestError: unknown) {
    stateActionError.value = `状态已更新，但列表刷新失败：${getApiErrorMessage(requestError, '请稍后重试。')}`
  } finally { stateActionId.value = null }
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
    <aside class="issues-sidebar" aria-label="反馈导航">
      <div class="sidebar-section">
        <button class="sidebar-item active" type="button" @click="activeFilter = 'OPEN'; author = ''"><span aria-hidden="true">◉</span><strong>反馈</strong></button>
        <button v-if="auth.user" class="sidebar-item" type="button" @click="author = auth.user.username"><span aria-hidden="true">◎</span>由我创建</button>
        <button class="sidebar-item" type="button" @click="sort = 'updatedDesc'"><span aria-hidden="true">◷</span>最近活动</button>
      </div>
      <div class="sidebar-section">
        <span class="sidebar-heading">浏览</span>
        <button class="sidebar-item" type="button" @click="router.push({ query: { ...route.query, tag: undefined } })"><span aria-hidden="true">◇</span>全部标签</button>
        <button v-for="category in categories.slice(0, 6)" :key="category.id" class="sidebar-item" type="button" @click="router.push({ query: { ...route.query, category: category.name } })"><span aria-hidden="true">▦</span>{{ category.name }}<small>{{ category.issueCount }}</small></button>
      </div>
    </aside>

    <section class="issues-main">
      <header class="issues-page-header">
        <div><h1>{{ author ? '我的反馈' : '所有反馈' }}</h1><p>跟踪玩家报告、建议和服务器问题。</p></div>
        <button class="primary-button" type="button" @click="router.push('/issues/new')">新建反馈</button>
      </header>

      <div class="issue-search-bar">
        <label class="search"><span aria-hidden="true">⌕</span><input v-model="search" type="search" maxlength="200" placeholder="搜索所有反馈" /></label>
        <label class="author-filter"><span aria-hidden="true">@</span><input v-model="author" type="search" maxlength="32" placeholder="作者" /></label>
        <AppSelect class="category-filter" :model-value="selectedCategory" :options="categoryOptions" ariaLabel="按所属服务器筛选" @update:model-value="(value) => router.push({ query: { ...route.query, category: value || undefined } })" />
        <AppSelect class="category-filter" :model-value="selectedTag" :options="tagOptions" ariaLabel="按标签筛选" @update:model-value="(value) => router.push({ query: { ...route.query, tag: value || undefined } })" />
      </div>

      <p v-if="countsError" class="list-status warning">{{ countsError }} <button type="button" class="inline-action" @click="loadIssueCounts">重试</button></p>
      <p v-if="store.loadError" class="list-status warning">{{ store.loadError }} <button type="button" class="inline-action" @click="retryIssueList">重试</button></p>
      <p v-else-if="selectedTag || selectedCategory || author" class="list-status tag-filter-status">筛选：<span v-if="selectedTag" class="tag">{{ selectedTag }}</span><span v-if="selectedCategory" class="tag category-badge">{{ selectedCategory }}</span><span v-if="author" class="tag">@{{ author }}</span><button type="button" @click="author = ''; router.push('/')">清除筛选</button></p>
      <p v-if="stateActionError" class="list-status warning">{{ stateActionError }}</p>

      <section class="issue-list" aria-live="polite">
        <header class="issue-list-header">
          <div class="filters">
            <button :class="{ selected: activeFilter === 'OPEN' }" @click="activeFilter = 'OPEN'"><span class="filter-icon" aria-hidden="true">◉</span>处理中 <strong>{{ issueCounts.open }}</strong></button>
            <button :class="{ selected: activeFilter === 'CLOSED' }" @click="activeFilter = 'CLOSED'"><span class="filter-icon" aria-hidden="true">✓</span>已解决 <strong>{{ issueCounts.closed }}</strong></button>
            <button :class="{ selected: activeFilter === 'ALL' }" @click="activeFilter = 'ALL'">全部 <strong>{{ issueCounts.all }}</strong></button>
          </div>
          <AppSelect v-model="sort" class="sort-filter" :options="sortOptions" ariaLabel="排序方式" />
        </header>
        <p v-if="store.loading" class="empty">正在加载反馈…</p>
        <article v-for="issue in visibleIssues" v-else :key="issue.id" class="issue-row" tabindex="0" @click="openIssue(issue)" @keydown.enter="openIssue(issue)">
          <span class="issue-state-icon" :class="issue.state.toLowerCase()" aria-hidden="true">{{ issue.state === 'OPEN' ? '◉' : '✓' }}</span>
          <div class="issue-main">
            <div class="issue-heading"><h2><span v-if="issue.pinned" class="pinned-badge" title="置顶反馈">⌖ 置顶</span>{{ issue.title }}</h2><span class="tag category-badge">{{ issue.category }}</span><template v-if="issue.tags.length"><span v-for="tag in issue.tags" :key="tag.id" class="tag" :style="{ color: tag.color }">{{ tag.name }}</span></template><span v-else class="tag">{{ issue.tag }}</span></div>
            <small>#{{ issue.id }} 由 <strong>{{ issue.author }}</strong> 提交 · 更新于 {{ issue.updatedAt }}</small>
          </div>
          <span v-if="issue.pollParticipants" class="poll-count" title="投票参与人数" aria-label="投票参与人数">▥ {{ issue.pollParticipants }}</span>
          <button v-if="canChangeState(issue)" class="state-button" :disabled="stateActionId === issue.id" @click.stop="toggleState(issue)">{{ stateActionId === issue.id ? '处理中…' : issue.state === 'OPEN' ? '关闭' : '重新打开' }}</button>
        </article>
        <div v-if="store.loadError" class="empty server-unavailable" role="alert"><strong>咻~服务器飞出了银河系~</strong><a href="https://doc.simpmc.org" target="_blank" rel="noopener noreferrer">点我和腐竹一起去追服务器~</a></div>
        <div v-else-if="!store.loading && visibleIssues.length === 0" class="empty">没有匹配的反馈</div>
      </section>

      <footer><span>第 {{ store.currentPage + 1 }} / {{ store.totalPages }} 页</span><div class="pagination"><button type="button" :disabled="store.currentPage === 0 || store.loading" @click="changePage(store.currentPage - 1)">上一页</button><button type="button" :disabled="store.currentPage >= store.totalPages - 1 || store.loading" @click="changePage(store.currentPage + 1)">下一页</button></div></footer>
    </section>
  </main>
</template>
