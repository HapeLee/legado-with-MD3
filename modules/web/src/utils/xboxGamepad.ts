// xboxGamepad.ts

// =====================================================
// 类型
// =====================================================

type Direction = 1 | -1

export interface XboxGamepadOptions {
  /** B 键切换目录：由调用方经 Vue 状态控制，避免直接操作 DOM */
  onToggleCatalog?: () => void
}

interface Binding {
  readonly index: number // 标准映射下的按键下标
  readonly label: string // 调试日志用的动作名
  readonly action: () => void
  readonly analog?: boolean // 模拟扳机：用 value 阈值判定
}

// =====================================================
// 配置
// =====================================================

interface GamepadConfig {
  readonly DEBUG: boolean // 日志总开关
  readonly AXIS_DEADZONE: number // 摇杆死区，防止漂移
  readonly AXIS_SCROLL_SPEED: number // 摇杆满偏滚动速度 (px/s)，按手感调整
  readonly TRIGGER_THRESHOLD: number // 扳机按下超过该比例视为触发
  readonly CHAPTER_COOLDOWN: number // 章节切换冷却 (ms)，仍误触多章就调大
  readonly CHAPTER_BUTTON_SELECTOR: string // 章节按钮，约定 [0]=上一章 [1]=下一章
  readonly PAGE_MARGIN: number // 翻页时少滚的距离 (px)，保留上下文和顶栏余量
  readonly DEFAULT_FRAME_MS: number // 首帧的默认帧间隔
  readonly MAX_FRAME_MS: number // 帧间隔上限，避免切回标签页后一次滚很远
}

const CONFIG: GamepadConfig = {
  DEBUG: false,
  AXIS_DEADZONE: 0.15,
  AXIS_SCROLL_SPEED: 1800,
  TRIGGER_THRESHOLD: 0.5,
  CHAPTER_COOLDOWN: 500,
  CHAPTER_BUTTON_SELECTOR: '.read-bar .tool-icon',
  PAGE_MARGIN: 110,
  DEFAULT_FRAME_MS: 16,
  MAX_FRAME_MS: 50,
}

/** Xbox 标准映射 (gamepad.mapping === 'standard') 下的按键下标 */
const BTN = {
  A: 0,
  B: 1,
  LB: 4,
  RB: 5,
  LT: 6,
  RT: 7,
  UP: 12,
  DOWN: 13,
  LEFT: 14,
  RIGHT: 15,
} as const

const AXIS_LEFT_Y = 1

// =====================================================
// 工具
// =====================================================

function log(...args: unknown[]): void {
  if (CONFIG.DEBUG) console.log(...args)
}

/** 冷却闸门：冷却期内返回 false，放行时自动重新计时 */
function createCooldown(ms: number): () => boolean {
  let last = -Infinity

  return () => {
    const now = performance.now()

    if (now - last < ms) return false

    last = now

    return true
  }
}

// =====================================================
// 页面动作（纯页面行为，与手柄无关，可被任何输入源复用）
// =====================================================

function scrollPage(direction: Direction): void {
  const distance = window.innerHeight - CONFIG.PAGE_MARGIN

  window.scrollBy({ top: direction * distance, behavior: 'smooth' })
}

