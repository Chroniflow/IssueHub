<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  fetchCategories,
  createCategory,
  renameCategory,
  deleteCategory,
  getApiErrorMessage,
  type CategoryResponse,
} from '@/api/http'

const router = useRouter()
const items = ref<CategoryResponse[]>([])
const name = ref('')
const error = ref('')
const loading = ref(true)
const saving = ref(false)
const editing = ref<number | null>(null)
const editName = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await fetchCategories()
  } catch (requestError: unknown) {
    error.value = getApiErrorMessage(requestError, '无法加载分类。')
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function add() {
  if (!name.value.trim()) return
  saving.value = true
  error.value = ''
  try {
    items.value.push(await createCategory(name.value.trim()))
    name.value = ''
  } catch (requestError: unknown) {
    error.value = getApiErrorMessage(requestError, '操作失败')
  } finally {
    saving.value = false
  }
}

async function save(item: CategoryResponse) {
  saving.value = true
  error.value = ''
  try {
    const updated = await renameCategory(item.id, editName.value.trim())
    Object.assign(item, updated)
    editing.value = null
  } catch (requestError: unknown) {
    error.value = getApiErrorMessage(requestError, '操作失败')
  } finally {
    saving.value = false
  }
}

async function remove(item: CategoryResponse) {
  if (item.issueCount) return
  saving.value = true
  error.value = ''
  try {
    await deleteCategory(item.id)
    items.value = items.value.filter((x) => x.id !== item.id)
  } catch (requestError: unknown) {
    error.value = getApiErrorMessage(requestError, '操作失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <main class="form-page">
    <button v-ripple class="back-link" @click="router.push('/')">← 返回反馈列表</button>
    <section class="form-panel">
      <p class="eyebrow">ISSUE CATEGORIES</p>
      <h1>分类管理</h1>
      <p class="form-lede">分类由管理员维护，仅可删除未被使用的分类。</p>
      <p v-if="loading" class="list-status">正在加载分类…</p>
      <div class="tag-directory">
        <div v-for="item in items" :key="item.id" class="tag-directory-row">
          <template v-if="editing === item.id">
            <input v-model="editName" @keyup.enter="save(item)" />
          </template>
          <strong v-else>{{ item.name }}</strong>
          <span>{{ item.issueCount }} 条反馈</span>
          <button
            v-ripple
            class="secondary-button"
            :disabled="saving"
            @click="editing === item.id ? save(item) : ((editing = item.id), (editName = item.name))"
          >
            {{ editing === item.id ? '保存' : '重命名' }}
          </button>
          <button v-ripple class="tag-delete" :disabled="item.issueCount > 0 || saving" @click="remove(item)">
            删除
          </button>
        </div>
        <p v-if="!loading && items.length === 0" class="no-comments">暂无分类。</p>
      </div>
      <form class="tag-create-form" @submit.prevent="add">
        <label>新分类名称<input v-model="name" maxlength="32" placeholder="例如：服务器" /></label>
        <span></span>
        <button v-ripple class="primary-button" type="submit" :disabled="saving">创建分类</button>
      </form>
      <p v-if="error" class="form-error">{{ error }}</p>
    </section>
  </main>
</template>
