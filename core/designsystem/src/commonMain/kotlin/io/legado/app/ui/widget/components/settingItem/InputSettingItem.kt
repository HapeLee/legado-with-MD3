package io.legado.app.ui.widget.components.settingItem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.core.designsystem.res.Res
import io.legado.app.core.designsystem.res.confirm
import io.legado.app.core.designsystem.res.edit
import io.legado.app.core.designsystem.res.text_default
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.SplicedColumnDivider
import io.legado.app.ui.widget.components.text.AppText
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import org.jetbrains.compose.resources.stringResource

// M5-4a-pre：本文件从 `:core:ui/src/main` 上提到 `:core:designsystem/commonMain`
// （`git mv`，**包名不变** ⇒ 调用方 import 零改动）。目的与前面几片资产上提一样：
// 给 `:feature:settings` 的下一个子页（ai/summary）让路。
//
// 形态与 `SliderSettingItem`(M5-2c)、`ColorPickerSheet`(M5-3a-pre) 完全同形：
//   · Miuix 分支用的是 `miuix-ui` 的 `BasicComponent` / `TextField`（**有** desktop 变体）
//     ⇒ 不撞 `miuix-preference`，不需要 `MiuixPreferenceRenderer` 契约；
//   · **要带资源**：迁移前用 `:core:ui` 自己的 `R`（`edit` / `text_default` / `confirm`），
//     已搬进 designsystem 的 composeResources（4 语言各一份，值逐字照搬）。
//     其中 `edit` 与 `text_default` 是 M5-2c 为 `SliderSettingItem` 搬过的，**复用同一份**；
//     `confirm` 是本次新增。

@Composable
fun InputSettingItem(
    title: String,
    value: String,
    defaultValue: String? = "",
    description: String? = null,
    onConfirm: (String) -> Unit
) {
    // 1. 状态统一提升到外部，两套引擎共用
    var expanded by remember { mutableStateOf(false) }
    val state = rememberTextFieldState(initialText = value)

    LaunchedEffect(expanded) {
        if (expanded) {
            state.edit {
                replace(0, length, value)
            }
        }
    }

    val composeEngine = LegadoTheme.composeEngine
    SplicedColumnDivider()

    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BasicComponent(
                title = title,
                summary = value,
                onClick = { expanded = !expanded }
            )

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    MiuixTextField(
                        state = state,
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(Res.string.edit),
                        onKeyboardAction = {
                            onConfirm(state.text.toString())
                            expanded = false
                        }
                    )

                    ConfirmDismissButtonsRow(
                        modifier = Modifier.padding(top = 16.dp),
                        onDismiss = {
                            state.edit { replace(0, length, defaultValue.toString()) }
                        },
                        onConfirm = {
                            onConfirm(state.text.toString())
                            expanded = false
                        },
                        dismissText = stringResource(Res.string.text_default),
                        confirmText = stringResource(Res.string.confirm)
                    )
                }
            }
        }
    } else {
        SettingItem(
            title = title,
            description = description,
            option = value,
            expanded = expanded,
            onExpandChange = { expanded = it },
            expandContent = {
                TextField(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    label = { AppText(stringResource(Res.string.edit)) },
                    contentPadding = PaddingValues(
                        top = 4.dp,
                        bottom = 4.dp,
                        start = 12.dp,
                        end = 12.dp
                    ),
                    onKeyboardAction = {
                        onConfirm(state.text.toString())
                        expanded = false
                    }
                )

                ConfirmDismissButtonsRow(
                    modifier = Modifier.padding(top = 16.dp),
                    onDismiss = {
                        state.edit { replace(0, length, defaultValue.toString()) }
                    },
                    onConfirm = {
                        onConfirm(state.text.toString())
                        expanded = false
                    },
                    dismissText = stringResource(Res.string.text_default),
                    confirmText = stringResource(Res.string.confirm)
                )
            }
        )
    }
}
