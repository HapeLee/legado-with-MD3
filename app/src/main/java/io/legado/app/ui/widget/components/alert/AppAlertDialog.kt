package io.legado.app.ui.widget.components.alert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.button.PrimaryButton
import io.legado.app.ui.widget.components.button.SecondaryButton
import top.yukonga.miuix.kmp.window.WindowDialog

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppAlertDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String? = null,
    text: String? = null,
    content: (@Composable () -> Unit)? = null,
    confirmText: String = "确定", // 默认文字
    onConfirm: (() -> Unit)? = null,
    dismissText: String = "取消",
    onDismiss: (() -> Unit)? = null,
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        WindowDialog(
            show = show,
            modifier = modifier,
            title = title,
            summary = text,
            onDismissRequest = onDismissRequest,
            content = {
                if (content != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        content()
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onDismiss != null) {
                        SecondaryButton(
                            text = dismissText,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onDismiss()
                                onDismissRequest()
                            }
                        )
                    }

                    if (onConfirm != null) {
                        PrimaryButton(
                            text = confirmText,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onConfirm()
                            }
                        )
                    }
                }
            }
        )
    } else {
        if (show) {
            AlertDialog(
                onDismissRequest = onDismissRequest,
                modifier = modifier,
                containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
                iconContentColor = LegadoTheme.colorScheme.primary,
                titleContentColor = LegadoTheme.colorScheme.onSurface,
                textContentColor = LegadoTheme.colorScheme.onSurfaceVariant,
                tonalElevation = AlertDialogDefaults.TonalElevation,
                title = title?.let { { Text(text = it) } },
                text = {
                    Column {
                        if (text != null) {
                            Text(
                                text = text,
                                modifier = Modifier.padding(bottom = if (content != null) 16.dp else 0.dp)
                            )
                        }
                        if (content != null) {
                            content()
                        }
                    }
                },
                confirmButton = {
                    if (onConfirm != null) {
                        PrimaryButton(
                            onClick = onConfirm,
                            text = confirmText
                        )
                    }
                },
                dismissButton = {
                    if (onDismiss != null) {
                        SecondaryButton(
                            onClick = {
                                onDismiss()
                                onDismissRequest()
                            },
                            text = dismissText
                        )
                    }
                }
            )
        }
    }
}

/**
 * 专为 nullable 数据设计的 AppAlertDialog 重载。
 * 当 [data] 不为 null 时显示弹窗；当 [data] 变为 null 时，自动缓存最后一次数据并播放退出动画。
 */
@Composable
fun <T> AppAlertDialog(
    data: T?,
    onDismissRequest: () -> Unit,
    title: String? = null,
    text: String? = null,
    confirmText: String = "确定",
    onConfirm: ((T) -> Unit)? = null,
    dismissText: String = "取消",
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: (@Composable (T) -> Unit)? = null
) {
    var cachedData by remember { mutableStateOf(data) }

    if (data != null) {
        cachedData = data
    }

    val currentData = cachedData
    if (currentData != null) {
        AppAlertDialog(
            show = data != null,
            onDismissRequest = onDismissRequest,
            title = title,
            text = text,
            modifier = modifier,
            confirmText = confirmText,
            onConfirm = onConfirm?.let { { it(currentData) } },
            dismissText = dismissText,
            onDismiss = onDismiss,
            content = content?.let { { it(currentData) } }
        )
    }
}
