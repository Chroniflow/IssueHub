<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { RouterView, useRouter } from 'vue-router'
import AppHeader from './components/layout/AppHeader.vue'
import AppSidebar from './components/layout/AppSidebar.vue'
import { useAuthStore } from './stores/auth'

const router = useRouter()
const auth = useAuthStore()
const routeAnnouncement = ref<HTMLElement | null>(null)
const sidebarCollapsed = ref(false)
const mobileNavOpen = ref(false)

const COLLAPSE_KEY = 'simpissuehub.sidebar.collapsed'

onMounted(() => {
  auth.initialize()
  sidebarCollapsed.value = localStorage.getItem(COLLAPSE_KEY) === '1'
})

watch(sidebarCollapsed, (value) => {
  localStorage.setItem(COLLAPSE_KEY, value ? '1' : '0')
})

watch(
  () => router.currentRoute.value.fullPath,
  async () => {
    mobileNavOpen.value = false
    await nextTick()
    if (routeAnnouncement.value) {
      routeAnnouncement.value.textContent = `已打开 ${document.title || 'SimpIssueHub'} 页面`
      routeAnnouncement.value.focus()
    }
  },
)

function toggleSidebar() {
  if (window.matchMedia('(max-width: 900px)').matches) mobileNavOpen.value = !mobileNavOpen.value
  else sidebarCollapsed.value = !sidebarCollapsed.value
}
</script>

<template>
  <div class="app-shell" :class="{ 'app-shell--nav-open': mobileNavOpen }">
    <div ref="routeAnnouncement" class="sr-only" tabindex="-1" aria-live="polite"></div>
    <AppHeader @toggle-sidebar="toggleSidebar" />
    <div class="app-body">
      <AppSidebar :collapsed="sidebarCollapsed" />
      <div v-if="mobileNavOpen" class="app-scrim" @click="mobileNavOpen = false"></div>
      <div class="app-main">
        <RouterView />
      </div>
    </div>
  </div>
</template>
