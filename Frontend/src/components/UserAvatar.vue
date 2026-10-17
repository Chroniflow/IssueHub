<script setup lang="ts">
import { computed, ref } from 'vue'

const props = withDefaults(defineProps<{
  username: string
  url?: string | null
  size?: 'small' | 'medium' | 'large'
}>(), { url: null, size: 'medium' })

const imageFailed = ref(false)
const initial = computed(() => props.username.trim().slice(0, 1).toUpperCase() || '?')
const imageVisible = computed(() => Boolean(props.url) && !imageFailed.value)
</script>

<template>
  <div class="user-avatar" :class="`avatar-${size}`" :title="username" :aria-label="`${username} 的头像`">
    <img v-if="imageVisible" :src="props.url ?? undefined" :alt="`${username} 的头像`" referrerpolicy="no-referrer" @error="imageFailed = true" />
    <span v-else aria-hidden="true">{{ initial }}</span>
  </div>
</template>
