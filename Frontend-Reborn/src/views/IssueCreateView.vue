<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import DropdownSelect from '@/components/DropdownSelect.vue'
import {
  fetchTags,
  fetchCategories,
  getApiErrorMessage,
  getApiErrorStatus,
  createIdempotencyKey,
  type IssuePollPayload,
} from '@/api/http'
import { useIssueStore } from '@/stores/issues'
import { hasTextWithin } from '@/utils/format'

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
const pollEnabled = ref(false)
const pollQuestion = ref('')
const pollOptions = ref(['', ''])
const pollMultipleChoice = ref(false)

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
    if (categories.length && !categories.some((item) => item.name === category.value))
      category.value = categories[0]?.name ?? ''
    if (!categories.length) categoryLoadError.value = '暂无可用分类，请联系管理员配置。'
  } catch (requestError: unknown) {
    categoryLoadError.value = getApiErrorMessage(requestError, '分类列表暂时不可用，请稍后重试。')
  } finally {
    categoryLoading.value = false
  }
}

onMounted(() => {
  void loadTagOptions()
  void loadCategoryOptions()
})

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
    error.value = '请选择有效分类。'
    return
  }
  let poll: IssuePollPayload | undefined
  if (pollEnabled.value) {
    const question = pollQuestion.value.trim()
    const options = pollOptions.value.map((option) => option.trim())
    if (!hasTextWithin(question, 1, 200)) {
      error.value = '投票问题长度必须为 1-200 个字符。'
      return
    }
    if (
      options.length < 2 ||
      options.length > 10 ||
      options.some((option) => !hasTextWithin(option, 1, 100))
    ) {
      error.value = '投票选项须为 2-10 个，每项 1-100 个字符。'
      return
    }
    if (new Set(options.map((option) => option.toLocaleLowerCase())).size !== options.length) {
      error.value = '投票选项不能重复。'
      return
    }
    poll = { question, multipleChoice: pollMultipleChoice.value, options }
  }
  submitting.value = true
  error.value = ''
  try {
    const id = await store.createFromApi(
      {
        title: title.value.trim(),
        body: body.value.trim(),
        tag: tag.value,
        category: category.value,
        poll,
      },
      idempotencyKey.value,
    )
    idempotencyKey.value = createIdempotencyKey()
    router.push(`/issues/${id}`)
  } catch (requestError: unknown) {
    error.value =
      getApiErrorStatus(requestError) === 401
        ? '请先登录后再提交反馈。'
        : getApiErrorMessage(requestError, '提交失败，请稍后重试。')
  } finally {
    submitting.value = false
  }
}

function addPollOption() {
  if (pollOptions.value.length < 10) pollOptions.value.push('')
}
function removePollOption(index: number) {
  if (pollOptions.value.length > 2) pollOptions.value.splice(index, 1)
}
</script>

<template>
  <main class="form-page">
    <button v-ripple class="back-link" @click="router.push('/')">← 返回反馈列表</button>
    <section class="form-panel">
      <p class="eyebrow">NEW ISSUE</p>
      <h1>创建反馈</h1>
      <p class="form-lede">请提供足够的信息，方便工作人员理解和复现问题。</p>
      <form @submit.prevent="submit">
        <label>标题<input v-model="title" maxlength="120" placeholder="用一句话描述问题或建议" /></label>
        <label
          >类型<DropdownSelect
            v-model="tag"
            :options="tagOptions"
            :disabled="tagLoading"
            ariaLabel="反馈类型"
        /></label>
        <label
          >分类<DropdownSelect
            v-model="category"
            :options="categoryOptions"
            :disabled="categoryLoading || categoryOptions.length === 0"
            ariaLabel="反馈分类"
        /></label>
        <p v-if="categoryLoadError" class="form-hint">
          {{ categoryLoadError }}
          <button type="button" class="inline-action" @click="loadCategoryOptions">重试</button>
        </p>
        <p v-if="tagLoadError" class="form-hint">
          {{ tagLoadError }}
          <button type="button" class="inline-action" @click="loadTagOptions">重试</button>
        </p>
        <label
          >具体内容<textarea
            v-model="body"
            rows="9"
            placeholder="发生了什么？你期望看到什么？如有坐标或复现步骤，请一并填写。"
          ></textarea>
        </label>
        <section class="issue-create-poll" aria-labelledby="poll-entry-title">
          <div class="issue-create-poll-header">
            <div>
              <h2 id="poll-entry-title">投票</h2>
              <p class="form-hint">可选：收集玩家对多个方案的意见。</p>
            </div>
            <button
              v-if="!pollEnabled"
              v-ripple
              class="secondary-button"
              type="button"
              @click="pollEnabled = true"
            >
              + 添加投票
            </button>
            <button v-else v-ripple class="secondary-button" type="button" @click="pollEnabled = false">
              移除投票
            </button>
          </div>
          <div v-if="pollEnabled" class="poll-creator issue-create-poll-fields">
            <label
              >投票问题<input
                v-model="pollQuestion"
                maxlength="200"
                placeholder="例如：下个赛季你最期待哪个玩法？"
            /></label>
            <div class="poll-option-editor">
              <label v-for="(_, index) in pollOptions" :key="index"
                >选项 {{ index + 1
                }}<span
                  ><input
                    v-model="pollOptions[index]"
                    maxlength="100"
                    :placeholder="`选项 ${index + 1}`" /><button
                    v-if="pollOptions.length > 2"
                    type="button"
                    :aria-label="`移除选项 ${index + 1}`"
                    @click="removePollOption(index)"
                  >
                    ×
                  </button></span
                ></label
              ><button
                v-if="pollOptions.length < 10"
                class="inline-action"
                type="button"
                @click="addPollOption"
              >
                + 添加选项
              </button>
            </div>
            <label class="poll-multiple-toggle"
              ><input v-model="pollMultipleChoice" type="checkbox" /><span
                >允许玩家选择多个选项</span
              ></label
            >
          </div>
        </section>
        <p v-if="error" class="form-error">{{ error }}</p>
        <div class="detail-actions">
          <button v-ripple class="primary-button" type="submit" :disabled="submitting">
            {{ submitting ? '提交中…' : '提交反馈' }}
          </button>
          <button
            v-ripple
            class="secondary-button"
            type="button"
            :disabled="submitting"
            @click="router.push('/')"
          >
            取消
          </button>
        </div>
      </form>
    </section>
  </main>
</template>
