<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <main class="form-page">
    <button v-ripple class="back-link" @click="router.push('/')">← 返回反馈列表</button>
    <section class="form-panel">
      <p class="eyebrow">ABOUT / LEGAL</p>
      <h1>关于 / 法律</h1>
      <p class="form-lede">
        Copyright © SimpMC Team 2025-2026. <br>
        All rights reserved. <br>
        <!--<a href="https://www.gnu.org/licenses/gpl-3.0-standalone.html"><img src="https://img.shields.io/badge/Licenced%20Under-GPLv3-blue"></a> <br>-->
      </p>
      <div class="detail-body">
        本站为玩家反馈服务器问题、跟踪解决状态的平台。<br>
        <strong>未经允许，不得在许可证范围以外二次分发本作品及其衍生作品。</strong> <br>
        未经授权，不得以非法方式利用本站。<br>
        <br>
        开发者: <a href="https://chroniflow.com">Chroniflow</a> &nbsp; <a href="https://github.com/Minecraft0122">Minecraft0122</a> <br>
        站点运行: SimpMC Team <br>
        文档 -> <a href="https://doc.simpmc.org">https://doc.simpmc.org</a> <br>
      </div>
    </section>
  </main>
</template>

<style scoped>
a {
  color: var(--ih-c-text);
}
.detail-body {
  color: var(--ih-c-text);
}
.detail-body strong {
  font-weight: 700;
  margin-left: 4px;
}
</style>
