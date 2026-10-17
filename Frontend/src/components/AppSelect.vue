<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'

export interface SelectOption {
  value: string | number
  label: string
}

const props = withDefaults(defineProps<{
  modelValue: string | number | null
  options: SelectOption[]
  placeholder?: string
  ariaLabel: string
  disabled?: boolean
}>(), {
  placeholder: '请选择',
  disabled: false,
})

const emit = defineEmits<{ 'update:modelValue': [value: string | number] }>()
const root = ref<HTMLElement | null>(null)
const optionButtons = ref<HTMLButtonElement[]>([])
const open = ref(false)
const activeIndex = ref(0)
let typeahead = ''
let typeaheadTimer: ReturnType<typeof setTimeout> | undefined
const selectedOption = computed(() => props.options.find((option) => option.value === props.modelValue))
const menuId = `select-menu-${Math.random().toString(36).slice(2)}`

function setOptionRefs(element: unknown) {
  if (element instanceof HTMLButtonElement) optionButtons.value.push(element)
}

function focusOption(index: number) {
  if (!props.options.length) return
  activeIndex.value = (index + props.options.length) % props.options.length
  void nextTick(() => optionButtons.value[activeIndex.value]?.focus())
}

function showMenu() {
  if (props.disabled || !props.options.length) return
  optionButtons.value = []
  activeIndex.value = Math.max(props.options.findIndex((option) => option.value === props.modelValue), 0)
  open.value = true
  void nextTick(() => optionButtons.value[activeIndex.value]?.focus())
}

function closeMenu(returnFocus = false) {
  open.value = false
  typeahead = ''
  if (typeaheadTimer) clearTimeout(typeaheadTimer)
  if (returnFocus) void nextTick(() => root.value?.querySelector<HTMLButtonElement>('.select-trigger')?.focus())
}

function select(option: SelectOption) {
  emit('update:modelValue', option.value)
  closeMenu(true)
}

function onTriggerKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    open ? focusOption(activeIndex.value) : showMenu()
  } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    showMenu()
    focusOption(event.key === 'ArrowDown' ? activeIndex.value : activeIndex.value - 1)
  } else if (event.key === 'Escape') {
    closeMenu()
  }
}

function onMenuKeydown(event: KeyboardEvent) {
  const currentIndex = optionButtons.value.indexOf(document.activeElement as HTMLButtonElement)
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    const option = props.options[currentIndex]
    if (option) select(option)
  } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    focusOption(currentIndex + (event.key === 'ArrowDown' ? 1 : -1))
  } else if (event.key === 'Home' || event.key === 'End') {
    event.preventDefault()
    focusOption(event.key === 'Home' ? 0 : props.options.length - 1)
  } else if (event.key === 'Escape' || event.key === 'Tab') {
    closeMenu(event.key === 'Escape')
  } else if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
    event.preventDefault()
    typeahead += event.key.toLocaleLowerCase()
    if (typeaheadTimer) clearTimeout(typeaheadTimer)
    typeaheadTimer = setTimeout(() => { typeahead = '' }, 700)
    const start = Math.max(currentIndex, -1) + 1
    const match = props.options.findIndex((_, offset) => {
      const index = (start + offset) % props.options.length
      return props.options[index]?.label.toLocaleLowerCase().startsWith(typeahead) ?? false
    })
    if (match >= 0) focusOption((start + match) % props.options.length)
  }
}

function onDocumentPointerdown(event: PointerEvent) {
  if (!root.value?.contains(event.target as Node)) closeMenu()
}

document.addEventListener('pointerdown', onDocumentPointerdown)
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerdown)
  if (typeaheadTimer) clearTimeout(typeaheadTimer)
})
</script>

<template>
  <span ref="root" class="custom-select">
    <button
      class="select-trigger"
      type="button"
      :disabled="disabled"
      :aria-label="ariaLabel"
      :aria-expanded="open"
      :aria-controls="menuId"
      aria-haspopup="listbox"
      @click="open ? closeMenu() : showMenu()"
      @keydown="onTriggerKeydown"
    >
      <span :class="{ 'select-placeholder': !selectedOption }">{{ selectedOption?.label ?? placeholder }}</span>
      <span class="select-chevron" :class="{ open }" aria-hidden="true">⌄</span>
    </button>
    <span v-if="open" :id="menuId" class="select-menu" role="listbox" :aria-label="ariaLabel" @keydown="onMenuKeydown">
      <button
        v-for="(option, index) in options"
        :key="option.value"
        :ref="setOptionRefs"
        class="select-option"
        :class="{ selected: modelValue === option.value }"
        type="button"
        role="option"
        :tabindex="index === activeIndex ? 0 : -1"
        :aria-selected="modelValue === option.value"
        @click="select(option)"
      >
        <span>{{ option.label }}</span><span v-if="modelValue === option.value" aria-hidden="true">✓</span>
      </button>
    </span>
  </span>
</template>
