<script setup lang="ts">
withDefaults(
  defineProps<{
    /** 视觉风格 */
    variant?: 'primary' | 'secondary' | 'danger'
    /** 尺寸 */
    size?: 'sm' | 'md' | 'lg'
    /** 是否禁用 */
    disabled?: boolean
    /** 原生 button 的 type 属性 */
    type?: 'button' | 'submit' | 'reset'
  }>(),
  {
    variant: 'secondary',
    size: 'md',
    disabled: false,
    type: 'button',
  },
)

const emit = defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<template>
  <button
    v-ripple
    :type="type"
    :disabled="disabled"
    class="base-button"
    :class="[`base-button--${variant}`, `base-button--${size}`]"
    @click="emit('click', $event)"
  >
    <slot />
  </button>
</template>

<style scoped>
.base-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4em;
  border: 1px solid transparent;
  border-radius: 6px;
  font-family: inherit;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color 0.2s,
    border-color 0.2s,
    color 0.2s,
    opacity 0.2s;
}

.base-button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

/* ---------- 尺寸 ---------- */
.base-button--sm {
  padding: 0.35rem 0.7rem;
  font-size: 0.8rem;
}

.base-button--md {
  padding: 0.5rem 1rem;
  font-size: 0.9rem;
}

.base-button--lg {
  padding: 0.7rem 1.4rem;
  font-size: 1rem;
}

/* ---------- 风格 ---------- */
.base-button--primary {
  background: var(--color-primary);
  color: #fff;
}

.base-button--primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
}

.base-button--secondary {
  background: var(--color-background-soft);
  border-color: var(--color-border);
  color: var(--color-text);
}

.base-button--secondary:hover:not(:disabled) {
  border-color: var(--color-border-hover);
}

.base-button--danger {
  background: transparent;
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.base-button--danger:hover:not(:disabled) {
  background: var(--color-danger);
  color: #fff;
}
</style>