function goTop(): void {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function goBottom(): void {
  window.scrollTo({ top: document.documentElement.scrollHeight, behavior: 'smooth' })
}

// 全局共享冷却：无论十字键触点抖动多少次，冷却期内只切一次章
const canSwitchChapter = createCooldown(CONFIG.CHAPTER_COOLDOWN)

function switchChapter(direction: Direction): void {
  const buttons = document.querySelectorAll<HTMLElement>(CONFIG.CHAPTER_BUTTON_SELECTOR)
  const target = buttons[direction === 1 ? 1 : 0]

  if (!target) return

  // 确认按钮存在后才消耗冷却，避免空触发占用冷却时间
  if (!canSwitchChapter()) {
    log('🎮 章节切换冷却中，忽略本次触发')

    return
  }

  target.click()
}

function toggleFullscreen(): void {
  if (document.fullscreenElement) {
    document.exitFullscreen?.()?.catch(() => {})

    return
  }

  // 手柄轮询触发通常没有用户激活，可能被浏览器拒绝；
  // 部分老浏览器的 requestFullscreen 不返回 Promise，所以对返回值也做可选链
  document.documentElement.requestFullscreen?.()?.catch(() => log('🎮 进入全屏被拒绝'))
}

// =====================================================
// 外部回调注册表（init 时登记，dispose 时注销）
// =====================================================

const registrations: XboxGamepadOptions[] = []

function toggleCatalog(): void {
  // 后注册者优先
  const handler = registrations[registrations.length - 1]?.onToggleCatalog

  if (!handler) {
    log('❌ 未注册目录切换回调')

    return
  }

  handler()
}

// =====================================================
// 按键绑定：新增或修改键位只需改这里
// =====================================================

const BINDINGS: readonly Binding[] = [
  { index: BTN.UP, label: '前往顶部', action: goTop },
  { index: BTN.DOWN, label: '前往底部', action: goBottom },
  { index: BTN.LEFT, label: '上一章', action: () => switchChapter(-1) },
  { index: BTN.RIGHT, label: '下一章', action: () => switchChapter(1) },

  { index: BTN.A, label: '切换全屏', action: toggleFullscreen },
  { index: BTN.B, label: '切换目录', action: toggleCatalog },

  { index: BTN.LB, label: '向上翻页', action: () => scrollPage(-1) },
  { index: BTN.RB, label: '向下翻页', action: () => scrollPage(1) },
  { index: BTN.LT, label: '向上翻页', action: () => scrollPage(-1), analog: true },
  { index: BTN.RT, label: '向下翻页', action: () => scrollPage(1), analog: true },
]

// =====================================================
// 轮询
// =====================================================

// 每个手柄独立记录"当前按下的键"，避免多手柄互相串触发
const pressedByPad = new Map<number, Set<number>>()

let rafId = 0
let lastFrameTime = 0

function getPressedSet(padIndex: number): Set<number> {
  let pressed = pressedByPad.get(padIndex)

  if (!pressed) {
    pressed = new Set()
    pressedByPad.set(padIndex, pressed)
  }

  return pressed
}

function isDown(gp: Gamepad, { index, analog }: Binding): boolean {
  const btn = gp.buttons[index]

  if (!btn) return false

  return analog ? btn.value > CONFIG.TRIGGER_THRESHOLD || btn.pressed : btn.pressed
}

/** 执行绑定动作；单个动作出错只记录，不影响同帧其他按键 */
function trigger({ label, action }: Binding): void {
  log(`🎮 ${label}`)

  try {
    action()
  } catch (err) {
    console.error(`[xboxGamepad] 「${label}」执行失败`, err)
  }
}

/** 按键：仅在"刚按下"的那一帧触发一次（边沿检测） */
function pollButtons(gp: Gamepad): void {
  const pressed = getPressedSet(gp.index)

  for (const binding of BINDINGS) {
    const down = isDown(gp, binding)

    if (down === pressed.has(binding.index)) continue // 状态没变化

    if (!down) {
      pressed.delete(binding.index)

      continue
    }

    pressed.add(binding.index)

    trigger(binding)
  }
}

/** 摇杆倾斜量 → 本帧滚动距离：去掉死区后重映射到 0~1，起步更平滑 */
function axisToDistance(axis: number, dtMs: number): number {
  const abs = Math.abs(axis)

  if (abs < CONFIG.AXIS_DEADZONE) return 0

  const strength = (abs - CONFIG.AXIS_DEADZONE) / (1 - CONFIG.AXIS_DEADZONE)

  return Math.sign(axis) * strength * CONFIG.AXIS_SCROLL_SPEED * (dtMs / 1000)
}

/** 左摇杆上下：连续滚动（不能用 smooth，否则每帧都会打断上一次动画） */
function pollAxis(gp: Gamepad, dtMs: number): void {
  const distance = axisToDistance(gp.axes[AXIS_LEFT_Y] ?? 0, dtMs)

  if (distance !== 0) window.scrollBy({ top: distance, behavior: 'instant' })
}

function tick(now: number): void {
  // 先预约下一帧：即使本帧出现意外，轮询链也不会断
  rafId = requestAnimationFrame(tick)

  // 帧间隔每帧只算一次，所有手柄共用
  const dtMs = lastFrameTime
    ? Math.min(now - lastFrameTime, CONFIG.MAX_FRAME_MS)
    : CONFIG.DEFAULT_FRAME_MS

  lastFrameTime = now

  for (const gp of navigator.getGamepads?.() ?? []) {
    if (!gp) continue

    pollAxis(gp, dtMs)

    pollButtons(gp)
  }
}

// =====================================================
// 生命周期
// =====================================================

function onConnected(e: GamepadEvent): void {
  log('🎮 手柄已连接:', e.gamepad.id)
}

function onDisconnected(e: GamepadEvent): void {
  log('🎮 手柄断开:', e.gamepad.id)

  pressedByPad.delete(e.gamepad.index)
}

function startPolling(): void {
  window.addEventListener('gamepadconnected', onConnected)
  window.addEventListener('gamepaddisconnected', onDisconnected)

  lastFrameTime = 0
  rafId = requestAnimationFrame(tick)
}

function stopPolling(): void {
  cancelAnimationFrame(rafId)

  window.removeEventListener('gamepadconnected', onConnected)
  window.removeEventListener('gamepaddisconnected', onDisconnected)

  pressedByPad.clear()
}

/**
 * 启动手柄监听，返回「仅注销本次调用」的停止函数。
 * - 全局只有一个轮询循环：第一次调用时启动，所有调用方都注销后才停止
 * - 多个调用方并存时（如路由过渡期间新旧页面同时存在），互不影响
 * - 停止函数可重复调用，Vue 中请在 onUnmounted 里调用
 */
export function initXboxGamepad(options: XboxGamepadOptions = {}): () => void {
  // 拷贝一份作为注册标识，同一个 options 对象多次传入也互不干扰
  const registration = { ...options }

  registrations.push(registration)

  if (registrations.length === 1) startPolling()

  let disposed = false

  return () => {
    if (disposed) return

    disposed = true

    registrations.splice(registrations.indexOf(registration), 1)

    if (registrations.length === 0) stopPolling()
  }
}