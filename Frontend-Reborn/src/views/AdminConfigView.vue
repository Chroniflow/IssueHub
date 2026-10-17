<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  fetchAdminConfig,
  updateAdminConfig,
  getApiErrorMessage,
  type AdminConfigEntry,
} from '@/api/http'
import { applySiteIcon } from '@/siteConfig'

const router = useRouter()
const items = ref<AdminConfigEntry[]>([])
const loading = ref(true)
const saving = ref('')
const error = ref('')
const message = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await fetchAdminConfig()
  } catch (e) {
    error.value = getApiErrorMessage(e, '配置加载失败')
  } finally {
    loading.value = false
  }
}

async function save(item: AdminConfigEntry) {
  saving.value = item.key
  error.value = ''
  message.value = ''
  try {
    const updated = await updateAdminConfig(item.key, item.value)
    Object.assign(item, updated)
    if (item.key === 'app.site.icon-url') applySiteIcon(item.value)
    message.value = '配置已保存，部分配置将在重启后被业务组件读取。'
  } catch (e) {
    error.value = getApiErrorMessage(e, '配置保存失败')
  } finally {
    saving.value = ''
  }
}

onMounted(load)
</script>

<template>
  <main class="form-page">
    <button v-ripple class="back-link" @click="router.push('/')">← 返回反馈列表</button>
    <section class="form-panel">
      <p class="eyebrow">SITE SETTINGS</p>
      <h1>站点配置</h1>
      <p class="form-lede">
        环境变量优先于配置文件。站点图标支持 http/https 图片 URL；邮件模板支持
        <code v-pre>{{siteName}}</code>、<code v-pre>{{username}}</code>、<code v-pre>{{issueId}}</code>、<code v-pre>{{commentId}}</code>、<code v-pre>{{message}}</code>、<code v-pre>{{actionUrl}}</code>、<code v-pre>{{verificationCode}}</code>
        和 <code v-pre>{{expiresMinutes}}</code>；实际可用字段取决于邮件类型。
      </p>
      <p v-if="loading">正在加载配置…</p>
      <p v-if="error" class="form-error">
        {{ error }} <button v-ripple class="secondary-button" @click="load">重试</button>
      </p>
      <div v-if="!loading" class="config-list">
        <div
          v-for="item in items"
          :key="item.key"
          class="config-row"
          :class="{ 'config-row-template': item.type === 'TEMPLATE' }"
        >
          <div class="config-copy">
            <strong>{{ item.label }}</strong>
            <small
              ><code>{{ item.key }}</code
              ><span v-if="item.environmentLocked" class="config-locked">由环境变量控制</span></small
            >
          </div>
          <div v-if="item.type === 'BOOLEAN'" class="config-control">
            <input
              v-model="item.value"
              type="checkbox"
              :true-value="'true'"
              :false-value="'false'"
              :disabled="item.environmentLocked"
            />
          </div>
          <div v-else-if="item.type === 'SECRET'" class="config-secret">
            {{ item.value || '未配置' }}
          </div>
          <textarea
            v-else-if="item.type === 'TEMPLATE'"
            v-model="item.value"
            class="config-input config-template-input"
            rows="5"
            maxlength="10000"
            :disabled="item.environmentLocked || saving === item.key"
          ></textarea>
          <input
            v-else
            v-model="item.value"
            class="config-input"
            :type="item.type === 'INTEGER' ? 'number' : item.type === 'URL' ? 'url' : 'text'"
            :placeholder="item.type === 'URL' ? 'https://example.com/icon.png' : undefined"
            :disabled="item.environmentLocked || saving === item.key"
          />
          <button
            v-ripple
            class="secondary-button"
            :disabled="item.environmentLocked || saving === item.key || item.type === 'SECRET'"
            @click="save(item)"
          >
            {{ saving === item.key ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
      <p v-if="message" class="form-message">{{ message }}</p>
    </section>
  </main>
</template>
