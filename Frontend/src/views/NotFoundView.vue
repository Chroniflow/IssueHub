<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <main class="form-page compact">
    <section class="form-panel not-found-panel" aria-labelledby="not-found-title">
      <p class="eyebrow">PAGE NOT FOUND</p>
      <h1 id="not-found-title">找不到这个页面</h1>
      <p class="form-lede">链接可能已经失效，或者反馈已被移除。</p>
      <button class="primary-button" type="button" @click="router.replace('/')">返回反馈列表</button>
    </section>
  </main>
</template>
