// =====================================================
// 类型定义
// =====================================================

interface GamepadConfig {
  DEBUG: boolean
  AXIS_THRESHOLD: number
  AXIS_COOLDOWN: number

  DPAD_INDEX: {
    UP: number
    DOWN: number
    LEFT: number
    RIGHT: number
  }

  BUTTON_INDEX: {
    A: number
    B: number
  }
}

interface DPadState {
  up: boolean
  down: boolean
  left: boolean
  right: boolean

  a: boolean
  b: boolean
}

interface GamepadState {
  lastAxisTime: number
  lastAxisDirection: number
  dpadPressed: DPadState
}

export interface XboxGamepadOptions {
  /** B 键切换目录：由调用方经 Vue 状态控制，避免直接操作 DOM */
  onToggleCatalog?: () => void
}

function createGamepadState(): GamepadState {
  return {
    lastAxisTime: 0,
    lastAxisDirection: 0,
    dpadPressed: {
      up: false,
      down: false,
      left: false,
      right: false,
      a: false,
      b: false,
    },
  }
}

// =====================================================
// 配置区域
// =====================================================

const CONFIG: GamepadConfig = {
  DEBUG: false, // 日志总开关
  AXIS_THRESHOLD: 0.7, // 摇杆触发阈值
  AXIS_COOLDOWN: 300, // 摇杆触发冷却时间 (ms)

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
 * 章节切换
 */
function switchChapter(direction: number): void {
  const buttons = document.querySelectorAll<HTMLElement>('.read-bar .tool-icon')

  if (buttons.length < 2) return

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

// =====================================================
// 摇杆处理
// =====================================================

function handleAxis(gp: Gamepad, now: number, gs: GamepadState): void {
  const axisY = gp.axes[1] || 0

  if (Math.abs(axisY) <= CONFIG.AXIS_THRESHOLD) {
    gs.lastAxisDirection = 0

    return
  }

  const direction = axisY > 0 ? 1 : -1

  const cooldownPassed = now - gs.lastAxisTime > CONFIG.AXIS_COOLDOWN

  const directionChanged = direction !== gs.lastAxisDirection

  if (cooldownPassed || directionChanged) {
    gs.lastAxisTime = now

    gs.lastAxisDirection = direction

    scrollPage(direction)
  }
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
