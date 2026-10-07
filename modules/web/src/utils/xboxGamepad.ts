// =====================================================
// 类型定义
// =====================================================

interface GamepadConfig {
  DEBUG: boolean
  AXIS_DEADZONE: number // 摇杆死区，防止漂移
  AXIS_SCROLL_SPEED: number // 摇杆满偏时每秒滚动的像素
  TRIGGER_THRESHOLD: number // 扳机触发阈值
  CHAPTER_COOLDOWN: number // 章节切换冷却时间 (ms)，防止十字键抖动连切多章

  DPAD_INDEX: {
    UP: number
    DOWN: number
    LEFT: number
    RIGHT: number
  }

  BUTTON_INDEX: {
    A: number
    B: number
    LB: number
    RB: number
    LT: number
    RT: number
  }
}

interface DPadState {
  up: boolean
  down: boolean
  left: boolean
  right: boolean

  a: boolean
  b: boolean
  lb: boolean
  rb: boolean
  lt: boolean
  rt: boolean
}

interface GamepadState {
  lastFrameTime: number
  dpadPressed: DPadState
}

export interface XboxGamepadOptions {
  /** B 键切换目录：由调用方经 Vue 状态控制，避免直接操作 DOM */
  onToggleCatalog?: () => void
}

function createGamepadState(): GamepadState {
  return {
    lastFrameTime: 0,
    dpadPressed: {
      up: false,
      down: false,
      left: false,
      right: false,
      a: false,
      b: false,
      lb: false,
      rb: false,
      lt: false,
      rt: false,
    },
  }
}

// =====================================================
// 配置区域
// =====================================================

const CONFIG: GamepadConfig = {
  DEBUG: false, // 日志总开关
  AXIS_DEADZONE: 0.15, // 摇杆死区
  AXIS_SCROLL_SPEED: 1800, // 摇杆满偏滚动速度 (px/s)，按手感调整
  TRIGGER_THRESHOLD: 0.5, // 扳机按下超过 50% 视为触发
  CHAPTER_COOLDOWN: 500, // 章节切换冷却 (ms)，仍误触多章就调大

  DPAD_INDEX: {
    // Xbox 标准映射
    UP: 12,
    DOWN: 13,
    LEFT: 14,
    RIGHT: 15,
  },

  // Xbox 按键
  BUTTON_INDEX: {
    A: 0,
    B: 1,
    LB: 4,
    RB: 5,
    LT: 6,
    RT: 7,
  },
}

// =====================================================
// 日志工具函数
// =====================================================

function log(...args: any[]): void {
  if (!CONFIG.DEBUG) return

  console.log(...args)
}

// =====================================================
// 状态记录
// =====================================================

let running = false

let onToggleCatalog: (() => void) | null = null

// 每个手柄独立一份按键/摇杆边沿状态，避免多手柄互相串触发
const gamepadStates = new Map<number, GamepadState>()

// 全局共享（不按手柄区分），保证冷却期内无论多少次抖动只切一次章
let lastChapterSwitchTime = 0

// =====================================================
// 通用工具函数
// =====================================================

/**
 * 平滑翻页
 */
function scrollPage(direction: number): void {
  const offset = window.innerHeight - 110

  const distance = direction === 1 ? offset : -offset

  window.scrollBy({
    top: distance,

    behavior: 'smooth',
  })
}

/**
 * 顶部
 */
function goTop(): void {
  log('🎮 前往顶部')

  window.scrollTo({
    top: 0,

    behavior: 'smooth',
  })
}

/**
 * 底部
 */
function goBottom(): void {
  log('🎮 前往底部')

  window.scrollTo({
    top: document.body.scrollHeight,

    behavior: 'smooth',
  })
}

/**
 * 章节切换（带冷却，防止十字键触点抖动导致连切多章）
 */
function switchChapter(direction: number): void {
  const now = performance.now()

  const gap = now - lastChapterSwitchTime

  if (gap < CONFIG.CHAPTER_COOLDOWN) {
    log(`🎮 章节切换冷却中，忽略本次触发（距上次 ${Math.round(gap)}ms）`)

    return
  }

  const buttons = document.querySelectorAll<HTMLElement>('.read-bar .tool-icon')

  if (buttons.length < 2) return

  // 确认真的会点击之后再记录时间
  lastChapterSwitchTime = now

  if (direction === 1) {
    log('🎮 下一章')

    buttons[1].click()
  } else {
    log('🎮 上一章')

    buttons[0].click()
  }
}

/**
 * 全屏切换
 */
function toggleFullscreen(): void {
  if (!document.fullscreenElement) {
    const promise = document.documentElement.requestFullscreen?.()

    // 手柄轮询触发通常没有用户激活，可能被浏览器拒绝，需要捕获
    promise?.catch(() => log('🎮 进入浏览器全屏被拒绝'))

    log('🎮 进入浏览器全屏')
  } else {
    document.exitFullscreen?.()

    log('🎮 退出浏览器全屏')
  }
}

/**
 * 目录显示隐藏
 */
function toggleCatalog(): void {
  if (onToggleCatalog) {
    onToggleCatalog()

    log('🎮 切换目录')

    return
  }

  log('❌ 未注册目录切换回调')
}

/**
 * 边沿检测
 */
function isPressedOnce(current: boolean, previous: boolean): boolean {
  return current && !previous
}

