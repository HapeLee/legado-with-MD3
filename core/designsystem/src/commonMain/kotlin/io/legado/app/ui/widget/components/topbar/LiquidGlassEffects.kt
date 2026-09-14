package io.legado.app.ui.widget.components.topbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/**
 * 顶栏「液态玻璃」效果的平台面。
 *
 * 这是 `RuleListScaffold` 依赖链上**唯一真正的 Android 专用能力**：原实现
 * `Modifier.topBarLiquidGlass(shape)` 依赖 `android.os.Build`（API < 33 直接关闭）与
 * `InteractiveHighlight`（`android.graphics.RuntimeShader` / AGSL），AGSL 没有跨平台等价物。
 *
 * 契约面刻意只留两个能力，而不是把整段绘制搬上来：
 *
 * - [enabled]：这个平台此刻是否支持液态玻璃。Android 侧还要看 `LocalTopBarBackdrop` 是否为
 *   `null` 与 SDK 版本；
 * - [liquidGlass]：给 [shape] 应用玻璃效果，返回新的 [Modifier]。
 *
 * 未注入时 [enabled] 返回 `false`、[liquidGlass] 原样返回 `this` ⇒ 顶栏退化为普通胶囊按钮。
 * 这不是「静默降级伪装」：**关闭液态玻璃本身就是原实现已有的合法状态**（API < 33 时如此），
 * 桌面端确实没有这套着色器能力。
 *
 * 契约住 `:core:designsystem`：返回类型 [Modifier] 是 Compose 类型，零 Compose 的
 * `:core:platform` 装不下（判据同 M1-3o 的 `ClipEntry`、本切片的 `StatusBarInsets`）。
 *
 * ⚠️ 这里用 `interface` 而不是 `fun interface`：SAM 转换无法可靠地为带 `@Composable`、
 * 带 receiver 的方法生成实现，而 Android 侧的实现需要一个具体类型来持有依赖。
 */
interface LiquidGlassEffects {
    /** 当前是否启用液态玻璃。未注入的实现返回 `false`。 */
    @Composable
    fun enabled(): Boolean

    /** 为 [shape] 应用液态玻璃效果；不支持时原样返回。 */
    @Composable
    fun Modifier.liquidGlass(shape: Shape): Modifier
}

/**
 * [LiquidGlassEffects] 的宿主注入点。未注入时返回禁用实现（不抛异常）。
 */
object LiquidGlassEffectsProvider {
    @Volatile
    private var effects: LiquidGlassEffects? = null

    private val DISABLED = object : LiquidGlassEffects {
        @Composable
        override fun enabled(): Boolean = false

        @Composable
        override fun Modifier.liquidGlass(shape: Shape): Modifier = this
    }

    fun install(effects: LiquidGlassEffects) {
        this.effects = effects
    }

    fun uninstall() {
        effects = null
    }

    val isInstalled: Boolean
        get() = effects != null

    val current: LiquidGlassEffects
        get() = effects ?: DISABLED
}

/** 是否启用液态玻璃。名字与迁移前 `:core:ui` 的实现一致，顶栏组件无需感知契约。 */
@Composable
internal fun topBarLiquidGlassEnabled(): Boolean = LiquidGlassEffectsProvider.current.enabled()

/** 应用液态玻璃。名字与迁移前一致，顶栏组件无需感知契约。 */
@Composable
internal fun Modifier.topBarLiquidGlass(shape: Shape): Modifier {
    val effects = LiquidGlassEffectsProvider.current
    return effects.run { this@topBarLiquidGlass.liquidGlass(shape) }
}
