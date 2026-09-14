package io.legado.app.ui.widget.components.settingItem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * [MiuixPreferenceRenderer] 的 Android 实现。
 *
 * 留在 `:core:ui` 而不是 `:app`：这条分支要用的 `top.yukonga.miuix.kmp.preference`
 * 制品只有 `-android` 变体，而 `:core:ui` 正是本仓承载「Android 专用 Compose UI」的地方
 * （同 M1-3r 把 `LiquidGlassEffects` 的 Android 实现留在这里）。对外只暴露本文件的
 * [androidMiuixPreferenceRenderer] 工厂，实现类私有。
 *
 * 参数与迁移前 `SwitchSettingItem` 里那段 `SwitchPreference(...)` 一致：`modifier` 传
 * `Modifier`（原调用方没有额外修饰）。
 */
fun androidMiuixPreferenceRenderer(): MiuixPreferenceRenderer = AndroidMiuixPreferenceRenderer

private object AndroidMiuixPreferenceRenderer : MiuixPreferenceRenderer {

    @Composable
    override fun switchPreference(
        title: String,
        summary: String?,
        checked: Boolean,
        enabled: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        SwitchPreference(
            title = title,
            summary = summary,
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier,
            enabled = enabled,
        )
    }
}