/**
 * 扳机键判定：优先使用模拟量 value，兼容仅提供 pressed 的情况
 */
function isTriggerPressed(btn: GamepadButton | undefined): boolean {
  if (!btn) return false

  return btn.value > CONFIG.TRIGGER_THRESHOLD || btn.pressed
}

// =====================================================
// 摇杆处理
// =====================================================

/**
 * 左摇杆上下：每帧按倾斜程度连续滚动（倾斜越多滚得越快）
 */
function handleAxis(gp: Gamepad, now: number, gs: GamepadState): void {
  const axisY = gp.axes[1] || 0

  // 用帧间隔换算距离，帧率不同速度也一致；限制上限避免切回标签页后跳一大段
  const dt = gs.lastFrameTime ? Math.min(now - gs.lastFrameTime, 50) : 16

  gs.lastFrameTime = now

  const abs = Math.abs(axisY)

  if (abs < CONFIG.AXIS_DEADZONE) return

  // 去掉死区后重新映射到 0~1，起步更平滑
  const strength = (abs - CONFIG.AXIS_DEADZONE) / (1 - CONFIG.AXIS_DEADZONE)

  const distance = Math.sign(axisY) * strength * CONFIG.AXIS_SCROLL_SPEED * (dt / 1000)

  // 连续滚动不能用 smooth，否则每帧都会打断上一次动画造成卡顿
  window.scrollBy({ top: distance, behavior: 'instant' })
}

// =====================================================
// DPad + 按键处理
// =====================================================

function handleDPad(gp: Gamepad, gs: GamepadState): void {
  const indexes = CONFIG.DPAD_INDEX

  const buttons = CONFIG.BUTTON_INDEX

  const current: DPadState = {
    up: gp.buttons[indexes.UP]?.pressed || false,

    down: gp.buttons[indexes.DOWN]?.pressed || false,

    left: gp.buttons[indexes.LEFT]?.pressed || false,

    right: gp.buttons[indexes.RIGHT]?.pressed || false,

    a: gp.buttons[buttons.A]?.pressed || false,

    b: gp.buttons[buttons.B]?.pressed || false,

    lb: gp.buttons[buttons.LB]?.pressed || false,

    rb: gp.buttons[buttons.RB]?.pressed || false,

    lt: isTriggerPressed(gp.buttons[buttons.LT]),

    rt: isTriggerPressed(gp.buttons[buttons.RT]),
  }

  // 十字 ↑ 顶部

  if (isPressedOnce(current.up, gs.dpadPressed.up)) {
    goTop()
  }

  // 十字 ↓ 底部

  if (isPressedOnce(current.down, gs.dpadPressed.down)) {
    goBottom()
  }

  // 左右章节

  if (isPressedOnce(current.left, gs.dpadPressed.left)) {
    switchChapter(-1)
  }

  if (isPressedOnce(current.right, gs.dpadPressed.right)) {
    switchChapter(1)
  }

  // A 全屏

  if (isPressedOnce(current.a, gs.dpadPressed.a)) {
    toggleFullscreen()
  }

  // B 目录

  if (isPressedOnce(current.b, gs.dpadPressed.b)) {
    toggleCatalog()
  }

  // LB 向上翻页

  if (isPressedOnce(current.lb, gs.dpadPressed.lb)) {
    log('🎮 LB 向上翻页')

    scrollPage(-1)
  }

  // RB 向下翻页

  if (isPressedOnce(current.rb, gs.dpadPressed.rb)) {
    log('🎮 RB 向下翻页')

    scrollPage(1)
  }

  // LT 向上翻页

  if (isPressedOnce(current.lt, gs.dpadPressed.lt)) {
    log('🎮 LT 向上翻页')

    scrollPage(-1)
  }

  // RT 向下翻页

  if (isPressedOnce(current.rt, gs.dpadPressed.rt)) {
    log('🎮 RT 向下翻页')

    scrollPage(1)
  }

  gs.dpadPressed = current
}

// =====================================================
// 主手柄处理入口
// =====================================================

function handleGamepad(gp: Gamepad): void {
  let gs = gamepadStates.get(gp.index)

  if (!gs) {
    gs = createGamepadState()

    gamepadStates.set(gp.index, gs)
  }

  const now = performance.now()

  handleAxis(gp, now, gs)

  handleDPad(gp, gs)
}

// =====================================================
// 主循环
// =====================================================

function gamepadLoop(): void {
  const gamepads = navigator.getGamepads?.() || []

  for (const gp of gamepads) {
    if (gp) handleGamepad(gp)
  }

  requestAnimationFrame(gamepadLoop)
}

// =====================================================
// 连接 / 断开事件
// =====================================================

window.addEventListener('gamepadconnected', (e: GamepadEvent) => {
  log('🎮 手柄已连接:', e.gamepad.id)

  if (!running) {
    running = true

    requestAnimationFrame(gamepadLoop)
  }
})

window.addEventListener('gamepaddisconnected', (e: GamepadEvent) => {
  log('🎮 手柄断开:', e.gamepad.id)

  gamepadStates.delete(e.gamepad.index)
})

// =====================================================
// 外部调用
// =====================================================

export function initXboxGamepad(options?: XboxGamepadOptions): void {
  onToggleCatalog = options?.onToggleCatalog ?? null

  if (!running) {
    running = true

    requestAnimationFrame(gamepadLoop)
  }
}
