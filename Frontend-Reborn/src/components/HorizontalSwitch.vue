<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

interface SwitchOption {
  value: string
  label: string
  /** 可选图标；所有选项都提供图标时才启用自动收起 */
  icon?: string
}

const props = withDefaults(
  defineProps<{
    /** 选项列表 */
    options: SwitchOption[]
    /** 当前选中值，配合 v-model 使用 */
    modelValue: string
    /** 强制仅显示图标（依赖选项的 icon 字段） */
    iconOnly?: boolean
  }>(),
  { iconOnly: false },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const wrapRef = ref<HTMLElement | null>(null)
const measureRef = ref<HTMLElement | null>(null)
const itemRefs = ref<HTMLElement[]>([])
const indicator = ref({ left: 0, width: 0, ready: false })
/** 空间不足时自动收起为图标 */
const autoCompact = ref(false)

const hasIcons = computed(() => props.options.length > 0 && props.options.every((item) => item.icon))
const compact = computed(() => props.iconOnly || (hasIcons.value && autoCompact.value))

const activeIndex = computed(() =>
  props.options.findIndex((item) => item.value === props.modelValue),
)

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

/**
 * 测量副本是收起根节点的兄弟节点，因此始终按“展开”布局渲染、宽度恒定，
 * 不会随真实组件的收起而缩小，避免“展开→测量变小→收起→测量变大”的震荡。
 */
function evaluate() {
  if (!hasIcons.value || !props.options.length) {
    autoCompact.value = false
    return
  }
  const required = measureRef.value?.offsetWidth ?? 0
  const available = wrapRef.value?.clientWidth ?? Number.POSITIVE_INFINITY
  autoCompact.value = required > available
}

let observer: ResizeObserver | undefined

onMounted(() => {
  nextTick(() => {
    updateIndicator()
    evaluate()
  })
  // 只监听外层槽位宽度；测量副本为兄弟节点，不受真实组件尺寸变化影响。
  observer = new ResizeObserver(() => {
    updateIndicator()
    evaluate()
  })
  if (wrapRef.value) observer.observe(wrapRef.value)
})

onBeforeUnmount(() => observer?.disconnect())

watch(
  [() => props.modelValue, () => props.options],
  () =>
    nextTick(() => {
      updateIndicator()
      evaluate()
    }),
  { deep: true },
)

// 收起/展开会改变选项尺寸，需要重新定位滑动指示块。
watch(compact, () => nextTick(updateIndicator))

function select(value: string) {
  if (value !== props.modelValue) emit('update:modelValue', value)
}
</script>

<template>
  <div ref="wrapRef" class="h-switch-wrap">
    <div class="h-switch" :class="{ 'h-switch--compact': compact }" role="tablist">
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
        :aria-label="option.label"
        :title="option.label"
        @click="select(option.value)"
      >
        <span v-if="option.icon" class="h-switch__glyph" aria-hidden="true">{{ option.icon }}</span>
        <span v-if="!compact" class="h-switch__text">{{ option.label }}</span>
      </button>
    </div>

    <!-- 测量完整文字布局所需宽度；作为真实组件的兄弟节点，脱离文档流 -->
    <div v-if="hasIcons" ref="measureRef" class="h-switch h-switch__measure" aria-hidden="true">
      <button
        v-for="option in options"
        :key="option.value"
        type="button"
        class="h-switch__item"
        tabindex="-1"
      >
        <span v-if="option.icon" class="h-switch__glyph">{{ option.icon }}</span>
        <span class="h-switch__text">{{ option.label }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.h-switch-wrap {
  position: relative;
  display: flex;
  flex: 1;
  min-width: 0;
}

.h-switch {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 0.15rem;
  padding: 0.2rem;
  border-radius: 10px;
  background: var(--ih-c-hover);
}

/* 测量副本：与展开布局同构，但脱离文档流且始终按展开态渲染 */
.h-switch__measure {
  position: absolute;
  top: 0;
  left: 0;
  visibility: hidden;
  pointer-events: none;
  white-space: nowrap;
  z-index: -1;
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
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
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

.h-switch__glyph {
  line-height: 1;
}

/* ---------- 仅图标 ---------- */
.h-switch--compact .h-switch__item {
  justify-content: center;
  width: 2.25rem;
  padding: 0.4rem;
}
</style>
