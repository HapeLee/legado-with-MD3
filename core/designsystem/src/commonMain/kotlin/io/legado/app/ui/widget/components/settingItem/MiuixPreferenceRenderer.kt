package io.legado.app.ui.widget.components.settingItem

import androidx.compose.runtime.Composable

/**
 * Miuix `preference` 系列渲染的平台面。
 *
 * `SwitchSettingItem` 的 Miuix 分支原先直接调
 * `top.yukonga.miuix.kmp.preference.SwitchPreference`，而 `preference` 是**整个 miuix 里
 * 唯一没有 desktop 变体的制品**：Gradle 缓存里其余制品（`miuix-ui` / `miuix-core` /
 * `miuix-icons` / `miuix-shader` / `miuix-squircle`）都有 `*-desktop` 对位，只有它是
 * 孤零零的 `miuix-preference-android`（实测解 jar：`miuix-ui-desktop` 里连 `preference/`
 * 包都没有）。⇒ 直接引用会让 `compileKotlinDesktop` 失败，必须走窄契约：Android 实现留在
 * `:core:ui`（那里才看得见 `miuix-preference-android`），在
 * `io.legado.app.help.PlatformServices.install()` 注入。
 *
 * ### 失败语义：未注入 ⇒ 调方走 Material3 分支（不是空实现）
 *
 * 判据是「那条分支在其它平台到底能不能走到」：
 * [io.legado.app.ui.theme.LocalComposeEngine] 的默认值是
 * [io.legado.app.ui.theme.ComposeEngine.Material3]，而 Miuix 引擎只由 Android 侧的
 * `:core:ui`（提供 `LocalComposeEngine` 的那一处）提供 ⇒ **desktop 上 Miuix 分支不可达**。
 * 所以这里既不用「静默什么都不画」糊过去（那才是伪造跨平台支持），也不用「缺失即抛」把
 * 一条不可达路径武装成地雷：未提供渲染器时 [current] 返回 `null`，由调用方显式落到原本就
 * 存在的 Material3 渲染路径上。与 M1-3r 的 `topbar.LiquidGlassEffects` 是同一类判据——
 * 先证明分支不可达，再定契约面。
 *
 * 契约面刻意只取 `SwitchSettingItem` 真正用到的那几个参数，不是照抄 miuix 的整个签名。
 *
 * ⚠️ 这里用 `interface` 而不是 `fun interface`：带 `@Composable` 的方法不能可靠地 SAM 转换，
 * 而 Android 侧的实现需要一个具体类型来承接。
 */
interface MiuixPreferenceRenderer {

    /**
     * 渲染一个 Miuix 风格的开关设置项。参数名与迁移前 miuix 的同名函数一一对应，
     * 便于对照；`modifier` 不在契约里——调用方原本传的就是 `Modifier`。
     */
    @Composable
    fun switchPreference(
        title: String,
        summary: String?,
        checked: Boolean,
        enabled: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    )
}

/**
 * [MiuixPreferenceRenderer] 的宿主注入点。
 *
 * 未注入时 [current] 返回 `null`（不是 `object` 空实现），由调用方决定回落到哪条路径。
 */
object MiuixPreferenceRendererProvider {

    @Volatile
    private var renderer: MiuixPreferenceRenderer? = null

    fun install(renderer: MiuixPreferenceRenderer) {
        this.renderer = renderer
    }

    fun uninstall() {
        renderer = null
    }

    val isInstalled: Boolean
        get() = renderer != null

    val current: MiuixPreferenceRenderer?
        get() = renderer
}
