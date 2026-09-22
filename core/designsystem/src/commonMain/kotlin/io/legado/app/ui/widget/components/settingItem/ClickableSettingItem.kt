package io.legado.app.ui.widget.components.settingItem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.SplicedColumnDivider

// M5-2a-pre：本文件从 `:core:ui/src/main` 上提到 `:core:designsystem/commonMain`
// （`git mv`，**包名不变** ⇒ 迁移前那 36 个调用方 import 零改动）。
//
// 为什么必须上提：M5-2a 要把实验室页迁进 `:feature:settings`，而它用了本组件；
// `:core:ui` 仍是 Android-only（`src/main`），共享层依赖不到。先例是 M1-3x-pre
// （`RuleEditSheet` / `ContentProcessUiState` 等 4 件资产为 replacerules 上提）。
//
// 上提时唯一的阻塞与 `SwitchSettingItem` 当初**完全一样**：Miuix 的 `preference` 系列
// 是本仓用到的 miuix 制品里**唯一没有 desktop 变体**的那个（版本目录里只有
// `miuix-preference-android`）⇒ 直接 `import top.yukonga.miuix.kmp.preference.ArrowPreference`
// 会让 `compileKotlinDesktop` 失败。处置沿用 M1-3t 定的窄契约：
// `MiuixPreferenceRenderer` 增加 `arrowPreference(...)`，Android 实现留在 `:core:ui`
// （`AndroidMiuixPreferenceRenderer`，那里才看得见 `miuix-preference-android`），
// 由 `:app` 的 `PlatformServices.install()` 注入。
//
// **失败语义同 `SwitchSettingItem`**：未注入渲染器 ⇒ 落回下面这条 Material3 路径
// （不是画空白）。判据也一样——`LocalComposeEngine` 默认 `Material3`，Miuix 引擎只由
// Android 侧的 `:core:ui` 提供 ⇒ **desktop 上那条分支不可达**，所以这里不需要
// 用「缺失即抛」把一条不可达路径武装成地雷。
//
// 行为与迁移前逐字等价：Miuix 分支仍是 `ArrowPreference(title, summary, insideMargin =
// BasicComponentDefaults.InsideMargin, onClick)`（`modifier` 本来就**没有**传给 miuix
// ——迁移前的代码也没传），Material3 分支仍是 `SettingItem` + `ChevronRight`。

@Composable
fun ClickableSettingItem(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    option: String? = null,
    imageVector: ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val composeEngine = LegadoTheme.composeEngine
    // 未注入 ⇒ 走下面那条原本就存在的 Material3 渲染路径；理由见上方注释与契约 KDoc。
    val miuixRenderer = MiuixPreferenceRendererProvider.current
    SplicedColumnDivider()

    if (miuixRenderer != null && ThemeResolver.isMiuixEngine(composeEngine)) {
        miuixRenderer.arrowPreference(
            title = title,
            summary = description,
            onClick = onClick,
        )
    } else {
        SettingItem(
            modifier = modifier,
            title = title,
            description = description,
            option = option,
            imageVector = imageVector,
            trailingContent = trailingContent ?: {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = onClick,
            onLongClick = onLongClick
        )
    }
}
