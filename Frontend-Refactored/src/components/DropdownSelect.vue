<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

interface DropdownOption {
  value: string
  label: string
}

const props = defineProps<{
  /** 选项列表 */
  options: DropdownOption[]
  /** 当前选中值，配合 v-model 使用 */
  modelValue: string
  /** 占位提示，未选中时显示 */
  placeholder?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const rootRef = ref<HTMLElement | null>(null)
const open = ref(false)

const selected = computed(() => props.options.find((item) => item.value === props.modelValue))
const displayText = computed(() => selected.value?.label ?? props.placeholder ?? '请选择')

function toggle() {
  open.value = !open.value
}

function select(option: DropdownOption) {
  emit('update:modelValue', option.value)
  open.value = false
}

function onDocumentPointerDown(event: PointerEvent) {
  if (rootRef.value && !rootRef.value.contains(event.target as Node)) {
    open.value = false
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') open.value = false
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocumentPointerDown)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div ref="rootRef" class="dropdown" :class="{ 'dropdown--open': open }">
    <button
      v-ripple
      type="button"
      class="dropdown__trigger"
      role="combobox"
      :aria-expanded="open"
      aria-haspopup="listbox"
      @click="toggle"
    >
      <span class="dropdown__value">{{ displayText }}</span>
      <svg class="dropdown__chevron" viewBox="0 0 16 16" aria-hidden="true">
        <path
          d="M4 6l4 4 4-4"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </button>

    <Transition name="dropdown-fade">
      <ul v-if="open" class="dropdown__menu" role="listbox">
        <li
          v-for="option in options"
          :key="option.value"
          v-ripple
          class="dropdown__option"
          :class="{ 'dropdown__option--active': option.value === modelValue }"
          role="option"
          :aria-selected="option.value === modelValue"
          @click="select(option)"
        >
          {{ option.label }}
        </li>
      </ul>
    </Transition>
  </div>
</template>

<style scoped>
.dropdown {
  position: relative;
  display: inline-block;
}

/* ---------- 触发器 ---------- */
.dropdown__trigger {
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  min-width: 9rem;
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-background);
  color: var(--color-text);
  font-family: inherit;
  font-size: 0.9rem;
  line-height: 1.4;
  cursor: pointer;
  transition:
    border-color 0.2s,
    background-color 0.2s;
}

.dropdown__trigger:hover {
  border-color: var(--color-border-hover);
  background: var(--ih-c-hover);
}

.dropdown--open .dropdown__trigger {
  border-color: var(--color-primary);
}

.dropdown__value {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dropdown__chevron {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  opacity: 0.6;
  transition: transform 0.2s;
}

.dropdown--open .dropdown__chevron {
  transform: rotate(180deg);
}

/* ---------- 菜单 ---------- */
.dropdown__menu {
  position: absolute;
  top: calc(100% + 0.35rem);
  left: 0;
  z-index: 20;
  min-width: 100%;
  margin: 0;
  padding: 0.25rem;
  list-style: none;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-background);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}

.dropdown__option {
  padding: 0.45rem 0.7rem;
  border-radius: 7px;
  color: var(--color-text);
  font-size: 0.9rem;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color 0.15s,
    color 0.15s;
}

.dropdown__option:hover {
  background: var(--ih-c-hover);
  color: var(--color-heading);
}

.dropdown__option--active {
  color: var(--color-primary);
  font-weight: 500;
}

/* ---------- 过渡 ---------- */
.dropdown-fade-enter-active,
.dropdown-fade-leave-active {
  transition:
    opacity 0.15s,
    transform 0.15s;
}

.dropdown-fade-enter-from,
.dropdown-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
