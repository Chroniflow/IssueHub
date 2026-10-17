<script setup lang="ts">
import { ref, watchEffect } from 'vue'

const pageTitle = ref('设置')

watchEffect(() => {
  document.title = `IssueHub | ${pageTitle.value}`
})
</script>

<template>
  <div class="page">
    <h1>{{ pageTitle }}</h1>
    <p>设置占位页。</p>
  </div>
</template>

<style scoped>
.page {
  padding: 3rem 2rem;
}

h1 {
  color: var(--color-heading);
  font-size: 1.5rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
}

p {
  color: var(--color-text);
  opacity: 0.7;
}
</style>
