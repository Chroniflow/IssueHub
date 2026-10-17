<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createTag, deleteTag, fetchTags, getApiErrorMessage, getApiErrorStatus, type TagResponse } from '../api/http'
import { useAuthStore } from '../stores/auth'
const router = useRouter()
const auth = useAuthStore()
const tags = ref<TagResponse[]>([])
const loading = ref(true)
const error = ref('')
const newName = ref('')
const newColor = ref('#DDEBE3')
const saving = ref(false)
const deleting = ref<number | null>(null)
const confirmingDelete = ref<number | null>(null)
const canCreate = () => auth.user?.role === 'STAFF' || auth.user?.role === 'ADMIN'
const canDelete = () => auth.user?.role === 'ADMIN'
async function loadTags() {
  loading.value = true
  error.value = ''
  try { tags.value = await fetchTags() }
  catch (requestError: unknown) { error.value = getApiErrorMessage(requestError, '暂时无法加载标签，请稍后重试。') }
  finally { loading.value = false }
}

onMounted(() => { void loadTags() })

async function addTag() {
  if (!newName.value.trim()) return
  saving.value = true; error.value = ''
  try {
    const tag = await createTag({ name: newName.value.trim(), color: newColor.value.trim() })
    tags.value = [...tags.value, tag].sort((a, b) => a.name.localeCompare(b.name))
    newName.value = ''
  } catch (requestError: unknown) {
    const status = getApiErrorStatus(requestError)
    error.value = status === 401 ? '请先登录后再管理标签。' : status === 403 ? '你没有权限创建标签。' : getApiErrorMessage(requestError, '标签创建失败，请稍后重试。')
  } finally { saving.value = false }
}

async function removeTag(tag: TagResponse) {
  if (confirmingDelete.value !== tag.id) { confirmingDelete.value = tag.id; return }
  deleting.value = tag.id; error.value = ''
  try { await deleteTag(tag.id); tags.value = tags.value.filter((item) => item.id !== tag.id); confirmingDelete.value = null }
  catch (requestError: unknown) {
    const status = getApiErrorStatus(requestError)
    error.value = status === 401 ? '请先登录后再管理标签。' : status === 403 ? '只有管理员可以删除标签。' : status === 409 ? '标签仍被工单使用，无法删除。' : getApiErrorMessage(requestError, '标签删除失败，请稍后重试。')
  } finally { deleting.value = null }
}
</script>
<template><main class="form-page"><button class="back-link" @click="router.push('/')">← 返回反馈列表</button><section class="form-panel"><p class="eyebrow">ISSUE TAGS</p><h1>标签</h1><p class="form-lede">按标签浏览玩家反馈。</p><p v-if="loading" class="list-status">正在加载标签…</p><p v-else-if="error && tags.length === 0" class="list-status warning">{{ error }} <button type="button" class="inline-action" @click="loadTags">重试</button></p><div v-else class="tag-directory"><div v-for="tag in tags" :key="tag.id" class="tag-directory-row"><button class="tag-directory-item" @click="router.push(`/?tag=${encodeURIComponent(tag.name)}`)"><span class="tag" :style="{ color: tag.color }">{{ tag.name }}</span><span>{{ tag.issueCount }} 条反馈 →</span></button><template v-if="canDelete()"><button v-if="confirmingDelete !== tag.id" class="tag-delete" type="button" :disabled="deleting === tag.id" @click="removeTag(tag)">删除</button><span v-else class="tag-delete-confirm"><button class="tag-delete" type="button" :disabled="deleting === tag.id" @click="removeTag(tag)">{{ deleting === tag.id ? '删除中…' : '确认删除' }}</button><button class="tag-cancel" type="button" :disabled="deleting === tag.id" @click="confirmingDelete = null">取消</button></span></template></div><p v-if="tags.length === 0" class="no-comments">暂无标签。</p></div><form v-if="canCreate()" class="tag-create-form" @submit.prevent="addTag"><label>新标签名称<input v-model="newName" maxlength="32" placeholder="例如：活动" /></label><label>颜色<input v-model="newColor" maxlength="7" placeholder="#DDEBE3" /></label><button class="primary-button" type="submit" :disabled="saving">{{ saving ? '创建中…' : '创建标签' }}</button></form><p v-if="error && tags.length > 0" class="form-error">{{ error }}</p></section></main></template>
