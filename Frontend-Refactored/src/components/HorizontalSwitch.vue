<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

interface SwitchOption {
  value: string
  label: string
}

const props = defineProps<{
  /** 选项列表 */
  options: SwitchOption[]
  /** 当前选中值，配合 v-model 使用 */
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const rootRef = ref<HTMLElement | null>(null)
const itemRefs = ref<HTMLElement[]>([])
const indicator = ref({ left: 0, width: 0, ready: false })

const activeIndex = computed(() => props.options.findIndex((item) => item.value === props.modelValue))

function setItemRef(el: Element | null, index: number) {
  if (el instanceof HTMLElement) itemRefs.value[index] = el
}

function updateIndicator() {
  const el = itemRefs.value[activeIndex.value]
  if (!el) {
    indicator.value = { ...indicator.value, ready: false }
    return
  }
  indicator.value = { left: el.offsetLeft, width: el.offsetWidth, ready: true }
}

let observer: ResizeObserver | undefined

onMounted(() => {
  nextTick(updateIndicator)
  observer = new ResizeObserver(updateIndicator)
  if (rootRef.value) observer.observe(rootRef.value)
})

onBeforeUnmount(() => observer?.disconnect())

watch([() => props.modelValue, () => props.options], () => nextTick(updateIndicator), {
  deep: true,
})

function select(value: string) {
  if (value !== props.modelValue) emit('update:modelValue', value)
}
</script>

<template>
  <div ref="rootRef" class="h-switch" role="tablist">
    <span
      class="h-switch__indicator"
      :class="{ 'h-switch__indicator--ready': indicator.ready }"
      :style="{ width: `${indicator.width}px`, transform: `translateX(${indicator.left}px)` }"
    />

    <button
      v-for="(option, index) in options"
      :key="option.value"
      :ref="(el) => setItemRef(el as Element | null, index)"
      v-ripple
      type="button"
      role="tab"
      class="h-switch__item"
      :class="{ 'h-switch__item--active': option.value === modelValue }"
      :aria-selected="option.value === modelValue"
      @click="select(option.value)"
    >
      {{ option.label }}
    </button>
  </div>
</template>

<style scoped>
.h-switch {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 0.15rem;
  padding: 0.2rem;
  border-radius: 10px;
  background: var(--ih-c-hover);
}

/* ---------- 滑动指示块 ---------- */
.h-switch__indicator {
  position: absolute;
  top: 0.2rem;
  left: 0;
  height: calc(100% - 0.4rem);
  z-index: 0;
  border-radius: 8px;
  background: var(--color-background);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.14);
  opacity: 0;
  transition:
    transform 0.25s cubic-bezier(0.4, 0, 0.2, 1),
    width 0.25s cubic-bezier(0.4, 0, 0.2, 1),
    opacity 0.2s;
}

.h-switch__indicator--ready {
  opacity: 1;
}

/* ---------- 选项 ---------- */
.h-switch__item {
  position: relative;
  z-index: 1;
  padding: 0.4rem 1rem;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text);
  font-family: inherit;
  font-size: 0.9rem;
  line-height: 1.4;
  white-space: nowrap;
  cursor: pointer;
  transition: color 0.2s;
}

.h-switch__item:hover {
  color: var(--color-heading);
}

.h-switch__item--active {
  color: var(--color-heading);
  font-weight: 500;
}
</style>
