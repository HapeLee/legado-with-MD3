@file:OptIn(ExperimentalLayoutApi::class)

package io.legado.app.ui.widget.components.topbar

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable

/**
 * 顶栏预留「状态栏高度」的来源。
 *
 * 为什么它是一条平台契约，而不是直接用 `WindowInsets.statusBars`：
 * `WindowInsets.statusBars`（commonMain 的 `expect val`，skikoMain/Android 都有 `actual`）在
 * **系统栏被隐藏时返回 0**。原实现用的是 Android 独有的
 * `WindowInsets.statusBarsIgnoringVisibility`——它无论可见与否都返回真实高度，目的是
 * **保持顶栏高度恒定**，避免从隐藏了状态栏的界面（阅读器）返回时顶栏内容重排
 * （见 `GlassTopAppBar` 里的注释）。换成 `statusBars` 会让 Android 侧出现真实的行为差异，
 * 所以这里把「取哪个 inset」下沉为契约：
 *
 * - Android：注入 `WindowInsets.statusBarsIgnoringVisibility`，**行为与迁移前逐字一致**；
 * - 其它平台：不注入，回落到 [WindowInsets.Companion.statusBars]。
 *
 * 回落值是**有意选择**的，不是「没有实现就退化」：desktop 没有系统状态栏，
 * `statusBars` 的 `actual` 走 `LocalPlatformWindowInsets`（恒为零），语义正确。
 *
 * 契约住 `:core:designsystem` 而不是 `:core:platform`：签名里出现的是 Compose foundation 的
 * [WindowInsets]，而 `:core:platform` 是零 Compose 的纯 KMP 模块。判据与 M1-3o 的 `ClipEntry`
 * 一致——**契约住哪个模块由返回类型决定**，不是由习惯决定。
 */
fun interface StatusBarInsets {
    /** 顶栏应当为状态栏预留的 inset。在 Composition 内调用。 */
    @Composable
    fun get(): WindowInsets
}

/**
 * [StatusBarInsets] 的宿主注入点。
 *
 * 未注入时返回回落实现（[WindowInsets.Companion.statusBars]），**不抛异常**：
 * 「这个平台没有常驻状态栏」是正常状态，不是调用方的错误。判据同 M1-3n 的 `MimeTypeResolver`
 * 与 M1-3p 的 `NinePatchLoader`——只有「用户主动发起的动作」才该在未注入时抛异常。
 */
object StatusBarInsetsProvider {
    @Volatile
    private var source: StatusBarInsets? = null

    /** 回落实现：未注入时按平台自身的 `statusBars` 语义取值。 */
    private val FALLBACK = object : StatusBarInsets {
        @Composable
        override fun get(): WindowInsets = WindowInsets.statusBars
    }

    fun install(source: StatusBarInsets) {
        this.source = source
    }

    fun uninstall() {
        source = null
    }

    val isInstalled: Boolean
        get() = source != null

    val current: StatusBarInsets
        get() = source ?: FALLBACK
}

/** 顶栏预留状态栏高度的统一入口；顶栏组件只用它，不再直接碰 `WindowInsets.*IgnoringVisibility`。 */
@Composable
internal fun topBarStatusBarInsets(): WindowInsets = StatusBarInsetsProvider.current.get()
