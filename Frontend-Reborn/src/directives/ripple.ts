import type { Directive } from 'vue'

/**
 * Material Design 风格的点击波纹指令。
 *
 * 用法：在任意元素上添加 `v-ripple`，点击时会在指针位置生成一个
 * 圆形波纹，颜色取自 `--ih-c-wave`，并向外扩散后淡出。
 *
 * 波纹的样式定义在 `src/assets/main.css`（全局），因为波纹元素是
 * 运行时动态创建的，无法被组件的 scoped 样式命中。
 */

const RIPPLE_CLASS = 'ih-ripple'
const HOST_CLASS = 'ih-ripple-host'
const LAYER_CLASS = 'ih-ripple-layer'

type RippleHandlers = {
  pointer: (event: PointerEvent) => void
  key: (event: KeyboardEvent) => void
}

const handlers = new WeakMap<HTMLElement, RippleHandlers>()

/**
 * 确保宿主内存在负责裁剪波纹的遮罩层，并返回它。
 *
 * 不能仅靠 `.ih-ripple-host` 类来约束波纹：Vue 在更新带动态 class 绑定的
 * 组件（例如 RouterLink 的激活态）时会整体重写元素的 className，从而抹掉
 * 指令在 mounted 中加上的宿主类。宿主类一旦丢失，宿主退回
 * `position: static; overflow: visible`，绝对定位的波纹就会改以更外层的
 * 定位祖先为基准，扩散到整个页面。
 *
 * 这里改为在宿主内插入一个遮罩层：它是运行时创建的节点，不在 Vue 的
 * 虚拟 DOM 管理范围内，其 class 不会被 class 补丁覆盖，因此波纹始终被
 * 限制在宿主范围内。宿主若为 static，则用内联样式补上定位，内联样式同样
 * 不会被 class 补丁影响。
 */
function ensureLayer(el: HTMLElement): HTMLElement {
  const existing = el.querySelector<HTMLElement>(`:scope > .${LAYER_CLASS}`)
  if (existing) return existing

  // 遮罩层为绝对定位，宿主必须是定位元素才能成为其包含块。
  // 用内联样式固定定位：即使之后宿主类被 Vue 的 class 补丁抹掉，
  // 定位也不会退回 static。若组件已显式设置过定位则保持不变。
  if (!el.style.position) {
    const position = getComputedStyle(el).position
    el.style.position = position === 'static' ? 'relative' : position
  }

  const layer = document.createElement('span')
  layer.className = LAYER_CLASS
  el.appendChild(layer)
  return layer
}

/**
 * 判断元素当前是否处于禁用态。
 *
 * 在事件触发时实时判断，因此对通过 prop 动态切换的 disabled 同样有效。
 * 同时兼容原生 disabled、aria-disabled，以及通过类名/属性标记的禁用态。
 */
function isDisabled(el: HTMLElement): boolean {
  // 原生表单控件（button / input / select / textarea ...）
  if ('disabled' in el && (el as HTMLButtonElement).disabled) return true
  if (el.hasAttribute('disabled')) return true

  // WAI-ARIA：aria-disabled="true"
  const aria = el.getAttribute('aria-disabled')
  if (aria != null && aria !== 'false') return true

  return false
}

/** 在元素内的 (clientX, clientY) 处生成一个波纹 */
function spawnRipple(el: HTMLElement, clientX: number, clientY: number): void {
  const layer = ensureLayer(el)
  const rect = el.getBoundingClientRect()
  const x = clientX - rect.left
  const y = clientY - rect.top

  // Material Design：半径取点击点到最远角的距离，保证波纹能覆盖整个元素
  const radius = Math.hypot(Math.max(x, rect.width - x), Math.max(y, rect.height - y))
  const size = radius * 2

  const ripple = document.createElement('span')
  ripple.className = RIPPLE_CLASS
  ripple.style.width = `${size}px`
  ripple.style.height = `${size}px`
  ripple.style.left = `${x - radius}px`
  ripple.style.top = `${y - radius}px`

  layer.appendChild(ripple)
  ripple.addEventListener('animationend', () => ripple.remove(), { once: true })
}

export const vRipple: Directive<HTMLElement> = {
  mounted(el) {
    el.classList.add(HOST_CLASS)
    ensureLayer(el)

    const pointer = (event: PointerEvent) => {
      // 只响应鼠标左键 / 触摸 / 触控笔主按键
      if (event.button !== 0) return
      // 禁用态不产生波纹
      if (isDisabled(el)) return
      spawnRipple(el, event.clientX, event.clientY)
    }

    // 键盘激活（Enter / Space）时从元素中心扩散，保证可访问性
    const key = (event: KeyboardEvent) => {
      if (event.key !== 'Enter' && event.key !== ' ') return
      if (isDisabled(el)) return
      const rect = el.getBoundingClientRect()
      spawnRipple(el, rect.left + rect.width / 2, rect.top + rect.height / 2)
    }

    el.addEventListener('pointerdown', pointer)
    el.addEventListener('keydown', key)
    handlers.set(el, { pointer, key })
  },

  unmounted(el) {
    const bound = handlers.get(el)
    if (!bound) return
    el.removeEventListener('pointerdown', bound.pointer)
    el.removeEventListener('keydown', bound.key)
    handlers.delete(el)
  },
}

export default vRipple
