<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'

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

interface Suggestion {
  key: 'author' | 'tag' | 'category'
  label: string
}

const suggestions: Suggestion[] = [
  { key: 'author', label: '作者' },
  { key: 'tag', label: '标签' },
  { key: 'category', label: '分类' },
]

const inputRef = ref<HTMLInputElement | null>(null)
const focused = ref(false)
const scrollLeft = ref(0)

const showClear = computed(() => props.clearable && props.modelValue.length > 0)

interface Segment {
  text: string
  key: boolean
}

/**
 * 将输入值切分为普通文本与 `author:` / `tag:` / `category:` 前缀两类，
 * 用于在输入框下方叠加一层语法高亮文本。
 */
const segments = computed<Segment[]>(() => {
  const result: Segment[] = []
  for (const part of props.modelValue.split(/(\s+)/)) {
    if (!part) continue
    const match = part.match(/^(author|tag|category):/i)
    if (match) {
      result.push({ text: match[0], key: true })
      result.push({ text: part.slice(match[0].length), key: false })
    } else {
      result.push({ text: part, key: false })
    }
  }
  return result
})

function onInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement).value)
}

function onScroll() {
  scrollLeft.value = inputRef.value?.scrollLeft ?? 0
}

function clear() {
  emit('update:modelValue', '')
  scrollLeft.value = 0
  inputRef.value?.focus()
}

function applySuggestion(key: string) {
  const current = props.modelValue
  const prefix = current && !current.endsWith(' ') ? `${current} ` : current
  emit('update:modelValue', `${prefix}${key}:`)
  void nextTick(() => {
    inputRef.value?.focus()
    inputRef.value?.setSelectionRange(inputRef.value.value.length, inputRef.value.value.length)
  })
}

function onEnter() {
  emit('enter', props.modelValue)
  focused.value = false
  inputRef.value?.blur()
}
</script>

<template>
  <div class="search" :class="{ 'search--open': focused }">
    <div class="search__field">
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

      <div class="search__input-wrap">
        <div class="search__highlight" aria-hidden="true">
          <span
            class="search__highlight-inner"
            :style="{ transform: `translateX(${-scrollLeft}px)` }"
          >
            <template v-for="(segment, index) in segments" :key="index">
              <span :class="{ search__token: segment.key }">{{ segment.text }}</span>
            </template>
          </span>
        </div>
        <input
          ref="inputRef"
          class="search__input"
          type="text"
          role="combobox"
          aria-haspopup="listbox"
          :aria-expanded="focused"
          :value="modelValue"
          :placeholder="placeholder"
          @input="onInput"
          @scroll="onScroll"
          @focus="focused = true"
          @blur="focused = false"
          @keydown.enter="onEnter"
          @keydown.escape="focused = false"
        />
      </div>

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

    <Transition name="search-menu">
      <ul v-if="focused" class="search__menu" role="listbox" aria-label="搜索语法">
        <li v-for="item in suggestions" :key="item.key" class="search__menu-item">
          <button
            type="button"
            class="search__suggestion"
            @mousedown.prevent="applySuggestion(item.key)"
          >
            <code class="search__token">{{ item.key }}:</code>
            <span>{{ item.label }}</span>
          </button>
        </li>
      </ul>
    </Transition>
  </div>
</template>

<style scoped>
.search {
  position: relative;
  color: var(--color-text);
}

/* ---------- 输入框本体 ---------- */
.search__field {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  height: 3rem;
  padding: 0 1rem;
  border: 1px solid var(--color-border);
  /* 两端为完整半圆，而非圆角矩形 */
  border-radius: 1.5rem;
  background: var(--ih-c-background);
  transition:
    background-color 0.2s,
    border-color 0.2s,
    border-radius 0.15s;
}

.search__field:hover,
.search--open .search__field {
  background: var(--color-background);
  border-color: var(--color-primary);
}

/* 聚焦时上部保持半圆，下侧收紧以衔接菜单，整体与菜单构成圆角矩形 */
.search--open .search__field {
  border-radius: 1.5rem 1.5rem 0 0;
}

.search__icon {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  opacity: 0.6;
}

/* ---------- 输入框 + 语法高亮叠加层 ---------- */
.search__input-wrap {
  position: relative;
  flex: 1;
  min-width: 0;
  height: 100%;
}

.search__highlight {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  overflow: hidden;
  pointer-events: none;
}

.search__highlight-inner {
  display: block;
  white-space: pre;
  color: var(--color-heading);
  font-family: inherit;
  font-size: 0.9rem;
  line-height: 1.4;
  will-change: transform;
}

.search__input {
  position: relative;
  width: 100%;
  height: 100%;
  border: none;
  background: transparent;
  color: transparent;
  caret-color: var(--color-heading);
  font-family: inherit;
  font-size: 0.9rem;
  line-height: 1.4;
  outline: none;
}

.search__input::placeholder {
  color: var(--color-text);
  opacity: 0.5;
}

/* ---------- 语法高亮 token ---------- */
.search__token {
  color: var(--color-primary);
  font-weight: 600;
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

/* ---------- 语法指引菜单 ---------- */
.search__menu {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  z-index: 30;
  margin: 0;
  padding: 0.3rem 0.3rem 0.35rem;
  list-style: none;
  border: 1px solid var(--color-primary);
  border-top: none;
  border-radius: 0 0 16px 16px;
  background: var(--color-background);
  box-shadow: 0 16px 32px rgba(0, 0, 0, 0.14);
}

.search__menu-item {
  margin: 0;
}

.search__suggestion {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  width: 100%;
  padding: 0.5rem 0.75rem;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text);
  font-family: inherit;
  font-size: 0.9rem;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.15s;
}

.search__suggestion:hover {
  background: var(--ih-c-hover);
}

.search__suggestion code.search__token {
  padding: 0 0.4rem;
  border-radius: 6px;
  background: var(--color-primary-soft);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.8rem;
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

.search-menu-enter-active,
.search-menu-leave-active {
  transition:
    opacity 0.15s,
    transform 0.15s;
}

.search-menu-enter-from,
.search-menu-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
