<script setup lang="ts">
import { ref, watchEffect } from 'vue'
import BaseButton from '../components/BaseButton.vue'
import DropdownSelect from '../components/DropdownSelect.vue'
import HorizontalSwitch from '../components/HorizontalSwitch.vue'
import SearchInput from '../components/SearchInput.vue'

const title = ref('IssueHub | 首页')

const tabs = [
  { value: 'all', label: '全部' },
  { value: 'open', label: '进行中' },
  { value: 'closed', label: '已完成' },
]
const currentTab = ref('all')

const sortOptions = [
  { value: 'newest', label: '最新创建' },
  { value: 'updated', label: '最近更新' },
  { value: 'comments', label: '评论最多' },
]
const currentSort = ref('newest')

const keyword = ref('')

// title 变化时，同步到浏览器标签页标题
watchEffect(() => {
  document.title = title.value
})

function toggleTitle() {
  title.value = title.value === 'IssueHub | 首页' ? 'IssueHub | 已切换' : 'IssueHub | 首页'
}
</script>

<template>
  <main class="home">
    <h1>{{ title }}</h1>

    <section class="group">
      <h2>水平切换</h2>
      <HorizontalSwitch v-model="currentTab" :options="tabs" />
      <p class="hint">当前选中：{{ currentTab }}</p>
    </section>

    <section class="group">
      <h2>下拉框</h2>
      <DropdownSelect v-model="currentSort" :options="sortOptions" />
      <p class="hint">当前排序：{{ currentSort }}</p>
    </section>

    <section class="group">
      <h2>搜索</h2>
      <SearchInput v-model="keyword" placeholder="搜索议题…" />
      <p class="hint">当前关键词：{{ keyword || '（空）' }}</p>
    </section>

    <section class="group">
      <h2>风格</h2>
      <div class="row">
        <BaseButton variant="primary">Primary</BaseButton>
        <BaseButton>Secondary</BaseButton>
        <BaseButton variant="danger">Danger</BaseButton>
      </div>
    </section>

    <section class="group">
      <h2>尺寸</h2>
      <div class="row">
        <BaseButton size="sm">Small</BaseButton>
        <BaseButton size="md">Medium</BaseButton>
        <BaseButton size="lg">Large</BaseButton>
      </div>
    </section>

    <section class="group">
      <h2>状态</h2>
      <div class="row">
        <BaseButton disabled>Disabled</BaseButton>
        <BaseButton variant="primary" @click="toggleTitle">切换标题</BaseButton>
      </div>
    </section>
  </main>
</template>

<style scoped>
.home {
  max-width: 720px;
  margin: 0 auto;
  padding: 3rem 1.5rem;
}

h1 {
  color: var(--color-heading);
  font-size: 1.5rem;
  font-weight: 600;
  margin-bottom: 2rem;
}

.group {
  margin-bottom: 2rem;
}

h2 {
  color: var(--color-text);
  font-size: 0.8rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  opacity: 0.6;
  margin-bottom: 0.75rem;
}

.row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}

.hint {
  margin-top: 0.75rem;
  font-size: 0.85rem;
  opacity: 0.7;
}
</style>
