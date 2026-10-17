<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

const props = withDefaults(defineProps<{ siteKey: string; action?: string }>(), {
  action: 'register',
})
const emit = defineEmits<{ token: [value: string] }>()
const container = ref<HTMLElement | null>(null)
let widgetId: string | undefined

function loadTurnstile() {
  if (window.turnstile) return Promise.resolve()
  return new Promise<void>((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(
      'script[data-simpissuehub-turnstile]',
    )
    const script = existing ?? document.createElement('script')
    const loaded = () => resolve()
    const failed = () => reject(new Error('Turnstile script failed to load'))
    script.addEventListener('load', loaded, { once: true })
    script.addEventListener('error', failed, { once: true })
    if (!existing) {
      script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'
      script.async = true
      script.defer = true
      script.dataset.simpissuehubTurnstile = 'true'
      document.head.appendChild(script)
    }
  })
}

function reset() {
  emit('token', '')
  if (widgetId !== undefined && window.turnstile) window.turnstile.reset(widgetId)
}

onMounted(async () => {
  await loadTurnstile()
  if (!container.value || !window.turnstile) return
  widgetId = window.turnstile.render(container.value, {
    sitekey: props.siteKey,
    action: props.action,
    theme: 'light',
    callback: (value: string) => emit('token', value),
    'expired-callback': () => emit('token', ''),
    'error-callback': () => emit('token', ''),
  })
})

onBeforeUnmount(() => {
  if (widgetId !== undefined && window.turnstile) window.turnstile.remove(widgetId)
})

defineExpose({ reset })
</script>

<template><div ref="container" class="turnstile-widget" aria-label="人机验证"></div></template>
