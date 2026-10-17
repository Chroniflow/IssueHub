<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { fetchSiteConfig } from "../api/http";
const router = useRouter();
const content = ref(
  "SimpIssueHub 面向 Minecraft 服务器玩家，用于提交反馈、功能建议和 Bug 报告。\n\n登录后可查看每条反馈的处理状态。玩家可以查看讨论、补充复现信息，并与工作人员一起让服务器变得更好。",
);
onMounted(async () => {
  try {
    const config = await fetchSiteConfig();
    if (config.aboutContent?.trim()) content.value = config.aboutContent;
  } catch {
    /* Keep bundled fallback. */
  }
});
</script>
<template>
  <main class="form-page">
    <button class="back-link" @click="router.push('/')">← 返回反馈列表</button>
    <section class="form-panel">
      <p class="eyebrow">ABOUT SIMPISSUEHUB</p>
      <h1>关于这个反馈站</h1>
      <div class="detail-body about-content">{{ content }}</div>
    </section>
  </main>
</template>
