package io.legado.app.ui.widget.components.settingItem

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.SplicedColumnDivider
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem

// M5-2b：本文件从 `:core:ui/src/main` 上提到 `:core:designsystem/commonMain`
// （`git mv`，**包名不变** ⇒ 调用方 import 零改动）。目的与 M5-2a-pre 的
// `ClickableSettingItem` 一样：给 `:feature:settings` 的后续子页（translation）让路。
//
// 上提时撞的约束也**同一样**：Miuix 分支的 `OverlaySpinnerPreference` 来自
// `miuix-preference` —— 本仓用到的 miuix 制品里**唯一没有 desktop 变体**的那个
// （版本目录只有 `miuix-preference-android`）⇒ 走 `MiuixPreferenceRenderer` 窄契约，
// Android 实现留 `:core:ui`。
//
// 契约面与迁移前的差异（刻意）：`DropdownItem` 是 miuix 的类，不能进共享层签名
// ⇒ 契约收 `List<String>`，实现侧再包 `DropdownItem(title = …)`；
// `startAction` 的 `@Composable () -> Unit` 退化成 `imageVector: ImageVector?`
// ——调用方本来就只传一个图标。
//
// 失败语义同族：未注入 ⇒ 落下面这条 Material3 路径（不是画空白）。判据见契约 KDoc。

@Composable
fun DropdownListSettingItem(
    title: String,
    selectedValue: String,
    displayEntries: Array<String>,
    entryValues: Array<String>,
    description: String? = null,
    imageVector: ImageVector? = null,
    onValueChange: (String) -> Unit
) {
    val composeEngine = LegadoTheme.composeEngine
    // 未注入 ⇒ 走下面那条原本就存在的 Material3 渲染路径；理由见上方注释与契约 KDoc。
    val miuixRenderer = MiuixPreferenceRendererProvider.current
    SplicedColumnDivider()

    if (miuixRenderer != null && ThemeResolver.isMiuixEngine(composeEngine)) {
        val selectedIndex = entryValues.indexOf(selectedValue).coerceAtLeast(0)
        miuixRenderer.overlaySpinnerPreference(
            title = title,
            summary = description,
            items = displayEntries.toList(),
            selectedIndex = selectedIndex,
            imageVector = imageVector,
            onSelectedIndexChange = { index ->
                onValueChange(entryValues[index])
            },
        )
    } else {

        val currentEntry =
            displayEntries.getOrNull(entryValues.indexOf(selectedValue)) ?: selectedValue

        SettingItem(
            title = title,
            description = description,
            option = currentEntry,
            imageVector = imageVector,
            onClick = { },
            dropdownMenu = { onDismiss ->
                displayEntries.forEachIndexed { index, display ->
                    RoundDropdownMenuItem(
                        text = display,
                        onClick = {
                            onValueChange(entryValues[index])
                            onDismiss()
                        },
                        trailingIcon = if (selectedValue == entryValues[index]) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null
                    )
                }
            }
        )
    }
}
