<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchAdminConfig, updateAdminConfig, getApiErrorMessage, type AdminConfigEntry } from '../api/http'
import { applySiteIcon } from '../siteConfig'
const router = useRouter(); const items = ref<AdminConfigEntry[]>([]); const loading = ref(true); const saving = ref(''); const error = ref(''); const message = ref('')
const groups = computed(() => {
  const definitions = [
    { id: 'site', title: '站点外观', description: '站点名称、图标和展示设置。', match: (key: string, _item: AdminConfigEntry) => key.startsWith('app.site.') },
    { id: 'security', title: '注册与安全', description: '验证、人机校验、跨域和登录会话。', match: (key: string, _item: AdminConfigEntry) => key.startsWith('app.security.') || key.startsWith('sa-token.') || key.startsWith('app.cors.') },
    { id: 'templates', title: '通知模板', description: '发送给玩家的邮件主题和正文。', match: (key: string, _item: AdminConfigEntry) => key.startsWith('app.mail.templates.') },
    { id: 'mail', title: '邮件服务', description: '邮件通知、收信和附件处理策略。', match: (key: string, _item: AdminConfigEntry) => key.startsWith('app.mail.') },
  ]
  const claimed = new Set<string>()
  const result = definitions.map((group) => ({ ...group, items: items.value.filter((item) => { if (claimed.has(item.key) || !group.match(item.key, item)) return false; claimed.add(item.key); return true }) })).filter((group) => group.items.length)
  const other = items.value.filter((item) => !claimed.has(item.key)); if (other.length) result.push({ id: 'other', title: '其他设置', description: '其余运行时配置。', match: () => true, items: other }); return result
})
async function load() { loading.value = true; error.value = ''; try { items.value = await fetchAdminConfig() } catch (e) { error.value = getApiErrorMessage(e, '配置加载失败') } finally { loading.value = false } }
async function save(item: AdminConfigEntry) { saving.value = item.key; error.value = ''; message.value = ''; try { const updated = await updateAdminConfig(item.key, item.value); Object.assign(item, updated); if (item.key === 'app.site.icon-url') applySiteIcon(item.value); message.value = `“${item.label}”已保存。` } catch (e) { error.value = getApiErrorMessage(e, '配置保存失败') } finally { saving.value = '' } }
onMounted(load)
</script>
<template>
  <main class="admin-config-page"><button class="back-link" @click="router.push('/admin')">← 返回管理面板</button>
    <header class="admin-config-header"><div><p class="eyebrow">ADMIN SETTINGS</p><h1>站点配置</h1><p>集中管理站点行为。环境变量控制的项目保持只读。</p></div><span>{{ items.length }} 项配置</span></header>
    <p v-if="loading" class="page-loading">正在加载配置…</p><p v-if="error" class="list-status warning">{{ error }} <button class="inline-action" @click="load">重试</button></p><p v-if="message" class="form-message admin-config-message">{{ message }}</p>
    <div v-if="!loading" class="admin-config-layout"><nav class="admin-config-nav" aria-label="配置分组"><a v-for="group in groups" :key="group.id" :href="`#config-${group.id}`">{{ group.title }}<small>{{ group.items.length }}</small></a></nav>
      <div class="admin-config-groups"><section v-for="group in groups" :id="`config-${group.id}`" :key="group.id" class="config-card"><header><div><h2>{{ group.title }}</h2><p>{{ group.description }}</p></div></header><div class="config-list">
        <div v-for="item in group.items" :key="item.key" class="config-row" :class="{ 'config-row-template': item.type === 'TEMPLATE' }"><div class="config-copy"><strong>{{ item.label }}</strong><small><code>{{ item.key }}</code><span v-if="item.environmentLocked" class="config-locked">环境变量锁定</span></small></div><div v-if="item.type === 'BOOLEAN'" class="config-control"><input v-model="item.value" type="checkbox" true-value="true" false-value="false" :disabled="item.environmentLocked" /></div><div v-else-if="item.type === 'SECRET'" class="config-secret">{{ item.value || '未配置' }}</div><textarea v-else-if="item.type === 'TEMPLATE'" v-model="item.value" class="config-input config-template-input" rows="6" maxlength="10000" :disabled="item.environmentLocked || saving === item.key"></textarea><input v-else v-model="item.value" class="config-input" :type="item.type === 'INTEGER' ? 'number' : item.type === 'URL' ? 'url' : 'text'" :disabled="item.environmentLocked || saving === item.key" /><button class="secondary-button" :disabled="item.environmentLocked || saving === item.key || item.type === 'SECRET'" @click="save(item)">{{ saving === item.key ? '保存中…' : '保存' }}</button></div>
      </div></section></div></div>
  </main>
</template>
<style scoped>
.admin-config-page{width:min(1180px,calc(100% - 32px));margin:0 auto;padding:26px 0 60px}.admin-config-header{display:flex;justify-content:space-between;gap:20px;align-items:end;padding:22px 0;border-bottom:1px solid var(--border)}.admin-config-header h1{margin:3px 0 6px;font-size:28px}.admin-config-header p{margin:0;color:var(--fg-muted)}.admin-config-header>span{padding:6px 10px;border:1px solid var(--border);border-radius:999px;color:var(--fg-muted)}.admin-config-layout{display:grid;grid-template-columns:190px minmax(0,1fr);gap:24px;margin-top:24px}.admin-config-nav{position:sticky;top:18px;align-self:start;display:grid;gap:4px}.admin-config-nav a{display:flex;justify-content:space-between;padding:9px 11px;border-radius:6px;color:var(--fg);text-decoration:none}.admin-config-nav a:hover{background:var(--canvas-inset)}.admin-config-nav small{color:var(--fg-muted)}.admin-config-groups{display:grid;gap:18px}.config-card{scroll-margin-top:18px;overflow:hidden;border:1px solid var(--border);border-radius:10px;background:var(--canvas)}.config-card>header{padding:17px 20px;border-bottom:1px solid var(--border-muted);background:var(--canvas-inset)}.config-card h2{margin:0 0 4px;font-size:17px}.config-card header p{margin:0;color:var(--fg-muted);font-size:13px}.admin-config-message{position:sticky;top:8px;z-index:5}@media(max-width:760px){.admin-config-layout{grid-template-columns:1fr}.admin-config-nav{position:static;grid-template-columns:repeat(2,1fr)}.admin-config-header{align-items:start;flex-direction:column}}
</style>
