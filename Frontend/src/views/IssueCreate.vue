<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppSelect from '../components/AppSelect.vue'
import { fetchTags, fetchCategories, getApiErrorMessage, getApiErrorStatus } from '../api/http'
import { useIssueStore } from '../stores/issues'
import { hasTextWithin } from '../utils/format'

const router = useRouter()
const store = useIssueStore()
const title = ref('')
const body = ref('')
const tag = ref('反馈')
const category = ref('其他')
const categoryOptions = ref<Array<{ value: string; label: string }>>([])
const categoryLoading = ref(true)
const categoryLoadError = ref('')
const error = ref('')
const submitting = ref(false)
const idempotencyKey = ref(createIdempotencyKey())
const fallbackTagOptions = [
  { value: '反馈', label: '反馈' },
  { value: '建议', label: '建议' },
  { value: 'bug', label: 'Bug' },
]
const tagOptions = ref(fallbackTagOptions)
const tagLoading = ref(true)
const tagLoadError = ref('')

async function loadTagOptions() {
  tagLoading.value = true
  tagLoadError.value = ''
  try {
    const tags = await fetchTags()
    if (tags.length) {
      tagOptions.value = tags.map((item) => ({ value: item.name, label: item.name }))
      if (!tags.some((item) => item.name === tag.value) && tags[0]) tag.value = tags[0].name
    }
  } catch (requestError: unknown) {
    tagLoadError.value = `${getApiErrorMessage(requestError, '标签列表暂时不可用')}仍可使用默认类型。`
  } finally {
    tagLoading.value = false
  }
}

async function loadCategoryOptions() {
  categoryLoading.value = true
  categoryLoadError.value = ''
  try {
    const categories = await fetchCategories()
    categoryOptions.value = categories.map((item) => ({ value: item.name, label: item.name }))
    if (categories.length && !categories.some((item) => item.name === category.value)) category.value = categories[0]?.name ?? ''
    if (!categories.length) categoryLoadError.value = '暂无可用服务器，请联系管理员配置。'
  } catch (requestError: unknown) {
    categoryLoadError.value = getApiErrorMessage(requestError, '服务器列表暂时不可用，请稍后重试。')
  } finally {
    categoryLoading.value = false
  }
}

onMounted(() => { void loadTagOptions(); void loadCategoryOptions() })

async function submit() {
  if (!hasTextWithin(title.value, 1, 120)) {
    error.value = '标题长度必须为 1-120 个字符。'
    return
  }
  if (!hasTextWithin(body.value, 1, 20000)) {
    error.value = '具体内容长度必须为 1-20000 个字符。'
    return
  }
  if (categoryLoading.value || !category.value || categoryOptions.value.length === 0) {
    error.value = '请选择反馈所属的服务器。'
    return
  }
  submitting.value = true
  error.value = ''
  try {
    const id = await store.createFromApi({ title: title.value.trim(), body: body.value.trim(), tag: tag.value, category: category.value }, idempotencyKey.value)
    idempotencyKey.value = createIdempotencyKey()
    router.push(`/issues/${id}`)
  } catch (requestError: unknown) {
    error.value = getApiErrorStatus(requestError) === 401 ? '请先登录后再提交反馈。' : getApiErrorMessage(requestError, '提交失败，请稍后重试。')
  } finally {
    submitting.value = false
  }
}


function createIdempotencyKey() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `sih-${Date.now()}-${Math.random().toString(36).slice(2)}`
}
</script>

<template>
  <main class="form-page">
    <button class="back-link" @click="router.push('/')">← 返回反馈列表</button>
    <section class="form-panel">
      <p class="eyebrow">NEW ISSUE</p><h1>创建反馈</h1><p class="form-lede">请提供足够的信息，方便工作人员理解和复现问题。</p>
      <form @submit.prevent="submit">
        <label>标题<input v-model="title" maxlength="120" placeholder="用一句话描述问题或建议" /></label>
        <label>类型<AppSelect v-model="tag" :options="tagOptions" :disabled="tagLoading" ariaLabel="反馈类型" /></label>
        <label>属于的服务器<AppSelect v-model="category" :options="categoryOptions" :disabled="categoryLoading || categoryOptions.length === 0" ariaLabel="反馈所属的服务器" /></label>
        <p v-if="categoryLoadError" class="form-hint">{{ categoryLoadError }} <button type="button" class="inline-action" @click="loadCategoryOptions">重试</button></p>
        <p v-if="tagLoadError" class="form-hint">{{ tagLoadError }} <button type="button" class="inline-action" @click="loadTagOptions">重试</button></p>
        <label>具体内容<textarea v-model="body" rows="9" placeholder="发生了什么？你期望看到什么？如有坐标或复现步骤，请一并填写。"></textarea></label>
        <p v-if="error" class="form-error">{{ error }}</p>
        <div class="detail-actions"><button class="primary-button" type="submit" :disabled="submitting">{{ submitting ? '提交中…' : '提交反馈' }}</button><button class="secondary-button" type="button" :disabled="submitting" @click="router.push('/')">取消</button></div>
      </form>
    </section>
  </main>
</template>
