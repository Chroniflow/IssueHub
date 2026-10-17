<script setup lang="ts">
import { computed, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 当前输入值，配合 v-model 使用 */
    modelValue: string
    /** 占位提示 */
    placeholder?: string
    /** 是否显示清空按钮 */
    clearable?: boolean
  }>(),
  {
    placeholder: '搜索…',
    clearable: true,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  enter: [value: string]
}>()

const inputRef = ref<HTMLInputElement | null>(null)

const showClear = computed(() => props.clearable && props.modelValue.length > 0)

function onInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement).value)
}

function clear() {
  emit('update:modelValue', '')
  inputRef.value?.focus()
}
</script>

<template>
  <div class="search">
    <svg class="search__icon" viewBox="0 0 16 16" aria-hidden="true">
      <circle cx="7" cy="7" r="4.5" fill="none" stroke="currentColor" stroke-width="1.5" />
      <path
        d="M10.5 10.5L14 14"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
      />
    </svg>

    <input
      ref="inputRef"
      class="search__input"
      type="text"
      :value="modelValue"
      :placeholder="placeholder"
      @input="onInput"
      @keydown.enter="emit('enter', modelValue)"
    />

    <Transition name="search-fade">
      <button
        v-if="showClear"
        v-ripple
        type="button"
        class="search__clear"
        aria-label="清空"
        @click="clear"
      >
        <svg viewBox="0 0 16 16" aria-hidden="true">
          <path
            d="M4.5 4.5l7 7M11.5 4.5l-7 7"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linecap="round"
          />
        </svg>
      </button>
    </Transition>
  </div>
</template>

<style scoped>
.search {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  height: 3rem;
  padding: 0 1rem;
  border: 1px solid var(--ih-c-text);
  /* 两端为完整半圆，而非圆角矩形 */
  border-radius: 9999px;
  background: var(--ih-c-background);
  color: var(--color-text);
  transition:
    background-color 0.2s,
    border-color 0.2s;
}

.search:hover,
.search:focus-within {
  background: var(--color-background);
  border-color: var(--color-primary);
}

.search__icon {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  opacity: 0.6;
}

/* ---------- 输入框 ---------- */
.search__input {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  color: var(--color-heading);
  font-family: inherit;
  font-size: 0.9rem;
  line-height: 1.4;
  outline: none;
}

.search__input::placeholder {
  color: var(--color-text);
  opacity: 0.5;
}

/* ---------- 清空按钮 ---------- */
.search__clear {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--color-text);
  cursor: pointer;
  opacity: 0.55;
  transition:
    background-color 0.15s,
    opacity 0.15s;
}

.search__clear svg {
  width: 14px;
  height: 14px;
}

.search__clear:hover {
  background: var(--ih-c-hover);
  opacity: 1;
}

/* ---------- 过渡 ---------- */
.search-fade-enter-active,
.search-fade-leave-active {
  transition:
    opacity 0.15s,
    transform 0.15s;
}

.search-fade-enter-from,
.search-fade-leave-to {
  opacity: 0;
  transform: scale(0.8);
}
</style>
