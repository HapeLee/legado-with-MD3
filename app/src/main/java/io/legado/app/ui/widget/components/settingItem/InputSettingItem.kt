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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import kotlinx.coroutines.flow.distinctUntilChanged
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.SplicedColumnDivider
import io.legado.app.ui.widget.components.text.AppText
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField

@Composable
fun InputSettingItem(
    title: String,
    value: String,
    defaultValue: String? = "",
    description: String? = null,
    onConfirm: (String) -> Unit,
    /**
     * 可选的实时回写：文本内容每次变化都会回调，供"确认式"输入之外的保存流程（如供应商编辑页的
     * 全局保存按钮）拿到最新文本。不传时行为与原来完全一致——只在点确认/键盘动作时提交。
     */
    onValueChange: ((String) -> Unit)? = null
) {
    // 1. 状态统一提升到外部，两套引擎共用
    var expanded by remember { mutableStateOf(false) }
    val state = rememberTextFieldState(initialText = value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    LaunchedEffect(expanded) {
        if (expanded) {
            state.edit {
                replace(0, length, value)
            }
        }
    }

    // 仅在配置了实时回写时监听文本框内容；distinctUntilChanged 避免重复提交同一内容。
    if (onValueChange != null) {
        LaunchedEffect(Unit) {
            snapshotFlow { state.text.toString() }
                .distinctUntilChanged()
                .collect { text -> currentOnValueChange?.invoke(text) }
        }
    }

    val composeEngine = LegadoTheme.composeEngine
    SplicedColumnDivider()

    when (composeEngine) {
        ComposeEngine.Miuix -> {
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
                            label = stringResource(R.string.edit),
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
                            dismissText = stringResource(R.string.text_default),
                            confirmText = stringResource(R.string.confirm)
                        )
                    }
                }
            }
        }
        ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
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
                        label = { AppText(stringResource(R.string.edit)) },
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
                        dismissText = stringResource(R.string.text_default),
                        confirmText = stringResource(R.string.confirm)
                    )
                }
            )
        }
    }
}
